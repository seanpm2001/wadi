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
package org.codehaus.wadi.sandbox.context.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.activecluster.Cluster;
import org.codehaus.activecluster.ClusterEvent;
import org.codehaus.activecluster.ClusterFactory;
import org.codehaus.activecluster.ClusterListener;
import org.codehaus.activecluster.impl.DefaultClusterFactory;
import org.codehaus.activemq.ActiveMQConnectionFactory;
import org.codehaus.wadi.impl.GZIPStreamingStrategy;
import org.codehaus.wadi.sandbox.context.Contextualiser;
import org.codehaus.wadi.sandbox.context.HttpProxy;
import org.codehaus.wadi.sandbox.context.Location;
import org.codehaus.wadi.sandbox.context.Promoter;
import org.codehaus.wadi.sandbox.context.RelocationStrategy;
import org.codehaus.wadi.sandbox.context.impl.CommonsHttpProxy;
import org.codehaus.wadi.sandbox.context.impl.HttpProxyLocation;
import org.codehaus.wadi.sandbox.context.impl.MessageDispatcher;
import org.codehaus.wadi.sandbox.context.impl.MigrateRelocationStrategy;
import org.codehaus.wadi.sandbox.context.impl.ProxyRelocationStrategy;
import org.codehaus.wadi.sandbox.context.impl.StandardHttpProxy;

import EDU.oswego.cs.dl.util.concurrent.Sync;

import junit.framework.TestCase;

/**
 * Unit Tests requiring a pair of Jetty's. Each one is set up with a Filter and Servlet placeholder.
 * These are injected with actual Filter and Servlet instances before the running of each test. This
 * allows the tests to set up the innards of these components, make http requests to them and then inspect
 * their innards for the expected changes,
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */

public class TestRelocation extends TestCase {
	protected Log _log = LogFactory.getLog(getClass());
	
	protected Node _node0;
	protected Node _node1;
	protected MyFilter _filter0;
	protected MyFilter _filter1;
	protected MyServlet _servlet0;
	protected MyServlet _servlet1;
	protected Cluster _cluster0;
	protected Cluster _cluster1;
	protected Location _location0;
	protected Location _location1;
	protected MessageDispatcher _dispatcher0;
	protected MessageDispatcher _dispatcher1;
	protected SwitchableRelocationStrategy _relocater0;
	protected SwitchableRelocationStrategy _relocater1;
	
	  class MyClusterListener
	    implements ClusterListener
	  {
		protected Log _log = LogFactory.getLog(getClass());

		public void
	      onNodeAdd(ClusterEvent ce)
	    {
	      _log.info("node added: " + ce.getNode());
	    }

	    public void
	      onNodeFailed(ClusterEvent ce)
	    {
	      _log.info("node failed: " + ce.getNode());
	    }

	    public void
	      onNodeRemoved(ClusterEvent ce)
	    {
	      _log.info("node removed: " + ce.getNode());
	    }

	    public void
	      onNodeUpdate(ClusterEvent ce)
	    {
	      _log.info("node updated: " + ce.getNode());
	    }

	    public void
	      onCoordinatorChanged(ClusterEvent ce)
	    {
	      _log.info("coordinator changed: " + ce.getNode());
	    }
	  }

	  class SwitchableRelocationStrategy implements RelocationStrategy {
	  	protected RelocationStrategy _delegate=new DummyRelocationStrategy();
	  	
	  	public void setRelocationStrategy(RelocationStrategy delegate){
	  		delegate.setTop(_delegate.getTop());
	  		_delegate=delegate;
	  	}
	  	
	  	// RelocationStrategy
	  	
		public boolean relocate(HttpServletRequest hreq, HttpServletResponse hres, FilterChain chain, String id, Promoter promoter, Sync promotionLock, Map locationMap) throws IOException, ServletException {
			return _delegate.relocate(hreq, hres, chain, id, promoter, promotionLock, locationMap);
		}

