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
import org.codehaus.wadi.sandbox.Dirtier;
import org.codehaus.wadi.sandbox.DistributableAttributesConfig;
import org.codehaus.wadi.sandbox.DistributableSessionConfig;

/**
 * TODO - JavaDoc this type
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision$
 */

public class DistributableSession extends Session implements DistributableAttributesConfig {

    /**
     * @param manager
     */
    public DistributableSession(DistributableSessionConfig config) {
        super(config);
    }

    public Dirtier getDirtier() {return ((DistributableSessionConfig)_config).getDirtier();}
    public StreamingStrategy getStreamer() {return ((DistributableSessionConfig)_config).getStreamer();}
}
