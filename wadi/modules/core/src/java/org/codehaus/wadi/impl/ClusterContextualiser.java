/**
 *
 * Copyright 2003-2005 Core Developers Network Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.codehaus.wadi.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activecluster.Cluster;
import org.activecluster.ClusterEvent;
import org.activecluster.ClusterListener;
import org.activecluster.LocalNode;
import org.activecluster.Node;
import org.codehaus.wadi.DistributableContextualiserConfig;
import org.codehaus.wadi.Evictable;
import org.codehaus.wadi.ExtendedCluster;
import org.codehaus.wadi.Collapser;
import org.codehaus.wadi.Contextualiser;
import org.codehaus.wadi.ContextualiserConfig;
import org.codehaus.wadi.Emoter;
import org.codehaus.wadi.HttpProxy;
import org.codehaus.wadi.Immoter;
import org.codehaus.wadi.Location;
import org.codehaus.wadi.Motable;
import org.codehaus.wadi.Relocater;
import org.codehaus.wadi.RelocaterConfig;
import org.codehaus.wadi.dindex.impl.DIndex;
import org.codehaus.wadi.io.Server;

import EDU.oswego.cs.dl.util.concurrent.Sync;
import EDU.oswego.cs.dl.util.concurrent.SynchronizedBoolean;
import EDU.oswego.cs.dl.util.concurrent.SynchronizedInt;
import EDU.oswego.cs.dl.util.concurrent.TimeoutException;

//this needs to be split into several parts...

//(1) a request relocation strategy (proxy)
//(2) a state relocation strategy (migrate) - used if (1) fails
//a location cache - used by both the above when finding the locarion of state...

//demotion :

//if the last node in the cluster, pass demotions through to e.g. shared DB below us.
//else, distribute them out to the cluster

//on startup, e.g. db will read in complete complement of sessions and try to promote them
//how do we promote them to disc, but not to memory ? - perhaps in the configuration as when the node shutdown ?
//could we remember the ttl and set it up the same, so nothing times out whilst the cluster is down ?

/**
 * A cache of Locations. If the Location of a Context is not known, the Cluster
 * may be queried for it. If it is forthcoming, we can proxy to it. After a
 * given number of successful proxies, the Context will be migrated to this
 * Contextualiser which should promote it so that future requests for it can be
 * run straight off the top of the stack.
 *
 * Node N1 sends LocationRequest to Cluster
 * Node N2 contextualises this request with a FilterChain that will send a LocationResponse and wait a specified handover period.
 * Node N1 receives the response, updates its cache and then proxies through the Location to the required resource.
 *
 * The promotion mutex is held correctly during the initial Location lookup, so that searches for the same Context are correctly collapsed.
 *
 * Proxy should be applied before Migration- if it succeeds, we don't migrate...
 *
 * This class is getting out of hand !
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */
public class ClusterContextualiser extends AbstractSharedContextualiser implements RelocaterConfig, ClusterListener {
    
    protected final static String _nodeNameKey="name";
    protected final static String _evacuationQueueKey="evacuationQueue";
    protected final static String _shuttingDownKey="shuttingDown";
    
    protected final Map _evacuations=Collections.synchronizedMap(new HashMap());
    protected final SynchronizedInt _evacuationPartnerCount=new SynchronizedInt(0);
    protected final Collapser _collapser;
    protected final Relocater _relocater;
    protected final Immoter _immoter;
    protected final Emoter _emoter;
    protected final int _resTimeout=500; // TODO - parameterise
    
    public ClusterContextualiser(Contextualiser next, Collapser collapser, Relocater relocater) {
        super(next, new CollapsingLocker(collapser), false);
        _collapser=collapser;
        _relocater=relocater;
        _immoter=new EmigrationImmoter();
        _emoter=null; // TODO - I think this should be something like the ImmigrationEmoter
        // it pulls a named Session out of the cluster and emotes it from this Contextualiser...
        // this makes it awkward to split session and request relocation into different strategies,
        // so session relocation should be the basic strategy, with request relocation as a pluggable
        // optimisation...
    }
    
    protected SynchronizedBoolean _shuttingDown;
    protected String _nodeName;
    protected ExtendedCluster _cluster;
    protected Location _location;
    protected Destination _evacuationQueue;
    protected Dispatcher _dispatcher;
    protected DIndex _dindex;
    protected Contextualiser _top;
    