		public void setTop(Contextualiser top) {
			_delegate.setTop(top);
		}
		
		public Contextualiser getTop(){return _delegate.getTop();}
	}
	  
	  class DummyRelocationStrategy implements RelocationStrategy {
		public boolean relocate(HttpServletRequest hreq, HttpServletResponse hres, FilterChain chain, String id, Promoter promoter, Sync promotionLock, Map locationMap) throws IOException, ServletException {return false;}
		protected Contextualiser _top;
		public void setTop(Contextualiser top) {_top=top;}
		public Contextualiser getTop(){return _top;}
	}
	  
	  /*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		System.setProperty("org.mortbay.xml.XmlParser.NotValidating", "true");
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("peer://WADI-TEST");
		ClusterFactory clusterFactory       = new DefaultClusterFactory(connectionFactory);
		String clusterName                  = "ORG.CODEHAUS.WADI.TEST.CLUSTER";

		InetSocketAddress isa0=new InetSocketAddress("localhost", 8080);
		_cluster0=clusterFactory.createCluster(clusterName);
		_cluster0.addClusterListener(new MyClusterListener());
		HttpProxy proxy0=new StandardHttpProxy("jsessionid");
		_dispatcher0=new MessageDispatcher(_cluster0);
		_location0=new HttpProxyLocation(_cluster0.getLocalNode().getDestination(), isa0, proxy0);
		_relocater0=new SwitchableRelocationStrategy();
		_servlet0=new MyServlet("0", _cluster0, new MyContextPool(), _relocater0);
		_filter0=new MyFilter("0", _servlet0);
		(_node0=new JettyNode("0", "localhost", 8080, "/test", "/home/jules/workspace/wadi/webapps/test", _filter0, _servlet0)).start();

		InetSocketAddress isa1=new InetSocketAddress("localhost", 8081);
		_cluster1=clusterFactory.createCluster(clusterName);
		_cluster1.addClusterListener(new MyClusterListener());
		HttpProxy proxy1=new CommonsHttpProxy("jsessionid");
		_dispatcher1=new MessageDispatcher(_cluster1);
		_location1=new HttpProxyLocation(_cluster1.getLocalNode().getDestination(), isa1, proxy1);
		_relocater1=new SwitchableRelocationStrategy();
		_servlet1=new MyServlet("1", _cluster1, new MyContextPool(), _relocater1);
		_filter1=new MyFilter("1", _servlet1);
		(_node1=new TomcatNode("1", "localhost", 8081, "/test", "/home/jules/workspace/wadi/webapps/test", _filter1, _servlet1)).start();
	    Thread.sleep(2000); // activecluster needs a little time to sort itself out...
	    _log.info("STARTING NOW!");
	}
	
	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
	    _log.info("STOPPING NOW!");
	    Thread.sleep(2000); // activecluster needs a little time to sort itself out...

	    _node1.stop();
		_node0.stop();
		super.tearDown();
	}

	/**
	 * Constructor for TestMigration.
	 * @param name
	 */
	public TestRelocation(String name) {
		super(name);
	}

	public int get(HttpClient client, HttpMethod method, String path) throws IOException, HttpException {
		client.setState(new HttpState());
		method.recycle();
		method.setPath(path);
		client.executeMethod(method);
		return method.getStatusCode();
	}
	
	public void testProxyInsecureRelocation() throws Exception {
		_relocater0.setRelocationStrategy(new ProxyRelocationStrategy(_cluster0, _dispatcher0, _location0, 2000, 3000));
		_relocater1.setRelocationStrategy(new ProxyRelocationStrategy(_cluster1, _dispatcher1, _location1, 2000, 3000));
		testInsecureRelocation(false);
		}
	
