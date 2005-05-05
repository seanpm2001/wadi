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

package org.codehaus.wadi.old.impl;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import javax.jms.Destination;

import org.activecluster.Cluster;
import org.codehaus.wadi.old.HttpSessionImpl;
import org.codehaus.wadi.old.MigrationService;

public class
  MessagedMigrationRequest
  extends org.codehaus.wadi.old.MigrationRequest
{
  public
    MessagedMigrationRequest(String id, long timeout)
  {
    super(id, timeout);
  }

  public Object
    doit(MigrationService service, HttpSessionImpl impl, String correlationID, Destination source)
  {
    byte[] buffer=null;
    try
    {
      // TODO - yeugh !! - work this out - double serialisation is BAD!!
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      ObjectOutputStream    oos =new ObjectOutputStream(baos);
      impl.writeContent(oos);
      oos.flush();
      buffer=baos.toByteArray();
      oos.close();
    }
    catch (Exception e)
    {
      _log.warn("problem filling session buffer", e);
      return null;
    }

    Cluster cluster=service.getManager().getCluster();
    return service.getAsyncToSyncAdaptor().send(cluster,
						new MessagedMigrationResponse(_id, _timeout, buffer),
						correlationID,
						_timeout,
						cluster.getLocalNode().getDestination(),
						source,
						impl);
  }

  public Destination getReplyTo(Destination destination) {return destination;}
}