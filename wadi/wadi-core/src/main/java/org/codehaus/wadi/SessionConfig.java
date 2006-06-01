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
package org.codehaus.wadi;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;


/**
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */
public interface SessionConfig extends Config {

    ValuePool getValuePool();
    AttributesFactory getAttributesFactory();
    HttpSessionListener[] getSessionListeners();
    HttpSessionAttributeListener[] getAttributeListeners();
    ServletContext getServletContext();

    void destroy(WebSession session);
    SessionWrapperFactory getSessionWrapperFactory();
    SessionIdFactory getSessionIdFactory();

    int getMaxInactiveInterval();

    void setLastAccessedTime(Evictable evictable, long oldTime, long newTime);
    void setMaxInactiveInterval(Evictable evictable, int oldInterval, int newInterval);

    Router getRouter();

}