	public void testMigrateInsecureRelocation() throws Exception {
		_relocater0.setRelocationStrategy(new MigrateRelocationStrategy(_cluster0, _dispatcher0, _location0, 2000, new GZIPStreamingStrategy()));
		_relocater1.setRelocationStrategy(new MigrateRelocationStrategy(_cluster1, _dispatcher1, _location1, 2000, new GZIPStreamingStrategy()));
		testInsecureRelocation(true);
		}
		
	public void testInsecureRelocation(boolean migrating) throws Exception {
		HttpClient client=new HttpClient();
		HttpMethod method0=new GetMethod("http://localhost:8080");
		HttpMethod method1=new GetMethod("http://localhost:8081");

		Map m0=_servlet0.getMemoryMap();
		Map m1=_servlet1.getMemoryMap();
		Map c0=_servlet0.getClusterMap();
		Map c1=_servlet1.getClusterMap();
		
		assertTrue(m0.isEmpty());
		assertTrue(m1.isEmpty());
		assertTrue(c0.isEmpty());
		assertTrue(c1.isEmpty());
		
		// no sessions available locally
		_filter0.setLocalOnly(true);
		assertTrue(get(client, method0, "/test;jsessionid=foo")!=200);
		assertTrue(get(client, method0, "/test;jsessionid=bar")!=200);
		_filter1.setLocalOnly(true);
		assertTrue(get(client, method1, "/test;jsessionid=foo")!=200);
		assertTrue(get(client, method1, "/test;jsessionid=bar")!=200);
		
		assertTrue(m0.isEmpty());
		assertTrue(m1.isEmpty());
		assertTrue(c0.isEmpty());
		assertTrue(c1.isEmpty());
		
		m0.put("foo", new MyContext());
		m1.put("bar", new MyContext());

		assertTrue(m0.size()==1);
		assertTrue(m1.size()==1);
		assertTrue(c0.isEmpty());
		assertTrue(c1.isEmpty());
		
		// 2/4 sessions available locally
		_filter0.setLocalOnly(true);
		assertTrue(get(client, method0, "/test;jsessionid=foo")==200);
		assertTrue(get(client, method0, "/test;jsessionid=bar")!=200);
		_filter1.setLocalOnly(true);
		assertTrue(get(client, method1, "/test;jsessionid=foo")!=200);
		assertTrue(get(client, method1, "/test;jsessionid=bar")==200);

		assertTrue(m0.size()==1);
		assertTrue(m1.size()==1);
		assertTrue(c0.isEmpty());
		assertTrue(c1.isEmpty());
		
		// 4/4 sessions available locally|remotely
		_filter0.setLocalOnly(false);
		assertTrue(get(client, method0, "/test;jsessionid=foo")==200);
		assertTrue(get(client, method0, "/test;jsessionid=bar")==200);
		
		if (migrating) {
			assertTrue(m0.size()==2);
			Thread.sleep(1000); // can take a while for ACK to be processed
			assertTrue(m1.size()==0);
			
			// TODO - what about location caches ?
			
		} else {
			assertTrue(m0.size()==1);
			assertTrue(m1.size()==1);			
			// TODO - what about location caches ?
		}
		
		_filter1.setLocalOnly(false);
		assertTrue(get(client, method1, "/test;jsessionid=foo")==200);
		assertTrue(get(client, method1, "/test;jsessionid=bar")==200);

		if (migrating) {
			assertTrue(m1.size()==2);
			Thread.sleep(1000); // can take a while for ACK to be processed
			assertTrue(m0.size()==0);
			// TODO - what about location caches ?
		} else {
			assertTrue(m0.size()==1);
			assertTrue(m1.size()==1);
			assertTrue(c0.size()==1); // location from clusterwide query has been cached
			assertTrue(c1.size()==1); // location from clusterwide query has been cached
		}
		
		// ensure that cached locations work second time around...
		_filter0.setLocalOnly(false);
		assertTrue(get(client, method0, "/test;jsessionid=foo")==200);
		assertTrue(get(client, method0, "/test;jsessionid=bar")==200);

		if (migrating) {
			assertTrue(m0.size()==2);
			Thread.sleep(1000); // can take a while for ACK to be processed
			assertTrue(m1.size()==0);
			// TODO - what about location caches ?
		} else {
			assertTrue(m0.size()==1);
			assertTrue(m1.size()==1);			
			// TODO - what about location caches ?
		}

		_filter1.setLocalOnly(false);
		assertTrue(get(client, method1, "/test;jsessionid=foo")==200);
		assertTrue(get(client, method1, "/test;jsessionid=bar")==200);
		
		if (migrating) {
			assertTrue(m1.size()==2);
			Thread.sleep(1000); // can take a while for ACK to be processed
			assertTrue(m0.size()==0);		
			// TODO - what about location caches ?
		} else {
			assertTrue(m0.size()==1);
			assertTrue(m1.size()==1);
			assertTrue(c0.size()==1);
			assertTrue(c1.size()==1);
		}
	}
	
