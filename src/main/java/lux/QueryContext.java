package lux;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lux.xml.QName;

/**
 * Holds external query context: variable bindings and the context item.
 * TODO: convert java primitives to XdmValues?  Currently the Evaluator expects 
 * XdmValues.
 */
public class QueryContext {
    
    private HashMap<QName, Object> variables;
    
    private Object contextItem;
    
    /**
     * bind an external variable so that it will be available in the scope of queries evaluated using this context
     * @param varName the name of the variable to bind
     * @param value the value to bind to the variable; this must be of an appropriate type for the Evaluator,
     * or null to clear any existing binding.
     */
    public void bindVariable (QName varName, Object value) {
        if (variables == null) {
            variables = new HashMap<QName, Object>();
        }
        if (value == null) {
            variables.remove(varName);            
        } else {
            variables.put(varName, value);
        }
    }
    
    public Map<QName, Object> getVariableBindings() {
        if (variables == null) {
            return null;
        }
        return Collections.unmodifiableMap(variables);
    }
    
    public void setContextItem (Object contextItem) {
        this.contextItem = contextItem;
    }
    
    public Object getContextItem () {
        return contextItem;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
