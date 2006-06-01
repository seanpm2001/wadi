/**
 * Copyright 2006 The Apache Software Foundation
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
package org.codehaus.wadi.replication.manager;

import org.codehaus.wadi.Replicater;
import org.codehaus.wadi.WebSession;

/**
 * 
 * @version $Revision$
 */
public class ReplicaterAdapter implements Replicater {
    private final ReplicationManager replicationManager;
    
    public ReplicaterAdapter(ReplicationManager replicationManager) {
        this.replicationManager = replicationManager;
    }

    public void create(Object tmp) {
        WebSession session = castAndEnsureType(tmp);
        replicationManager.create(session.getName(), session);
    }

    public void update(Object tmp) {
        WebSession session = castAndEnsureType(tmp);
        replicationManager.update(session.getName(), session);
    }

    public void destroy(Object tmp) {
        WebSession session = castAndEnsureType(tmp);
        replicationManager.destroy(session.getName());
    }

    public void acquireFromOtherReplicater(Object tmp) {
        WebSession session = castAndEnsureType(tmp);
        Object object = replicationManager.acquirePrimary(session.getName());
        // TODO initialize the state of the session. This is required to 
        // support reusingStore set to true.
    }
    
    public boolean getReusingStore() {
        // TODO this should actually be true. Yet, the migration protocol
        // does not work in this case.
        return false;
    }
    
    private WebSession castAndEnsureType(Object tmp) {
        if (false == tmp instanceof WebSession) {
            throw new IllegalArgumentException("tmp is not a Session");
        }
        return (WebSession) tmp;
    }
}
