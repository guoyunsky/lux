package lux.compiler;

import lux.xpath.AbstractExpression;
import lux.xquery.Variable;

/**
 * Captures information about variable bindings of let and for variables used during optimization
 */
public class VarBinding {
    private final Variable var;
    private final AbstractExpression expr;
    private final XPathQuery query;
    private final VarBinding shadowedBinding;
    
    public VarBinding (Variable var, AbstractExpression expr, XPathQuery query, VarBinding currentBinding) {
        this.var = var;
        this.expr = expr;
        this.shadowedBinding = currentBinding;
        this.query = query;
    }

    public Variable getVar() {
        return var;
    }

    public AbstractExpression getExpr() {
        return expr;
    }

    public XPathQuery getQuery() {
        return query;
    }

    public VarBinding getShadowedBinding() {
        return shadowedBinding;
    }
   
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */

