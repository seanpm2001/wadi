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

import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.StreamingStrategy;
import org.codehaus.wadi.sandbox.Session;
import org.codehaus.wadi.sandbox.ValuePool;
import org.codehaus.wadi.sandbox.AttributesPool;
import org.codehaus.wadi.sandbox.SessionConfig;
import org.codehaus.wadi.sandbox.SessionPool;
import org.codehaus.wadi.sandbox.impl.StandardSession;

/**
 * TODO - JavaDoc this type
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */

public class Manager implements SessionConfig {

    protected final Log _log = LogFactory.getLog(getClass());

    protected final SessionPool _sessionPool;
    protected final AttributesPool _attributesPool;
    protected final ValuePool _valuePool;
    
    public Manager(SessionPool sessionPool, AttributesPool attributesPool, ValuePool valuePool) {
        _sessionPool=sessionPool;
        _attributesPool=attributesPool;
        _valuePool=valuePool;
    }
    
    public boolean isStarted(){return false;}
    
    public HttpSession newHttpSession() {
        return createSession().getWrapper();
    }
    
    public void destroySession(StandardSession session) {
        // fetch any ActivationListeners - so that they are activated.
        // Session must appear to be in memory when invalidated ? - Think - TODO
        for (Iterator i=session.getActivationListenerNames().iterator(); i.hasNext();) // ALLOC ?
            session.getAttribute((String)i.next());
        if (_attributeListeners.size()>0) {
            // expensive - we are going to have to remove every
            // attribute - one by one, so that these listeners 
            // are correctly notified...
            // BindingListeners will fire, if they are present...
            for (Iterator i=session.getAttributeNameSet().iterator(); i.hasNext();) {
                // inefficient - results in a lot of dehashing - maybe we can improve this later - TODO...
                // perhaps an Attribute.Iterator, which we could advise with the same notification aspects ?
                session.removeAttribute((String)i.next());
            }
        } else {
            // remove any BindingListeners  - so that they are unbound
            for (Iterator i=session.getBindingListenerNames().iterator(); i.hasNext();) // ALLOC ?
                session.removeAttribute((String)i.next());
        }
        
        // TODO - remove from Contextualiser....at end of initial request ?
        _sessionPool.put(session);
    }

    public Session createSession() {
        return _sessionPool.take(this);
    }
    
    //----------------------------------------
    // Listeners

    // These lists are only modified at webapp [un]deployment time, by a
    // single thread, so although read by multiple threads whilst the
    // Manager is running, need no synchronization...

    protected final List _sessionListeners  =new ArrayList();
    public List getSessionListeners(){return _sessionListeners;}
    protected final List _attributeListeners=new ArrayList();
    public List getAttributeListeners(){return _attributeListeners;}

    public synchronized void
      addEventListener(EventListener listener)
        throws IllegalArgumentException, IllegalStateException
    {
      if (isStarted())
        throw new IllegalStateException("EventListeners must be added before a Session Manager starts");

      boolean known=false;
      if (listener instanceof HttpSessionAttributeListener)
      {
        if (_log.isDebugEnabled()) _log.debug("adding HttpSessionAttributeListener: "+listener);
        _attributeListeners.add(listener);
        known=true;
      }
      if (listener instanceof HttpSessionListener)
      {
        if (_log.isDebugEnabled()) _log.debug("adding HttpSessionListener: "+listener);
        _sessionListeners.add(listener);
        known=true;
      }

      if (!known)
        throw new IllegalArgumentException("Unknown EventListener type "+listener);
    }

    public synchronized void
      removeEventListener(EventListener listener)
        throws IllegalStateException
    {
      boolean known=false;

      if (isStarted())
        throw new IllegalStateException("EventListeners may not be removed while a Session Manager is running");

      if (listener instanceof HttpSessionAttributeListener)
      {
        if (_log.isDebugEnabled()) _log.debug("removing HttpSessionAttributeListener: "+listener);
        known|=_attributeListeners.remove(listener);
      }
      if (listener instanceof HttpSessionListener)
      {
        if (_log.isDebugEnabled()) _log.debug("removing HttpSessionListener: "+listener);
        known|=_sessionListeners.remove(listener);
      }

      if (!known)
        if (_log.isWarnEnabled()) _log.warn("EventListener not registered: "+listener);
    }
    
    // Context stuff
    public ServletContext getServletContext() {return null;}// TODO
    public HttpSessionContext getSessionContext() {return null;} // TODO
    
    public AttributesPool getAttributesPool() {return _attributesPool;}
    public ValuePool getValuePool() {return _valuePool;}
    
    public Manager getManager(){return this;}
    
}
