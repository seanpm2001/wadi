/**
 *
 * Copyright 2003-2004 The Apache Software Foundation
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

package org.codehaus.wadi.test;

import java.util.HashMap;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.JMSException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.activecluster.Cluster;
import org.codehaus.activecluster.ClusterException;
import org.codehaus.activecluster.ClusterListener;
import org.codehaus.activecluster.impl.DefaultClusterFactory;
import org.codehaus.activemq.ActiveMQConnectionFactory;
import org.codehaus.wadi.cluster.NChooseKTopologyStrategy;
import org.codehaus.wadi.cluster.TopologyStrategy;

// originally based on James' ClusterDemo from activecluster...

/**
 * @version $Revision$
 */
public class
  ClusterDemo
{
  protected Cluster                   _cluster;
  protected ActiveMQConnectionFactory _connFactory = new ActiveMQConnectionFactory("multicast://224.1.2.3:5123");
  protected String                    _id;
  protected TopologyStrategy          _topology;
  protected int                       _cellSize=2;

  public
    ClusterDemo(String id, int cellSize)
    {
      _id=id;
      _cellSize=cellSize;
    }

  protected void
    start()
    throws JMSException, ClusterException
    {
      _cluster = createCluster();
      Map state=new HashMap();
      state.put("id", _id);
      _cluster.getLocalNode().setState(state);
      _topology=new NChooseKTopologyStrategy(_cluster, _cellSize);
      _topology.start();
      _cluster.addClusterListener(_topology);
      _cluster.start();
    }

  protected void
    stop()
    throws JMSException
    {
      _cluster.stop();
      _topology.stop();
      _connFactory.stop();
    }

  protected Cluster
    createCluster()
    throws JMSException, ClusterException
    {
      Connection connection = _connFactory.createConnection();
      DefaultClusterFactory factory = new DefaultClusterFactory(connection);
      return factory.createCluster("ORG.CODEHAUS.WADI.TEST.CLUSTER");

    }

  //----------------------------------------


  public static void
    main(String[] args)
    {
      Log log=LogFactory.getLog(ClusterDemo.class);

      int nPeers=Integer.parseInt(args[0]);
      int cellSize=Integer.parseInt(args[1]);

      for (int i=0; i<nPeers; i++)
      {
	try
	{
	  String pid=System.getProperty("pid");
	  ClusterDemo test = new ClusterDemo("node"+pid+"."+i, cellSize);
	  test.start();
	}
	catch (JMSException e)
	{
	  log.warn("unexpected problem", e);
	  Exception c = e.getLinkedException();
	  if (c != null)
	    log.warn("unexpected problem", c);
	}
	catch (Throwable e)
	{
	  log.warn("unexpected problem", e);
	}
      }
    }
}
