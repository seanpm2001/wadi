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
package org.codehaus.wadi.sandbox;

/**
 * API for objects that may be inspected to determine whether they should
 * be timed out after certain period of inactivity.
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */

public interface Evictable {
	public long getCreationTime();
	public void setCreationTime(long creationTime);
	public long getLastAccessedTime();
	public void setLastAccessedTime(long lastAccessedTime);
	public int  getMaxInactiveInterval();
	public void setMaxInactiveInterval(int maxInactiveInterval);

	public long getTimeToLive(long time);

	public boolean getValid();
	//public void setValid(boolean valid);
}