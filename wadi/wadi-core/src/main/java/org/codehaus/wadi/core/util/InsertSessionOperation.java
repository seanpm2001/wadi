/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.codehaus.wadi.core.util;

import org.codehaus.wadi.core.manager.SessionMonitor;
import org.codehaus.wadi.core.session.Session;
import org.codehaus.wadi.location.statemanager.StateManager;
import org.codehaus.wadi.replication.manager.ReplicationManager;

/**
 *
 * @version $Rev:$ $Date:$
 */
public class InsertSessionOperation {
    private final StateManager stateManager;
    private final SessionMonitor sessionMonitor;
    private final ReplicationManager replicationManager;

    public InsertSessionOperation(ReplicationManager replicationManager,
            SessionMonitor sessionMonitor,
            StateManager stateManager) {
        if (null == replicationManager) {
            throw new IllegalArgumentException("replicationManager is required");
        } else if (null == sessionMonitor) {
            throw new IllegalArgumentException("sessionMonitor is required");
        } else if (null == stateManager) {
            throw new IllegalArgumentException("stateManager is required");
        }
        this.replicationManager = replicationManager;
        this.sessionMonitor = sessionMonitor;
        this.stateManager = stateManager;
    }

    public Session insert(String key, CreateSessionOperation createSessionOperation) {
        stateManager.insert(key);
        Session session = createSessionOperation.create();
        sessionMonitor.notifySessionCreation(session);
        replicationManager.create(key, session);
        return session;
    }
}
