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
package org.codehaus.wadi.sandbox.impl;

// Thoughts - 

// invalidation is tricky - stuff invalidated on disc needs may need to be unmarshalled so that the correct listeners may be notified...

// we need a JDBC passivation store

// can we even store an invalidated session without breaking listener model - consider...



import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.codehaus.activecluster.ClusterException;
import org.codehaus.activemq.ActiveMQConnectionFactory;
import org.codehaus.wadi.StreamingStrategy;
import org.codehaus.wadi.impl.SimpleStreamingStrategy;
import org.codehaus.wadi.sandbox.Collapser;
import org.codehaus.wadi.sandbox.ContextPool;
import org.codehaus.wadi.sandbox.Contextualiser;
import org.codehaus.wadi.sandbox.Evicter;
import org.codehaus.wadi.sandbox.HttpProxy;
import org.codehaus.wadi.sandbox.Immoter;
import org.codehaus.wadi.sandbox.Location;
import org.codehaus.wadi.sandbox.Motable;
import org.codehaus.wadi.sandbox.RelocationStrategy;

import EDU.oswego.cs.dl.util.concurrent.Sync;

public class SimpleContextualiserStack implements Contextualiser {
    
    protected final StreamingStrategy _streamer;
    protected final Collapser _collapser;

    protected final DummyContextualiser _dummy;
 
    protected final DataSource _databaseDataSource;
    protected final String _databaseTable;
    protected Evicter _databaseEvicter;
    protected final SharedJDBCContextualiser _database;
    
    protected final ConnectionFactory _connectionFactory;
    protected final CustomClusterFactory _clusterFactory;
    protected final String _clusterName;
    protected final CustomCluster _clusterCluster;
    protected final Evicter _clusterEvicter;
    protected final Map _clusterMap;
    protected final MessageDispatcher _clusterDispatcher;
    protected final RelocationStrategy _clusterRelocater;
    protected final Location _clusterLocation;
    protected final ClusterContextualiser _cluster;
    
    protected final Pattern _statelessMethods;
    protected final boolean _statelessMethodFlag;
    protected final Pattern _statelessURIs;
    protected final boolean _statelessURIFlag;
    protected final StatelessContextualiser _stateless;
      
    protected final File _discDirectory;
    protected final Evicter _discEvicter;
    protected final Map _discMap;
    protected final ExclusiveDiscContextualiser _disc;
    
    protected final SerialContextualiser _serial;
    
    protected final ContextPool _memoryPool;
    protected final Evicter _memoryEvicter;
    protected final Map _memoryMap;
    protected final MemoryContextualiser _memory;
    
    public SimpleContextualiserStack(Map sessionMap, ContextPool pool, DataSource dataSource) throws SQLException, JMSException, ClusterException {
        super();
        _streamer=new SimpleStreamingStrategy();
        _collapser=new DebugCollapser();

        _dummy=new DummyContextualiser();
        _databaseEvicter=new NeverEvicter();
        _databaseDataSource=dataSource;
        _databaseTable="WADI";
        SharedJDBCMotable.initialise(_databaseDataSource, _databaseTable);
        _database=new SharedJDBCContextualiser(_dummy, _databaseEvicter, _databaseDataSource, _databaseTable);

        _connectionFactory=new ActiveMQConnectionFactory("peer://WADI-TEST");
        _clusterFactory=new CustomClusterFactory(_connectionFactory);
        _clusterName="ORG.CODEHAUS.WADI.TEST.CLUSTER";
        _clusterCluster=(CustomCluster)_clusterFactory.createCluster(_clusterName);
        _clusterCluster.start();
        InetSocketAddress isa=new InetSocketAddress("localhost", 8080); // FIXME - hardwired port
        HttpProxy proxy=new StandardHttpProxy("jsessionid");
        _clusterLocation=new HttpProxyLocation(_clusterCluster.getLocalNode().getDestination(), isa, proxy);
        _clusterMap=new HashMap();
        _clusterEvicter=new NeverEvicter();
        _clusterDispatcher=new MessageDispatcher(_clusterCluster);
        _clusterRelocater=new ImmigrateRelocationStrategy(_clusterDispatcher, _clusterLocation, 2000, _clusterMap, _collapser);
        _cluster=new ClusterContextualiser(_database, _clusterEvicter, _clusterMap, _collapser, _clusterCluster, _clusterDispatcher, _clusterRelocater, _clusterLocation);
 
        _statelessMethods=Pattern.compile("GET|POST", Pattern.CASE_INSENSITIVE);
        _statelessMethodFlag=true;
        _statelessURIs=Pattern.compile(".*\\.(JPG|JPEG|GIF|PNG|ICO|HTML|HTM)(|;jsessionid=.*)", Pattern.CASE_INSENSITIVE);
        _statelessURIFlag=false;
        _stateless=new StatelessContextualiser(_cluster, _statelessMethods, _statelessMethodFlag, _statelessURIs, _statelessURIFlag);
 
        _discDirectory=new File("/tmp");
        _discEvicter=new TimedOutEvicter();
        _discMap=new HashMap();
        _disc=new ExclusiveDiscContextualiser(_stateless, _discEvicter, _discMap, _collapser, _streamer, _discDirectory);


        _memoryPool=pool;
        _memoryEvicter=new AbsoluteEvicter(30*60*1000);
        _memoryMap=sessionMap;
        _serial=new SerialContextualiser(_disc, _collapser, _memoryMap);
        _memory=new MemoryContextualiser(_serial, _memoryEvicter, _memoryMap, _streamer, _memoryPool);
        
        _cluster.setTop(_memory);
        _clusterRelocater.setTop(_memory);
        
        // ready to rock !
    }

    public boolean contextualise(HttpServletRequest hreq, HttpServletResponse hres, FilterChain chain, String id, Immoter immoter, Sync promotionLock, boolean localOnly) throws IOException, ServletException {
        return _memory.contextualise(hreq, hres, chain, id, immoter, promotionLock, localOnly);
    }

    public void evict() {
        _memory.evict(); // TODO - consider
    }

    public Evicter getEvicter() {
        return _memory.getEvicter();
    }

    public boolean isLocal() {
        return _memory.isLocal();
    }

    public Immoter getDemoter(String id, Motable motable) {
        return _memory.getDemoter(id, motable);
    }
    
    public void start() throws JMSException {
        _clusterCluster.start();
    }

    public void stop() throws JMSException {
        _clusterCluster.stop();
    }
}