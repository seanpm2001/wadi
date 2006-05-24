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
package org.codehaus.wadi.location.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.Emoter;
import org.codehaus.wadi.Immoter;
import org.codehaus.wadi.Invocation;
import org.codehaus.wadi.InvocationException;
import org.codehaus.wadi.Motable;
import org.codehaus.wadi.PartitionMapper;
import org.codehaus.wadi.group.Address;
import org.codehaus.wadi.group.Cluster;
import org.codehaus.wadi.group.ClusterEvent;
import org.codehaus.wadi.group.ClusterListener;
import org.codehaus.wadi.group.Dispatcher;
import org.codehaus.wadi.group.Message;
import org.codehaus.wadi.group.Peer;
import org.codehaus.wadi.group.Quipu;
import org.codehaus.wadi.group.impl.SeniorityElectionStrategy;
import org.codehaus.wadi.impl.AbstractChainedEmoter;
import org.codehaus.wadi.impl.Utils;
import org.codehaus.wadi.location.CoordinatorConfig;
import org.codehaus.wadi.location.PartitionManager;
import org.codehaus.wadi.location.PartitionManagerConfig;
import org.codehaus.wadi.location.StateManager;
import org.codehaus.wadi.location.StateManagerConfig;
import org.codehaus.wadi.location.newmessages.DeleteIMToPM;
import org.codehaus.wadi.location.newmessages.EvacuateIMToPM;
import org.codehaus.wadi.location.newmessages.InsertIMToPM;
import org.codehaus.wadi.location.newmessages.InsertPMToIM;
import org.codehaus.wadi.location.newmessages.MoveIMToPM;
import org.codehaus.wadi.location.newmessages.MoveIMToSM;
import org.codehaus.wadi.location.newmessages.MovePMToIM;
import org.codehaus.wadi.location.newmessages.MoveSMToIM;
import EDU.oswego.cs.dl.util.concurrent.Latch;
import EDU.oswego.cs.dl.util.concurrent.Sync;

/**
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision:1815 $
 */
public class DIndex implements ClusterListener, CoordinatorConfig, SimplePartitionManager.Callback, StateManagerConfig {

	protected final static String _nodeNameKey="nodeName";
	protected final static String _partitionKeysKey="partitionKeys";
	protected final static String _timeStampKey="timeStamp";
	protected final static String _birthTimeKey="birthTime";
	protected final static String _correlationIDMapKey="correlationIDMap";

	protected final Map _distributedState;
	protected final Latch _coordinatorLatch=new Latch();
	protected final Object _coordinatorLock=new Object();
	protected final Dispatcher _dispatcher;
	protected final Cluster _cluster;
	protected final String _nodeName;
	protected final Log _log;
	protected final long _inactiveTime;
	protected final PartitionManager _partitionManager;
	protected final StateManager _stateManager;
	protected final Log _lockLog=LogFactory.getLog("org.codehaus.wadi.LOCKS");

	public DIndex(String nodeName, int numPartitions, long inactiveTime, Dispatcher dispatcher, Map distributedState, PartitionMapper mapper) {
		_nodeName=nodeName;
		_log=LogFactory.getLog(getClass().getName()+"#"+_nodeName);
		_inactiveTime=inactiveTime;
		_dispatcher=dispatcher;
		_cluster=_dispatcher.getCluster();
		_distributedState=distributedState;
		_partitionManager=new SimplePartitionManager(_dispatcher, numPartitions, _distributedState, this, mapper);
		_stateManager= new SimpleStateManager(_dispatcher, _inactiveTime);
	}

	protected Peer _coordinatorNode;
	protected Coordinator _coordinator;
	protected PartitionManagerConfig _config;

	public void init(PartitionManagerConfig config) {
		_log.info("init-ing...");
		_config=config;
		_cluster.setElectionStrategy(new SeniorityElectionStrategy());
        _cluster.addClusterListener(this);
		_distributedState.put(_nodeNameKey, _nodeName);
		_distributedState.put(_correlationIDMapKey, new HashMap());
		_distributedState.put(_birthTimeKey, new Long(_config.getBirthTime()));
		PartitionKeys keys=_partitionManager.getPartitionKeys();
		_distributedState.put(_partitionKeysKey, keys);
		_distributedState.put(_timeStampKey, new Long(System.currentTimeMillis()));
		_partitionManager.init(config);
		_stateManager.init(this);
		_log.info("...init-ed");
	}

