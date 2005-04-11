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

import org.codehaus.wadi.StreamingStrategy;
import org.codehaus.wadi.sandbox.AttributesPool;
import org.codehaus.wadi.sandbox.Dirtier;
import org.codehaus.wadi.sandbox.DistributableSessionConfig;
import org.codehaus.wadi.sandbox.SessionPool;
import org.codehaus.wadi.sandbox.ValuePool;

public class DistributableManager extends Manager implements DistributableSessionConfig {

    protected final StreamingStrategy _streamer;
    protected final Dirtier _dirtier;

    public DistributableManager(SessionPool sessionPool, AttributesPool attributesPool, ValuePool attributePool, StreamingStrategy streamer, Dirtier dirtier) {
        super(sessionPool, attributesPool, attributePool);
        _streamer=streamer;
        _dirtier=dirtier;
    }

    // Distributable
    public StreamingStrategy getStreamer() {return _streamer;}
    public Dirtier getDirtier() {return _dirtier;}
    
}
