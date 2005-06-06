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

package org.codehaus.wadi.sandbox.jetty;

import javax.management.MBeanException;

import org.codehaus.wadi.impl.jetty.JettyManager;
import org.mortbay.util.jmx.LifeCycleMBean;

/**
 * Publishes session manager API to JMX agent
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */
public class
  ManagerMBean
  extends LifeCycleMBean
{
  public ManagerMBean() throws MBeanException {}
  public ManagerMBean(JettyManager object) throws MBeanException {super(object);}

  protected void
    defineManagedResource()
  {
    super.defineManagedResource();
    // jetty/Manager
    defineAttribute("houseKeepingInterval");
    defineAttribute("sessionCookieName");
    defineAttribute("sessionUrlParamName");
    // shared/Manager
    defineAttribute("distributable");
    defineAttribute("maxInactiveInterval");
    // stats
    defineAttribute("specificationVersion");
    defineAttribute("sessionCreationCounter");
    defineAttribute("sessionDestructionCounter");
    defineAttribute("sessionExpirationCounter");
    defineAttribute("sessionInvalidationCounter");
    defineAttribute("sessionRejectionCounter");
    defineAttribute("sessionLoadCounter");
    defineAttribute("sessionStoreCounter");
    defineAttribute("sessionSendCounter");
    defineAttribute("sessionReceivedCounter");
    defineAttribute("sessionLocalHitCounter");
    defineAttribute("sessionStoreHitCounter");
    defineAttribute("sessionRemoteHitCounter");
    defineAttribute("sessionMissCounter");
    defineAttribute("requestAcceptedCounter");
    defineAttribute("requestRedirectedCounter");
    defineAttribute("requestProxiedCounter");
    defineAttribute("requestStatefulCounter");
    defineAttribute("requestStatelessCounter");
  }
}