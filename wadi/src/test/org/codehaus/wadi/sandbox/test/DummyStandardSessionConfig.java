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
package org.codehaus.wadi.sandbox.test;

import java.util.Collections;
import java.util.List;

import javax.servlet.ServletContext;

import org.codehaus.wadi.sandbox.AttributesPool;
import org.codehaus.wadi.sandbox.Evictable;
import org.codehaus.wadi.sandbox.EvicterConfig;
import org.codehaus.wadi.sandbox.Router;
import org.codehaus.wadi.sandbox.Session;
import org.codehaus.wadi.sandbox.SessionConfig;
import org.codehaus.wadi.sandbox.SessionIdFactory;
import org.codehaus.wadi.sandbox.SessionWrapperFactory;
import org.codehaus.wadi.sandbox.ValuePool;
import org.codehaus.wadi.sandbox.impl.DistributableAttributesFactory;
import org.codehaus.wadi.sandbox.impl.DistributableValueFactory;
import org.codehaus.wadi.sandbox.impl.DummyRouter;
import org.codehaus.wadi.sandbox.impl.SimpleAttributesPool;
import org.codehaus.wadi.sandbox.impl.SimpleValuePool;
import org.codehaus.wadi.sandbox.impl.TomcatSessionIdFactory;
import org.codehaus.wadi.sandbox.impl.jetty.JettySessionWrapperFactory;

class DummyStandardSessionConfig implements SessionConfig {
    
    protected final EvicterConfig _config;
    public DummyStandardSessionConfig(EvicterConfig config) {_config=config;}
    
    protected final ValuePool _valuePool=new SimpleValuePool(new DistributableValueFactory());
    public ValuePool getValuePool() {return _valuePool;}
    
    protected final AttributesPool _attributesPool=new SimpleAttributesPool(new DistributableAttributesFactory());
    public AttributesPool getAttributesPool() {return _attributesPool;}
    
    public List getSessionListeners() {return Collections.EMPTY_LIST;}
    
    public List getAttributeListeners() {return Collections.EMPTY_LIST;}
    
    public ServletContext getServletContext() {return null;}
    
    public void destroySession(Session session) {_config.expire(session);}
    
    protected final SessionWrapperFactory _sessionWrapperfactory=new JettySessionWrapperFactory();
    public SessionWrapperFactory getSessionWrapperFactory() {return _sessionWrapperfactory;}
    
    protected SessionIdFactory _sessionIdFactory=new TomcatSessionIdFactory();
    public SessionIdFactory getSessionIdFactory() {return _sessionIdFactory;}
    
    protected final int _maxInactiveInterval=2;
    public int getMaxInactiveInterval() {return _maxInactiveInterval;}
    
    public void setLastAccessedTime(Evictable evictable, long oldTime, long newTime) {
        throw new UnsupportedOperationException();
    }
    
    public void setMaxInactiveInterval(Evictable evictable, int oldInterval, int newInterval) {
        throw new UnsupportedOperationException();
    }
    
    protected final Router _router=new DummyRouter();
    public Router getRouter() {return _router;}
    
}