	public void testProxySecureRelocation() throws Exception {
		_relocater0.setRelocationStrategy(new ProxyRelocationStrategy(_cluster0, _dispatcher0, _location0, 2000, 3000));
		_relocater1.setRelocationStrategy(new ProxyRelocationStrategy(_cluster1, _dispatcher1, _location1, 2000, 3000));
		testSecureRelocation(false);
		}
	
	public void testMigrateSecureRelocation() throws Exception {
		_relocater0.setRelocationStrategy(new MigrateRelocationStrategy(_cluster0, _dispatcher0, _location0, 2000, new GZIPStreamingStrategy()));
		_relocater1.setRelocationStrategy(new MigrateRelocationStrategy(_cluster1, _dispatcher1, _location1, 2000, new GZIPStreamingStrategy()));
		testSecureRelocation(true);
		}
		
	public void testSecureRelocation(boolean migrating) throws Exception {
	    HttpClient client=new HttpClient();
		HttpMethod method0=new GetMethod("http://localhost:8080");
		HttpMethod method1=new GetMethod("http://localhost:8081");

		Map m0=_servlet0.getMemoryMap();
		Map m1=_servlet1.getMemoryMap();
		Map c0=_servlet0.getClusterMap();
		Map c1=_servlet1.getClusterMap();
		
		assertTrue(m0.isEmpty());
		assertTrue(m1.isEmpty());
		assertTrue(c0.isEmpty());
		assertTrue(c1.isEmpty());
		
		m0.put("foo", new MyContext());
		m1.put("bar", new MyContext());

		assertTrue(m0.size()==1);
		assertTrue(m1.size()==1);
		assertTrue(c0.isEmpty());
		assertTrue(c1.isEmpty());
		
		assertTrue(!_node0.getSecure());
		// won't run locally
		assertTrue(get(client, method0, "/test/confidential;jsessionid=foo")==403); // forbidden
		// won't run remotely
		assertTrue(get(client, method0, "/test/confidential;jsessionid=bar")==403); // forbidden
		
		_node0.setSecure(true);	
		assertTrue(_node0.getSecure());
		// will run locally - since we have declared the Listener secure
		assertTrue(get(client, method0, "/test/confidential;jsessionid=foo")==200);
		// will run remotely - proxy should preserve confidentiality on remote server...
		assertTrue(get(client, method0, "/test/confidential;jsessionid=bar")==200);

		assertTrue(!_node1.getSecure());
		// won't run locally
		assertTrue(get(client, method1, "/test/confidential;jsessionid=bar")==403); // forbidden
		// won't run remotely
		assertTrue(get(client, method1, "/test/confidential;jsessionid=foo")==403); // forbidden

		_node1.setSecure(true);	
		assertTrue(_node1.getSecure());
		// will run locally - since we have declared the Listener secure
		assertTrue(get(client, method1, "/test/confidential;jsessionid=bar")==200);
		// will run remotely - proxy should preserve confidentiality on remote server...
		assertTrue(get(client, method1, "/test/confidential;jsessionid=foo")==200);
	}
	
