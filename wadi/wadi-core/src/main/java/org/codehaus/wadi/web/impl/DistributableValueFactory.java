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
package org.codehaus.wadi.web.impl;

import org.codehaus.wadi.Value;
import org.codehaus.wadi.web.ValueHelperRegistry;

/**
 *
 * @author <a href="mailto:jules@coredevelopers.net">Jules Gosnell</a>
 * @version $Revision: 1139 $
 */
public class DistributableValueFactory extends StandardValueFactory {
    protected final ValueHelperRegistry valueHelperRegistry;
    
    public DistributableValueFactory(ValueHelperRegistry valueHelperRegistry) {
        if (null == valueHelperRegistry) {
            throw new IllegalArgumentException("valueHelperRegistry is required");
        }
        this.valueHelperRegistry = valueHelperRegistry;
    }

    public Value create() {
        return new DistributableValue(valueHelperRegistry);
    }

}
