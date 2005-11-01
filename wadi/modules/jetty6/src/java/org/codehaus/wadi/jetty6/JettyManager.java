package org.codehaus.wadi.jetty6;

import java.io.InputStream;
import java.util.EventListener;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.ManagerConfig;
import org.codehaus.wadi.impl.AtomicallyReplicableSessionFactory;
import org.codehaus.wadi.impl.ListenerSupport;
import org.codehaus.wadi.impl.SpringManagerFactory;
import org.codehaus.wadi.impl.StandardManager;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.HttpOnlyCookie;
import org.mortbay.jetty.SessionManager;
import org.mortbay.thread.AbstractLifeCycle;

public class JettyManager extends AbstractLifeCycle implements ManagerConfig, SessionManager {

  protected final Log _log = LogFactory.getLog(getClass());

  protected final ListenerSupport _listeners=new ListenerSupport();

  protected StandardManager _wadi;
  protected ContextHandler.Context _context;
  protected boolean _usingCookies=true;
  protected int _maxInactiveInterval=60*30;

  // org.codehaus.wadi.ManagerConfig

  public ServletContext getServletContext() {
    return _context;
  }

  public void callback(StandardManager manager) {
    _listeners.installListeners(manager);
  }

  // org.mortbay.thread.AbstractLifecycle

  public void doStart() throws Exception {
    _context=ContextHandler.getCurrentContext();

    try {
      InputStream descriptor=_context.getContextHandler().getBaseResource().addPath("WEB-INF/wadi-web.xml").getInputStream();
      _wadi=(StandardManager)SpringManagerFactory.create(descriptor, "SessionManager", new AtomicallyReplicableSessionFactory(), new JettySessionWrapperFactory());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    _wadi.setMaxInactiveInterval(_maxInactiveInterval);
    _wadi.init(this);
    _wadi.start();
  }

  public void doStop() throws InterruptedException {
    try {
      _wadi.stop();
    } catch (Exception e) {
      _log.warn("unexpected problem shutting down", e);
    }
  }

  // org.mortbay.jetty.SessionManager

  public HttpSession getHttpSession(String id) {
    //throw new UnsupportedOperationException();
    return null; // FIXME - this will be the container trying to 'refresh' a session...
  }

  public HttpSession newHttpSession(HttpServletRequest request) {
    return _wadi.create().getWrapper();
  }

  protected boolean _secureCookies=false;

  public boolean getSecureCookies() {
    return _secureCookies;
  }

  protected boolean _httpOnly=true;

  public boolean getHttpOnly() {
    return _httpOnly;
  }

  public int getMaxInactiveInterval() {
    return _wadi.getMaxInactiveInterval();
  }

  public void setMaxInactiveInterval(int seconds) {
    _maxInactiveInterval=seconds;
  }

  public void addEventListener(EventListener listener) throws IllegalArgumentException, IllegalStateException {
    _listeners.addEventListener(listener);
  }

  public void removeEventListener(EventListener listener) throws IllegalStateException {
    _listeners.removeEventListener(listener);
  }

  // cut-n-pasted from Jetty src - aargh !
  // Greg uses Apache-2.0 as well - so no licensing issue as yet - TODO

  public Cookie
    getSessionCookie(javax.servlet.http.HttpSession session,boolean requestIsSecure)
  {
    if (isUsingCookies())
    {
      Cookie cookie = getHttpOnly()
	?new HttpOnlyCookie(SessionManager.__SessionCookie,session.getId())
	:new Cookie(SessionManager.__SessionCookie,session.getId());

      cookie.setPath("/");
      cookie.setMaxAge(-1);
      cookie.setSecure(requestIsSecure && getSecureCookies());

      if (_context!=null)
      {
	String domain=_context.getInitParameter(SessionManager.__SessionDomain);
	String maxAge=_context.getInitParameter(SessionManager.__MaxAge);
	String path=_context.getInitParameter(SessionManager.__SessionPath);

	if (path==null)
	  cookie.setPath(path);
	if (domain!=null)
	  cookie.setDomain(domain);
	if (maxAge!=null)
	  cookie.setMaxAge(Integer.parseInt(maxAge));
      }

      return cookie;
    }
    return null;
  }

  protected MetaManager _metaManager;

  public MetaManager getMetaManager() {
    return _metaManager;
  }

  public void setMetaManager(MetaManager metaManager) {
    _metaManager=metaManager;
  }

  // org.codehaus.wadi.jetty6.JettyManagerLoader

  protected boolean isUsingCookies() {
    return _usingCookies;
  }

}