	// TODO:
	// consider merging two classes
	// consider having MigrationConceptualiser at top of stack (to promote sessions to other nodes)
	// Consider a MigrationContextualiser in place of the Location tier, which only migrates
	// and a HybridContextualiser, which sometimes proxies and sometimes migrates...
	// consider moving back to a more jcache like architecture, where the CacheKey is a compound - Req, Chain, etc...
	// lookup returns a FilterChain to be run.... (problem - pause between lookup and locking - think about it).
	// if we have located a session and set up a timeout, this should be released after the first proxy to it...
	// 8080, 8081 should only be encoded once...
    
	static class Test implements MyServlet.Test {
		protected int _count=0;
		public int getCount(){return _count;}
		public void setCount(int count){_count=count;}
		
		protected boolean _stateful;
		public boolean isStateful(){return _stateful;}

		public void test(ServletRequest req, ServletResponse res){
			_count++;
			try {
				((javax.servlet.http.HttpServletRequest)req).getSession();
				_stateful=true;
			} catch (UnsupportedOperationException ignore){
				_stateful=false;
			}
		}
	};
	
	public void testRelocationStatelessContextualiser() throws Exception {
		_relocater0.setRelocationStrategy(new ProxyRelocationStrategy(_cluster0, _dispatcher0, _location0, 2000, 3000));
		_relocater1.setRelocationStrategy(new ProxyRelocationStrategy(_cluster1, _dispatcher1, _location1, 2000, 3000));
		testStatelessContextualiser(false);
		}
	
	public void testMigrateStatelessContextualiser() throws Exception {
		_relocater0.setRelocationStrategy(new MigrateRelocationStrategy(_cluster0, _dispatcher0, _location0, 2000, new GZIPStreamingStrategy()));
		_relocater1.setRelocationStrategy(new MigrateRelocationStrategy(_cluster1, _dispatcher1, _location1, 2000, new GZIPStreamingStrategy()));
		testStatelessContextualiser(true);
		}
		
	public void testStatelessContextualiser(boolean migrating) throws Exception {

		HttpClient client=new HttpClient();
		HttpMethod method0=new GetMethod("http://localhost:8080");
		HttpMethod method1=new GetMethod("http://localhost:8081");
		
		Map m0=_servlet0.getMemoryMap();
		Map m1=_servlet1.getMemoryMap();

		m0.put("foo", new MyContext());
		m1.put("bar", new MyContext());

		Test test;
		
		// this won't be proxied, because we can prove that it is stateless...
		test=new Test();
		_servlet0.setTest(test);
		assertTrue(get(client, method0, "/test/static.html;jsessionid=bar")==200);
		assertTrue(test.getCount()==1 && !test.isStateful());
		_servlet0.setTest(null);
		
		MyServlet servlet;

		servlet=migrating?_servlet0:_servlet1;		
		// this will be proxied, because we cannot prove that it is stateless...
		test=new Test();		
		servlet.setTest(test);
		assertTrue(get(client, method0, "/test/dynamic.dyn;jsessionid=bar")==200);
		assertTrue(test.getCount()==1 && test.isStateful());
		servlet.setTest(null);
		
		// this won't be proxied, because we can prove that it is stateless...
		test=new Test();
		_servlet1.setTest(test);
		assertTrue(get(client, method1, "/test/static.html;jsessionid=foo")==200);
		assertTrue(test.getCount()==1 && !test.isStateful());
		_servlet1.setTest(null);

		servlet=migrating?_servlet1:_servlet0;
		// this will be proxied, because we cannot prove that it is stateless...
		test=new Test();
		servlet.setTest(test);
		assertTrue(get(client, method1, "/test/dynamic.jsp;jsessionid=foo")==200);
		assertTrue(test.getCount()==1 && test.isStateful());
		servlet.setTest(null);
	}
}