    public void init(ContextualiserConfig config) {
        super.init(config);
        DistributableContextualiserConfig dcc=(DistributableContextualiserConfig)config;
        _shuttingDown=dcc.getShuttingDown();
        _nodeName=dcc.getNodeName();
        _cluster=dcc.getCluster();
        _location=new HttpProxyLocation(_cluster.getLocalNode().getDestination(), dcc.getHttpAddress(), dcc.getHttpProxy());
        _dispatcher=dcc.getDispatcher();
        _dindex=dcc.getDIndex();
        _cluster.addClusterListener(this);
        _dispatcher.register(this, "onEmigrationRequest", EmigrationRequest.class);
        _dispatcher.register(EmigrationResponse.class, _resTimeout);
        _top=dcc.getContextualiser();
        _relocater.init(this);
    }
    
    public String getStartInfo() {
        return "["+_nodeName+"]";
    }
    
    public void destroy() {
        _relocater.destroy();
        // TODO - what else ?
        super.destroy();
    }
    
    public Immoter getImmoter(){return _immoter;}
    public Emoter getEmoter(){return _emoter;}
    
    protected int getEvacuationPartnersCount() {
        return _evacuationPartnerCount.get();
    }
    
    protected void refreshEvacuationPartnersCount() {
        LocalNode localNode=_cluster.getLocalNode();
        Map nodes=_cluster.getNodes();
        int count=0;
        synchronized (nodes) { // does James modify this Map or replace it ?
            for (Iterator i=nodes.values().iterator(); i.hasNext();) {
                Node node=(Node)i.next();
                if (node!=localNode && !node.getState().containsKey(_shuttingDownKey))
                    count++;
            }
        }
        
        _evacuationPartnerCount.set(count);
    }
    
    public Immoter getDemoter(String name, Motable motable) {
        if (getEvacuationPartnersCount()>0) {
            ensureEvacuationQueue();
            return getImmoter();
        } else {
            return _next.getDemoter(name, motable);
        }
    }
    
    public Immoter getSharedDemoter() {
        if (getEvacuationPartnersCount()>0) {
            ensureEvacuationQueue();
            return getImmoter();
        } else {
            return _next.getSharedDemoter();
        }
    }
    
    public boolean handle(HttpServletRequest hreq, HttpServletResponse hres, FilterChain chain, String id, Immoter immoter, Sync motionLock) throws IOException, ServletException {
        return _relocater.relocate(hreq, hres, chain, id, immoter, motionLock);
    }
    
    protected void createEvacuationQueue() throws JMSException {
        _log.trace("creating evacuation queue");
        DistributableContextualiserConfig dcc=(DistributableContextualiserConfig)_config;
        dcc.putDistributedState(_shuttingDownKey, Boolean.TRUE);
        _evacuationQueue=_cluster.createQueue(_nodeName+"."+_evacuationQueueKey);
        dcc.putDistributedState(_evacuationQueueKey, _evacuationQueue);
        dcc.distributeState();
        
        // whilst we are evacuating...
        // 1) do not get involved in any other evacuations.
        _log.info("ignoring further evacuation appeals");
        // 2) withdraw from any other evacuation in which we may be involved
        _log.info("withdrawing from ongoing evacuations: "+_evacuations.size());
        synchronized (_evacuations) {
            for (Iterator i=new ArrayList(_evacuations.keySet()).iterator(); i.hasNext(); ) {
                String nodeName=(String)i.next();
                ensureEvacuationLeft(nodeName);
            }
        }
        // give time for threads that are already processing asylum applications to finish - can we join them ? -
        // TODO - use shutdownAfterProcessingCurrentlyQueuedTasks() and a separate ThreadPool...
        Utils.safeSleep(2000);
    }
    
    protected void destroyEvacuationQueue() throws JMSException {
        DistributableContextualiserConfig dcc=(DistributableContextualiserConfig)_config;
        // leave shuttingDown=true
        _evacuationQueue=null;
        dcc.removeDistributedState(_evacuationQueueKey);
        dcc.distributeState();
        //Utils.safeSleep(5*1000*2); // TODO - should be hearbeat period...
        // FIXME - can we destroy the queue ?
        _log.trace("emigration queue destroyed");
    }
    
    protected synchronized void ensureEvacuationQueue() {
        synchronized (_shuttingDown) {
            try {
                if (_shuttingDown.get()) {
                    createEvacuationQueue();
                }
            } catch (JMSException e) {
                _log.error("emmigration queue initialisation failed", e);
                _evacuationQueue=null;
            }
        }
    }
    
    public void stop() throws Exception {
        synchronized (_shuttingDown) {
            if (_evacuationQueue!=null) { // evacuation is synchronous, so we will not get to here until all sessions are gone...
                destroyEvacuationQueue();
            }
        }
        super.stop();
    }
    
    /**
     * Manage the immotion of a session into the cluster tier from another and its emigration thence to another node via the EvacuationQueue.
     *
     * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
     * @version $Revision$
     */
    class EmigrationImmoter implements Immoter {
        public Motable nextMotable(String id, Motable emotable) {return new SimpleMotable();}
        
