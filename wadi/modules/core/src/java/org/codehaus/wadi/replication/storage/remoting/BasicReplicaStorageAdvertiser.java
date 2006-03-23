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
package org.codehaus.wadi.replication.storage.remoting;

import javax.jms.Destination;
import javax.jms.ObjectMessage;

import org.codehaus.wadi.gridstate.Dispatcher;
import org.codehaus.wadi.replication.common.ComponentEventType;
import org.codehaus.wadi.replication.common.NodeInfo;
import org.codehaus.wadi.replication.storage.ReplicaStorage;


/**
 * 
 * @version $Revision$
 */
class BasicReplicaStorageAdvertiser implements ReplicaStorageAdvertiser {
    private final Dispatcher dispatcher;

    public BasicReplicaStorageAdvertiser(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void advertiseJoin(ReplicaStorage storage, NodeInfo nodeInfo) {
        Destination target = dispatcher.getDestination(nodeInfo.getName());
        sendToDestination(ComponentEventType.JOIN, storage.getHostingNode(), target);
    }

    public void advertiseJoin(ReplicaStorage storage) {
        sendToCluster(ComponentEventType.JOIN, storage);
    }

    public void advertiseLeave(ReplicaStorage storage) {
        sendToCluster(ComponentEventType.LEAVE, storage);
    }

    private void sendToCluster(ComponentEventType type, ReplicaStorage storage) {
        sendToDestination(type, storage.getHostingNode(), dispatcher.getClusterDestination());
    }

    private void sendToDestination(ComponentEventType type, NodeInfo nodeInfo, Destination target) {
        try {
            ObjectMessage message = dispatcher.createObjectMessage();
            message.setObject(new ReplicaStorageEvent(type, nodeInfo));
            dispatcher.send(target, message);
        } catch (Exception e) {
            throw (IllegalStateException) new IllegalStateException().initCause(e);
        }
    }
}