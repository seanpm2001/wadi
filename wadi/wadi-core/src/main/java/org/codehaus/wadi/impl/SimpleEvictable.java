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
package org.codehaus.wadi.impl;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.codehaus.wadi.Evictable;

/**
 * A very Simple impementation of Evictable
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */

public abstract class SimpleEvictable implements Evictable, Serializable {
    protected long creationTime;
    protected long lastAccessedTime;
    protected int maxInactiveInterval;

    public void init(long creationTime, long lastAccessedTime, int maxInactiveInterval) {
        this.creationTime = creationTime;
        this.lastAccessedTime = lastAccessedTime;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public void destroy() throws Exception {
        creationTime = 0;
        lastAccessedTime = 0;
        maxInactiveInterval = 0;
    }

    public void copy(Evictable evictable) throws Exception {
        creationTime = evictable.getCreationTime();
        lastAccessedTime = evictable.getLastAccessedTime();
        maxInactiveInterval = evictable.getMaxInactiveInterval();
    }

    public void mote(Evictable recipient) throws Exception {
        recipient.copy(this);
        destroy();
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public long getTimeToLive(long time) {
        return maxInactiveInterval < 0 ? Long.MAX_VALUE : (maxInactiveInterval * 1000) - (time - lastAccessedTime);
    }

    public boolean getTimedOut(long time) {
        return getTimeToLive(time) <= 0;
    }
    
    public void readContent(ObjectInput oi) throws IOException, ClassNotFoundException {
        creationTime = oi.readLong();
        lastAccessedTime = oi.readLong();
        maxInactiveInterval = oi.readInt();
    }

    public void writeContent(ObjectOutput oo) throws IOException {
        oo.writeLong(creationTime);
        oo.writeLong(lastAccessedTime);
        oo.writeInt(maxInactiveInterval);
    }

}