        public boolean prepare(String name, Motable emotable, Motable immotable) {
            try {
                immotable.copy(emotable);
            } catch (Exception e) {
                if (_log.isWarnEnabled()) _log.warn("problem sending emigration request: "+name, e);
                return false;
            }
            Destination to=_evacuationQueue;
            Destination from=_cluster.getLocalNode().getDestination();
            EmigrationRequest request=new EmigrationRequest(immotable);
            ObjectMessage message=_dispatcher.exchangeSend(from, to, request, _resTimeout);
            EmigrationResponse ack=null;
            try {
                ack=message==null?null:(EmigrationResponse)message.getObject();
            } catch (JMSException e) {
                _log.error("could not unpack response", e);
            }
            
            if (ack==null) {
                if (_log.isWarnEnabled()) _log.warn("no acknowledgement within timeframe ("+_resTimeout+" millis): "+name);
                return false;
            } else {
                if (_log.isTraceEnabled()) _log.trace("received acknowledgement within timeframe ("+_resTimeout+" millis): "+name);
                return true;
            }
        }
        
        public void commit(String name, Motable immotable) {
            // TODO - cache new location of emigrating session...
        }
        
        public void rollback(String name, Motable immotable) {
            // TODO - errr... HOW ?
        }
        
        public boolean contextualise(HttpServletRequest hreq, HttpServletResponse hres, FilterChain chain, String id, Motable immotable, Sync motionLock) {
            return false;
            // TODO - perhaps this is how a proxied contextualisation should occur ?
        }
        
        public String getInfo() {
            return "cluster";
        }
    }
    
    /**
     * Manage the immigration of a session from another node and and thence its emotion from the cluster layer into another.
     *
     * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
     * @version $Revision$
     */
    class ImmigrationEmoter implements Emoter {
        
        protected final ObjectMessage _message;
        
        public ImmigrationEmoter(ObjectMessage message) {
            _message=message;
        }
        
        public boolean prepare(String name, Motable emotable) {
            return true;
        }
        
        public void commit(String name, Motable emotable) {
            // TODO - consider moving this to prepare()...
            if (!_dispatcher.reply(_message, new EmigrationResponse(name, _location))) { 
                if (_log.isErrorEnabled()) _log.error("could not acknowledge safe receipt: "+name);
            }
        }
        
        public void rollback(String name, Motable emotable) {
            throw new RuntimeException("NYI");
            // difficult !!!
        }
        
        public String getInfo() {
            return "cluster";
        }
    }
    
    public void onEmigrationRequest(ObjectMessage message, EmigrationRequest request) {
        Motable emotable=request.getMotable();
        String name=emotable.getName();
        if (_log.isTraceEnabled()) _log.trace("EmigrationRequest received: "+name);
        Sync lock=_locker.getLock(name, emotable);
        boolean acquired=false;
        try {
            Utils.acquireUninterrupted(lock);
            acquired=true;
            
            Emoter emoter=new ImmigrationEmoter(message);
            
            if (!emotable.checkTimeframe(System.currentTimeMillis()))
                if (_log.isWarnEnabled()) _log.warn("immigrating session has come from the future!: "+emotable.getName());
            
            Immoter immoter=_top.getDemoter(name, emotable);
            Utils.mote(emoter, immoter, emotable, name);
            notifySessionRelocation(name);
        } catch (TimeoutException e) {
            if (_log.isWarnEnabled()) _log.warn("could not acquire promotion lock for incoming session: "+name);
        } finally {
            if (acquired)
                lock.release();
        }
    }
    
    public void load(Emoter emoter, Immoter immoter) {
        // currently - we don't load anything from the Cluster - we could do state-balancing here
        // i.e. on startup, take ownership of a number of active sessions - affinity implications etc...
    }
    
    // AbstractMotingContextualiser
    
    // ClusterListener
    
    public void onNodeAdd(ClusterEvent event) {
        //_log.info("node joined: "+event.getNode().getState().get(_nodeNameKey));
        refreshEvacuationPartnersCount();
        onNodeStateChange(event);
    }
    
    public void onNodeUpdate(ClusterEvent event) {
        //_log.info("node updated: "+event.getNode().getState().get(_nodeNameKey));
        refreshEvacuationPartnersCount();
        onNodeStateChange(event);
    }
    
    public void onNodeStateChange(ClusterEvent event) {
        Map state=event.getNode().getState();
        String nodeName=(String)state.get(_nodeNameKey);
        
        if (nodeName.equals(_nodeName)) return; // we do not want to listen to our own state changes
        
        //_log.info("node state changed: "+nodeName+" : "+state);
        Destination evacuationQueue=(Destination)state.get(_evacuationQueueKey);
        if (evacuationQueue==null) {
            ensureEvacuationLeft(nodeName);
        } else {
            // we must not ourselves be evacuating...
            synchronized (_shuttingDown) {
                if (!_shuttingDown.get()) {
                    ensureEvacuationJoined(nodeName, evacuationQueue);
                }
            }
        }
    }
    
