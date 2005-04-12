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
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionBindingListener;

import org.codehaus.wadi.sandbox.DistributableAttributesConfig;
import org.codehaus.wadi.sandbox.DistributableValueConfig;
import org.codehaus.wadi.sandbox.AttributesConfig;
import org.codehaus.wadi.sandbox.ValueHelper;

public class SimpleAttributes extends AbstractAttributes implements DistributableValueConfig {

    public SimpleAttributes(AttributesConfig config, Map map) {
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
            DistributableValue val=(DistributableValue)_config.getValuePool().take(this);
            val.readContent(oi);
            _map.put(key, val);
        }
    }

    public void writeContent(ObjectOutput oo) throws IOException {
        oo.writeInt(size());
        
        Set activationListenerNames;
        Set bindingListenerNames;
        
        DistributableAttributesConfig config=(DistributableAttributesConfig)_config;
        for (Iterator i=_map.entrySet().iterator(); i.hasNext();) {
            Map.Entry e=(Map.Entry)i.next();
            Object key=e.getKey();
            oo.writeObject(key);
            DistributableValue val=(DistributableValue)e.getValue();
            Object o=val.getValue();
            val.writeContent(oo);
            
            if (!config.getContextHasListeners()) {
                if (o instanceof HttpSessionActivationListener) {
                    // a bit silly that we spent all this time hiding Activationlistaners inside Value - yet need to know about them here...
                    // NYI
                }
                if (o instanceof HttpSessionBindingListener) {
                    // NYI
                }
            }
        }
    }
    
    public ValueHelper findHelper(Class type) {return ((DistributableAttributesConfig)_config).findHelper(type);}
    
    public boolean getContextHasListeners() {return ((DistributableAttributesConfig)_config).getContextHasListeners();}
    
    

}
