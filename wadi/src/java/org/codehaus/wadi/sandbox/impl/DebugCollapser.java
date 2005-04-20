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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.wadi.sandbox.Collapser;

import EDU.oswego.cs.dl.util.concurrent.ReentrantLock;
import EDU.oswego.cs.dl.util.concurrent.Sync;

/**
 * A lock Collapser, with useful debugging info
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */
public class DebugCollapser implements Collapser {
	protected final Log _log = LogFactory.getLog(getClass());

    class DebugSync extends ReentrantLock {
        protected int _counter;
        public synchronized void acquire() throws InterruptedException {
            _log.info("acquiring: "+ _counter);
            super.acquire();
            _log.info("acquired: "+ (++_counter));
        }
        
        public synchronized void release(){
            super.release();
            _log.info("released: "+ (--_counter));
        }
        
        public synchronized boolean attempt(long timeout) throws InterruptedException {
            _log.info("attempting: "+_counter);
            boolean success=super.attempt(timeout);
            _log.info("attempted: "+(++_counter));
            return success;
        }
    }

	protected Sync _sync=new DebugSync();

	/**
	 *
	 */
	public DebugCollapser() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.codehaus.wadi.sandbox.context.Collapser#getLock(java.lang.String)
	 */
	public Sync getLock(String id) {
		return _sync;
	}
}