    protected void ensureEvacuationJoined(String nodeName, Destination evacuationQueue) {
        synchronized (_evacuations) {
            if (!_evacuations.containsKey(nodeName)) {
                try {
                    if (_log.isTraceEnabled()) _log.trace("joining evacuation: "+nodeName);
                    MessageConsumer consumer=_dispatcher.addDestination(evacuationQueue);
                    _evacuations.put(nodeName, consumer);
//                  } catch (IllegalStateException e) {
//                  _log.debug("evacuation finished before we could join: "+nodeName);
                } catch (JMSException e) {
                    _log.warn("unexpected problem", e);
                }
            }
        }
    }
    
    protected void ensureEvacuationLeft(String nodeName) {
        synchronized (_evacuations) {
            MessageConsumer consumer=(MessageConsumer)_evacuations.get(nodeName);
            if (consumer!=null) {
                if (_log.isTraceEnabled()) _log.trace("leaving evacuation: "+nodeName);
                try {
                    _dispatcher.removeDestination(consumer);
                } catch (JMSException e) {
                    _log.warn("could not leave evacuation", e);
                }
                _evacuations.remove(nodeName);
            }
        }
    }
    
    public void onNodeRemoved(ClusterEvent event) {
        Map state=event.getNode().getState();
        String nodeName=(String)state.get(_nodeNameKey);
        _log.info("node left: "+nodeName);
        refreshEvacuationPartnersCount();
        ensureEvacuationLeft(nodeName);
    }
    
    public void onNodeFailed(ClusterEvent event)  {
        Map state=event.getNode().getState();
        String nodeName=(String)state.get(_nodeNameKey);
        _log.info("node failed: "+nodeName);
        refreshEvacuationPartnersCount();
        ensureEvacuationLeft(nodeName);
    }
    
    public void onCoordinatorChanged(ClusterEvent event) {
        _log.trace("coordinator changed: "+event.getNode().getState().get(_nodeNameKey)); // we don't use this...
    }
    
    protected int _locationMaxInactiveInterval=30;
    
    class MyLocation implements Evictable {
        
        protected long _lastAccessedTime;
        protected String _nodeName;
        
        public MyLocation(long timestamp, String nodeName) {
            _lastAccessedTime=timestamp;
            _nodeName=nodeName;
        }
        
        public void init(long creationTime, long lastAccessedTime, int maxInactiveInterval) {
            // ignore creationTime;
            _lastAccessedTime=lastAccessedTime;
            // ignore maxInactiveInterval
        }
        
        public void destroy()  {throw new UnsupportedOperationException();}
        
        public void copy(Evictable evictable) {throw new UnsupportedOperationException();}
        
        public long getCreationTime() {return _lastAccessedTime;}
        public long getLastAccessedTime() {return _lastAccessedTime;}
        public void setLastAccessedTime(long lastAccessedTime) {throw new UnsupportedOperationException();}
        public int  getMaxInactiveInterval() {return _locationMaxInactiveInterval;}
        public void setMaxInactiveInterval(int maxInactiveInterval) {throw new UnsupportedOperationException();}
        
        public boolean isNew() {throw new UnsupportedOperationException();}
        
        public long getTimeToLive(long time) {return _lastAccessedTime+(_locationMaxInactiveInterval*1000)-time;}
        public boolean getTimedOut(long time) {return getTimeToLive(time)<=0;}
        public boolean checkTimeframe(long time) {throw new UnsupportedOperationException();}
        
    }
    
    // RelocaterConfig
    
    public Collapser getCollapser() {return _collapser;}
    public Dispatcher getDispatcher() {return _dispatcher;}
    public Location getLocation() {return _location;}
    public Contextualiser getContextualiser() {return _top;}
    public Server getServer() {return ((DistributableContextualiserConfig)_config).getServer();}
    public String getNodeName() {return _nodeName;}
    public SynchronizedBoolean getShuttingDown() {return _shuttingDown;}
    public HttpProxy getHttpProxy() {return ((DistributableContextualiserConfig)_config).getHttpProxy();}
    public InetSocketAddress getHttpAddress() {return ((DistributableContextualiserConfig)_config).getHttpAddress();}
    
    public Cluster getCluster() {return _cluster;}
    
    public DIndex getDIndex() {
        return _dindex;
    }
    
    public void notifySessionRelocation(String name) {
        _config.notifySessionRelocation(name);
    }
    
    public Motable get(String name) {
        throw new UnsupportedOperationException();
    }
    
}