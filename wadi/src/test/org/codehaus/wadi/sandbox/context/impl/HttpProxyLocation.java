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
package org.codehaus.wadi.sandbox.context.impl;

import java.net.InetSocketAddress;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.sandbox.context.HttpProxy;
import org.codehaus.wadi.sandbox.context.Location;

import EDU.oswego.cs.dl.util.concurrent.Sync;

/**
 * TODO - JavaDoc this type
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */

public class HttpProxyLocation implements Location {
	protected final static Log _log = LogFactory.getLog(HttpProxyLocation.class);
	
	protected InetSocketAddress _location;
	protected HttpProxy _proxy;
	
	public HttpProxyLocation(InetSocketAddress location, HttpProxy proxy) {
		super();
		_location=location;
		_proxy=proxy;
	}
	
	public boolean proxy(HttpServletRequest req, HttpServletResponse res, String id, Sync promotionLock) {
		boolean success=false;
		try {
			success=_proxy.proxy(_location, req, res);
		} catch (Exception e) {
			_log.error("request relocation failed: "+_location, e); // TODO - some cases here a resolvable...
		} finally {
			promotionLock.release();
		}
		
		return success;
	}

	public long getExpiryTime(){ return 0;}// TODO - NYI
	
	public String toString() {return "<HttpProxyLocation:"+_location+">";} // we could include proxy strategy here...
}