	public void start() throws Exception {
		_log.info("starting...");

		_partitionManager.start();

		_log.info("sleeping...");
		boolean isNotCoordinator=_coordinatorLatch.attempt(_inactiveTime); // wait to find out if we are the Coordinator
		_log.info("...waking");

		// If our wait timed out, then we must be the coordinator...
		if (!isNotCoordinator) {
			_partitionManager.localise();
			PartitionKeys k=_partitionManager.getPartitionKeys();
			_distributedState.put(_partitionKeysKey, k);
			_distributedState.put(_timeStampKey, new Long(System.currentTimeMillis()));
			if (_log.isInfoEnabled()) _log.info("local state: " + k);
			_dispatcher.setDistributedState(_distributedState);
			if (_log.isTraceEnabled()) _log.trace("distributed state updated: " + _dispatcher.getDistributedState());
			onCoordinatorChanged(new ClusterEvent(_cluster, _dispatcher.getLocalPeer(), ClusterEvent.COORDINATOR_ELECTED));
			_coordinator.queueRebalancing();
		}

		// whether we are the coordinator or not...

		_partitionManager.waitUntilUseable();
		
		_log.info("...started");
	}

	public void stop() throws Exception {
		_log.info("stopping...");

		Thread.interrupted();

		_stateManager.stop();

		if (_coordinator!=null) {
			_coordinator.stop();
			_coordinator=null;
		}

		_partitionManager.stop();

		_log.info("...stopped");
	}

	public Cluster getCluster() {
		return _cluster;
	}

	public Dispatcher getDispatcher() {
		return _dispatcher;
	}

	public PartitionManager getPartitionManager() {
		return _partitionManager;
	}

	// ClusterListener

	public int getPartition() {
		// TODO - think about synchronisation...
		PartitionKeys keys=(PartitionKeys)_distributedState.get(_partitionKeysKey);
		return keys.getKeys()[Math.abs((int)(Math.random()*keys.size()))];
	}

	public void onPeerUpdated(ClusterEvent event) {
		Peer node=event.getPeer();
		if (_log.isTraceEnabled()) _log.trace("onNodeUpdate: " + getNodeName(node) + ": " + node.getState());

		_partitionManager.update(node);

		Map state=node.getState();
		correlateStateUpdate(state);
	}

	protected void correlateStateUpdate(Map state) {
		Map correlationIDMap=(Map)state.get(_correlationIDMapKey);
		Address local=_dispatcher.getLocalAddress();
		String correlationID=(String)correlationIDMap.get(local);
		if (correlationID!=null) {
			Quipu rv=(Quipu)_dispatcher.getRendezVousMap().get(correlationID);
			if (rv==null) {
				if (_log.isWarnEnabled()) _log.warn("no one waiting for: " + correlationID);
			} else {
				if (_log.isTraceEnabled()) _log.trace("successful correlation: " + correlationID);
				rv.putResult(state);
			}
		}
	}

	public void onPeerAdded(ClusterEvent event) {
		Peer node=event.getPeer();

		if (_log.isDebugEnabled()) _log.debug("node joined: "+getNodeName(node));

		if (_dispatcher.getLocalPeer().equals(_coordinatorNode)) {
			_coordinator.queueRebalancing();
		}

		_partitionManager.update(node);
	}

	public void onPeerRemoved(ClusterEvent event) {
		Peer node=event.getPeer();
		if (_log.isDebugEnabled()) _log.debug("node leaving: "+getNodeName(node));
		_leavers.add(node.getAddress());
		if (_coordinator!=null)
			_coordinator.queueRebalancing();
	}


	public boolean amCoordinator() {
        if (null == _coordinatorNode) {
            return false;
        }
        
		return _coordinatorNode.getAddress().equals(_dispatcher.getLocalAddress());
	}

	public void onPeerFailed(ClusterEvent event) {
		Peer node=event.getPeer();
		if (_log.isDebugEnabled()) _log.info("node failed: "+getNodeName(node));
		if (_leavers.remove(node.getAddress())) {
			// we have already been explicitly informed of this node's wish to leave...
			_left.remove(node);
			if (_log.isTraceEnabled()) _log.trace("onNodeFailed:" + getNodeName(node) + "- already evacuated - ignoring");
		} else {
			if (_log.isErrorEnabled()) _log.error("onNodeFailed: " + getNodeName(node));
			if (amCoordinator()) {
				if (_log.isErrorEnabled()) _log.error("CATASTROPHIC FAILURE on: " + getNodeName(node));
				if (_coordinator!=null)
					_coordinator.queueRebalancing();
				else
					_log.warn("coordinator thread not running");
			}
		}
	}

	public void onCoordinatorChanged(ClusterEvent event) {
        _log.info("COORDINATOR CHANGED: " + event.getPeer());
		synchronized (_coordinatorLock) {
            if (_log.isDebugEnabled()) _log.debug("coordinator elected: " + getNodeName(event.getPeer()));
			Peer newCoordinator=event.getPeer();
			if (false == newCoordinator.equals(_coordinatorNode)) {
				if (null != _coordinatorNode && _coordinatorNode.equals(_dispatcher.getLocalPeer())) {
                    onDismissal(event);
                }
				_coordinatorNode = newCoordinator;
				if (_coordinatorNode.equals(_dispatcher.getLocalPeer())) {
                    onElection(event);
                }
			}

			_coordinatorLatch.release(); // we are still waiting in start() to find out if we are the Coordinator...
		}
	}

	public Collection[] createResultSet(int numPartitions, int[] keys) {
		Collection[] c=new Collection[numPartitions];
		for (int i=0; i<keys.length; i++)
			c[keys[i]]=new ArrayList();
		return c;
	}

	public void onElection(ClusterEvent event) {
		_log.info("accepting coordinatorship");
		try {
			(_coordinator=new Coordinator(this)).start();
			_coordinator.queueRebalancing();
		} catch (Exception e) {
			_log.error("problem starting Coordinator");
		}
	}

	public void onDismissal(ClusterEvent event) {
		_log.info("resigning coordinatorship"); // never happens - coordinatorship is for life..
		try {
			_coordinator.stop();
			_coordinator=null;
		} catch (Exception e) {
			_log.error("problem starting Balancer");
		}
	}


	public static String getNodeName(Peer node) {
		return node==null?"<unknown>":(String)node.getState().get(_nodeNameKey);
	}

	public boolean isCoordinator() {
		synchronized (_coordinatorLock) {
			return _dispatcher.getLocalPeer()==_coordinatorNode;
		}
	}

	public Peer getCoordinator() {
		synchronized (_coordinatorLock) {
			return _coordinatorNode;
		}
	}

	public int getNumPartitions() {
		return _partitionManager.getNumPartitions();
	}

	public Peer getLocalNode() {
		return _dispatcher.getLocalPeer();
	}

	public Collection getRemoteNodes() {
		return _cluster.getRemotePeers().values();
	}

	public Map getRendezVousMap() {
		return _dispatcher.getRendezVousMap();
	}

	protected final Collection _leavers=Collections.synchronizedCollection(new ArrayList());
	protected final Collection _left=Collections.synchronizedCollection(new ArrayList());

	public Collection getLeavers() {
		return _leavers;
	}

	public Collection getLeft() {
		return _left;
	}

	protected int printNode(Peer node) {
		if (node!=_dispatcher.getLocalPeer())
			node=(Peer)_cluster.getRemotePeers().get(node.getAddress());
		if (node==null) {
			if (_log.isInfoEnabled()) _log.info(DIndex.getNodeName(node) + " : <unknown> - {?...}");
			return 0;
		} else {
			PartitionKeys keys=DIndex.getPartitionKeys(node);
			int amount=keys.size();
			if (_log.isInfoEnabled()) _log.info(DIndex.getNodeName(node) + " : " + amount + " - " + keys);
			return amount;
		}
	}
	// temporary test methods...

	public boolean insert(String name, long timeout) {
		try {
			InsertIMToPM request=new InsertIMToPM(name);
			PartitionFacade pf=getPartition(name);
			Message reply=pf.exchange(request, timeout);
			return ((InsertPMToIM)reply.getPayload()).getSuccess();
		} catch (Exception e) {
			_log.warn("problem inserting session key into DHT", e);
			return false;
		}
	}

	public void remove(String name) {
		try {
			DeleteIMToPM request=new DeleteIMToPM(name);
			getPartition(name).exchange(request, _inactiveTime);
		} catch (Exception e) {
			_log.info("oops...", e);
		}
	}

	public void relocate(String name) {
		try {
			EvacuateIMToPM request=new EvacuateIMToPM(name);
			getPartition(name).exchange(request, _inactiveTime);
		} catch (Exception e) {
			_log.info("oops...", e);
		}
	}

	class SMToIMEmoter extends AbstractChainedEmoter {
		protected final Log _log=LogFactory.getLog(getClass());

		protected final String _nodeName;
		protected final Message _message;

		protected Sync _invocationLock;
		protected Sync _stateLock;

		public SMToIMEmoter(String nodeName, Message message) {
			_nodeName=nodeName;
			_message=message;
		}

