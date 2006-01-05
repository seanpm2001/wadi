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

import org.codehaus.wadi.sandbox.wcache.Cache;
import org.codehaus.wadi.sandbox.wcache.RequestProcessor;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;

/**
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 *
 * Promotion from this cache may result in some for of lazy remote reference.
 * Demotion to this cache should result in a migration off-node.
 *
 * This tier is responsible for finding, caching and listening out for changes to the location of recently mobile content.
 */
public class ClusterCache extends AbstractMappedCache {

	// TODO add ClusterListener for migrations - feed them into internal map
	public ClusterCache(Evicter evicter, Cache subcache) {
		super(new ConcurrentReaderHashMap(), evicter, subcache);
		}

	/* (non-Javadoc)
	 * @see org.codehaus.wadi.test.cache.Cache#put(java.lang.String, org.codehaus.wadi.test.cache.RequestProcessor)
	 */
	public RequestProcessor put(String key, RequestProcessor val) {
		// TODO - check for relevant waiting get()s - wake them.
		// do not raise notification, this will be done in migration code
		return (RequestProcessor)_map.put(key, val);
	}

	/* (non-Javadoc)
	 * @see org.codehaus.wadi.test.cache.Cache#get(java.lang.String)
	 */
	public RequestProcessor get(String key) {
		RequestProcessor val=peek(key);
		// TODO - send message querying location to whole cluster - waiting, with timeout, for an update to our _map;
		if (val==null)
			val=promote(key, _subcache);
		return val;
	}

	/* (non-Javadoc)
	 * @see org.codehaus.wadi.test.cache.Cache#remove(java.lang.String)
	 */
	public RequestProcessor remove(String key) {
		// what should we do here ?
		return (RequestProcessor)_map.remove(key);
	}

	public RequestProcessor peek(String key) {
		return (RequestProcessor)_map.get(key);
	}

	public boolean isOffNode() {return true;}
}
