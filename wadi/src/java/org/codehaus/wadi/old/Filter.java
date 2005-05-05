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

package org.codehaus.wadi.old;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// TODO

// will filter do anything unless manager is 'distributable' ? it will
// still need to fix session id...

// TODO - we need, in some cases, to wrap the req/res and rewrite the
// session cookie to exclude/include mod_jk routing info. mod_jk and
// client need to see this. webapp and container do not.

// TODO - if we were not accepting sessions, we would need to know if
// an accessible session was local or evicted, so that we could proxy
// the request and force its loading in another container...

// TODO - if we wanted to spill a few sessions we could wait for them
// to become free, evict them and then proxy subsequent requests until
// client catches up with new location...

/**
 * Installed at front of Filter stack. Manages WADI-specific fn-ality.
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */
public class
  Filter
  implements javax.servlet.Filter
{
  protected Log _log = LogFactory.getLog(getClass());

  protected Manager _manager;
  protected boolean _distributable;

  // Filter Lifecycle

  public void
    init(FilterConfig filterConfig)
  {
    _manager=(Manager)filterConfig.getServletContext().getAttribute(org.codehaus.wadi.old.Manager.class.getName());
    if (_manager==null)
      _log.fatal("Manager not found");
    else
      if (_log.isTraceEnabled()) _log.trace("Manager found: "+_manager);

    _manager.setFilter(this);
    _distributable=_manager.getDistributable();

  }

  public void
    destroy()
  {
    _distributable=false;
    _manager=null;
  }

  // Filter fn-ality

  public void
    doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException
  {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse))
    {
      _log.warn("not an HttpServlet req/res pair - therefore stateless - ignored by WADI");
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest req=(HttpServletRequest)request;
    HttpServletResponse res=(HttpServletResponse)response;

    _manager.setInside(true);	// entering container...

    String id=req.getRequestedSessionId();
    HttpSessionImpl impl=null;

    try
    {
      if (id==null)
      {
	// TODO - at the moment, nothing, but later we should consider
	// whether we want to run this request here - it may create a
	// session - or somewhere else...
      }
      else
      {
	String realId=_manager.getRoutingStrategy().strip(id);

	impl=_manager.getLocalSession(realId);
	if (impl!=null)
	{
	  boolean acquired=false;
	  while (acquired==false)
	  {
	    try
	    {
	      impl.getApplicationLock().acquire(); // TODO - timeout
	      acquired=true;
	    }
	    catch (InterruptedException ignore)
	    {
	      // interrupts are ignored here. Jetty interrupts request
	      // threads on shutdown, but we want the thread to
	      // complete correctly....
	      _log.trace(realId+": interrupted whilst acquiring application lock");
	    }
	  }

	  HttpSession facade=(HttpSession)impl.getFacade();
	  if (facade==null || !facade.isValid())
	  {
	    impl.getApplicationLock().release();
	    impl=null;
	    _log.debug(realId+": session disappeared before it could be locked into container");
	  }
	}

	if (impl==null)
	{
	  if (_manager.getDistributable())
	  {
	    _log.debug(realId+": getRemoteSession()");

	    for (int n=_manager.getImmigrationAttemptCount();
		 n>0 && (impl=_manager.getRemoteSession(realId))==null;	// returns already app-locked...
		 n--)
	      _log.info("immigration unsuccessful - retrying ["+n+"]");

	    if (impl==null) _log.warn(realId+": SESSION IMMIGRATION FAILED");
	  }
	  else
	  {
	    // we cannot relocate the session to this request, so we
	    // must relocate the request to the session...
	    ManagerProxy proxy=_manager.locate(realId);
	    if (proxy!=null)
	    {
	      proxy.relocateRequest(req, res, _manager);
	      return;
	    }
	  }
	}

	if(impl==null)
	  _log.error(realId+": session id cannot be mapped");
	else
	{
	  HttpSession facade=(HttpSession)impl.getFacade();
	  if (facade!=null && facade.isValid())
	  {
	    // restick lb to this node if necessary...
	    if (req.isRequestedSessionIdFromCookie())
	      _manager.getRoutingStrategy().rerouteCookie(req, res, _manager, id);
	    else if (req.isRequestedSessionIdFromURL())
	      _manager.getRoutingStrategy().rerouteURL();	// NYI
	  }
	  else
	  {
	    _log.debug("facade: "+facade);
	    _log.debug("facade,isValid(): "+(facade!=null?facade.isValid():false));
	    _log.debug(realId+": maps to invalid session");
	  }
	}
      }

      if (impl!=null)
	impl.setLastAccessedTime(System.currentTimeMillis());

      chain.doFilter(request, response);
    }
    finally
    {
      // TODO - This needs to be rewritten to e.g. stash a lock in a
      // threadlocal when taken and remove it when released. If thread
      // still owns lock when it gets to here it should be
      // released....


      // ensure that this request's current session's shared lock is
      // released...

      // we have to look up the session again as it may have been
      // invalidated and even replaced during the request.
      javax.servlet.http.HttpSession session=req.getSession(false);
      if (session==null)
      {
	// no valid session - no lock to release...
	_log.trace("no outgoing session");
      }
      else
      {
	String newId=session.getId(); // can we not be more clever about this ?
	String newRealId=_manager.getRoutingStrategy().strip(newId);

	boolean reuse=_manager.getReuseSessionIds();
	// we have to release a lock
	if (id!=null && !reuse && id.equals(newRealId))
	{
	  // an optimisation, hopefully the most common case -
	  // saves us a lookup that we have already done...
	  impl.getApplicationLock().release();
	  if (_log.isTraceEnabled()) _log.trace(newRealId+": original session maintained throughout request");
	}
	else
	{
	  // we cannot be sure that the session coming out of the
	  // request is the same as the one that went in to it, so
	  impl=_manager.getLocalSession(newRealId);
	  // session must still be valid, since we have not yet
	  // released our lock, so no need to check...

	  impl.getApplicationLock().release();
	  if (reuse)
	    if (_log.isTraceEnabled()) _log.trace(newRealId+": potential session id reuse - outgoing session may be new");
	    else
	      if (_log.isTraceEnabled()) _log.trace(newRealId+": new outgoing session");
	}
      }

      // a little bit fragile with all this stuff above...
      _manager.setInside(false); // leaving container...
    }
  }
}