		public boolean prepare(String name, Motable emotable, Motable immotable) {
			try {
//				Sync _stateLock=((Context)emotable).getExclusiveLock();
//				if (_lockLog.isTraceEnabled()) _lockLog.trace("State - (excl.): "+name+ " ["+Thread.currentThread().getName()+"]");
//				Utils.acquireUninterrupted(_stateLock);
//				if (_lockLog.isTraceEnabled()) _lockLog.trace("State - (excl.): "+name+ " ["+Thread.currentThread().getName()+"]");
				immotable.copy(emotable);
			} catch (Exception e) {
				_log.warn("oops", e);
				return false;
			}

			return true;
		}

		public void commit(String name, Motable emotable) {
			try {
				// respond...
				MoveIMToSM response=new MoveIMToSM(true);
				_dispatcher.reply(_message, response);

				emotable.destroy(); // remove copy in store
//				if (_lockLog.isTraceEnabled()) _lockLog.trace("State (excl.) - releasing: "+name+ " ["+Thread.currentThread().getName()+"]");
//				_stateLock.release();
//				if (_lockLog.isTraceEnabled()) _lockLog.trace("State (excl.) - released: "+name+ " ["+Thread.currentThread().getName()+"]");

			} catch (Exception e) {
				throw new UnsupportedOperationException("NYI"); // NYI
			}
		}

		public void rollback(String name, Motable motable) {
			throw new RuntimeException("NYI");
		}

		public String getInfo() {
			return "immigration:"+_nodeName;
		}
	}

	public Motable relocate(String sessionName, String nodeName, int concurrentRequestThreads, boolean shuttingDown, long timeout, Immoter immoter) throws Exception {
		MoveIMToPM request=new MoveIMToPM(sessionName, nodeName, concurrentRequestThreads, shuttingDown);
		Message message=getPartition(sessionName).exchange(request, timeout);

		if (message==null) {
			_log.error("something went wrong - what should we do?"); // TODO
			return null;
		}

        Serializable dm=(Serializable)message.getPayload();
        // the possibilities...
        if (dm instanceof MoveSMToIM) {
            MoveSMToIM req=(MoveSMToIM)dm;
            // insert motable into contextualiser stack...
            Motable emotable=req.getMotable();
            if (emotable==null) {
                _log.warn("failed relocation - 0 bytes arrived: "+sessionName);
                return null;
            } else {
                if (!emotable.checkTimeframe(System.currentTimeMillis()))
                    if (_log.isWarnEnabled()) _log.warn("immigrating session has come from the future!: "+emotable.getName());
                Emoter emoter=new SMToIMEmoter(_config.getPeerName(message.getReplyTo()), message);
                Motable immotable=Utils.mote(emoter, immoter, emotable, sessionName);
                return immotable;
//              if (null==immotable)
//              return false;
//              else {
//              boolean answer=immoter.contextualise(null, null, null, sessionName, immotable, null);
//              return answer;
//              }
            }
        } else if (dm instanceof MovePMToIM) {
            if (_log.isTraceEnabled()) _log.trace("unknown session: "+sessionName);
            return null;
        } else {
            _log.warn("unexpected response returned - what should I do? : "+dm);
            return null;
        }
	}

	public PartitionFacade getPartition(Object key) {
		return _partitionManager.getPartition(key);
	}

	public String getPeerName(Address address) {
		Peer local=_dispatcher.getLocalPeer();
		Peer node=address.equals(local.getAddress())?local:(Peer)_cluster.getRemotePeers().get(address);
		return getNodeName(node);
	}

	public long getInactiveTime() {
		return _inactiveTime;
	}

	public void regenerateMissingPartitions(Peer[] living, Peer[] leaving) {
		_partitionManager.regenerateMissingPartitions(living, leaving);
	}

	public static PartitionKeys getPartitionKeys(Peer node) {
		return ((PartitionKeys)node.getState().get(_partitionKeysKey));
	}


	// only for use whilst developing GridState...

//	public PartitionFacade[] getPartitions() {
//	return _partitions;
//	}

	// StateManagerConfig API

	public PartitionFacade getPartition(int key) {
		return _partitionManager.getPartition(key);
	}

	public StateManager getStateManager() {
		return _stateManager;
	}

	// StateManagerConfig API

	public String getLocalPeerName() {
		return _nodeName;
	}

	public 	boolean contextualise(Invocation invocation, String id, Immoter immoter, Sync motionLock, boolean exclusiveOnly) throws InvocationException {
		return _config.contextualise(invocation, id, immoter, motionLock, exclusiveOnly);
	}

	public Sync getInvocationLock(String name) {
		return _config.getInvocationLock(name);
	}

}

