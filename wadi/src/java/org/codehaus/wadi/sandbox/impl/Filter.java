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

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.sandbox.impl.Manager;

public class Filter implements javax.servlet.Filter {

    protected final Log _log = LogFactory.getLog(getClass());

    protected Manager _manager;
    protected boolean _distributable;

    // Filter Lifecycle

    public void
      init(FilterConfig filterConfig) throws ServletException
    {
      _manager=(Manager)filterConfig.getServletContext().getAttribute(Manager.class.getName());
      if (_manager==null)
        _log.fatal("Manager not found");
      else
          _log.info("Manager found: "+_manager);

      _manager.setFilter(this);
      _distributable=_manager.getDistributable();
    }

    public void
      destroy()
    {
      _distributable=false;
      _manager=null;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        _log.info("WADI Filter handling...");
        chain.doFilter(request, response);
    }

}
