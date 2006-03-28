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
/*
 * Created on Feb 14, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.codehaus.wadi.sandbox.wcache.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.sandbox.wcache.Cache;
import org.codehaus.wadi.sandbox.wcache.RequestProcessor;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;

/**
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 *
 * A Map of content in local memory. Exclusive access to the map is assumed.
 */
public class MemoryCache extends AbstractMappedCache {
	protected static final Log _log = LogFactory.getLog(MemoryCache.class);

	public MemoryCache(Evicter evicter, Cache subcache) {
		super(new ConcurrentReaderHashMap(), evicter, subcache);
	}

	public RequestProcessor put(String key, RequestProcessor val){return (RequestProcessor)_map.put(key, val);}
	public RequestProcessor peek(String key) {return (RequestProcessor)_map.get(key);}
	public RequestProcessor remove(String key) {return (RequestProcessor)_map.remove(key);}

	public boolean isOffNode() {return false;}
}