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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionBindingListener;

import org.codehaus.wadi.sandbox.DistributableAttributesConfig;
import org.codehaus.wadi.sandbox.DistributableValueConfig;
import org.codehaus.wadi.sandbox.Value;
import org.codehaus.wadi.sandbox.AttributesConfig;

public class AtomicAttributes extends AbstractAttributes implements DistributableValueConfig {

    public AtomicAttributes(AttributesConfig config, Map map) {
        super(config, map);
    }

    protected Set _activationListenerNames;
    public Set getActivationListenerNames(){return _activationListenerNames;}
    
    protected Set _bindingListenerNames;
    public Set getBindingListenerNames(){return _bindingListenerNames;}
    
    public void readContent(ObjectInput oi) throws IOException, ClassNotFoundException {
        int size=oi.readInt();
        for (int i=0; i<size; i++) {
            Object key=oi.readObject();
            Object val=oi.readObject();
            Value a=_config.getAttributePool().take(this);
            a.setValue(val);
            _map.put(key, a);
        }
    }

    public void writeContent(ObjectOutput oo) throws IOException {
        oo.writeInt(size());
        
        Set activationListenerNames;
        Set bindingListenerNames;
        
        for (Iterator i=_map.entrySet().iterator(); i.hasNext();) {
            Map.Entry e=(Map.Entry)i.next();
            Object key=e.getKey();
            oo.writeObject(key);
            Value a=(Value)e.getValue();
            Object val=a.getValue();
            oo.writeObject(val);
            
            if (!_config.hasListeners()) {
                if (val instanceof HttpSessionActivationListener) {
                    // NYI
                }
                if (val instanceof HttpSessionBindingListener) {
                    // NYI
                }
            }
        }
    }

}
