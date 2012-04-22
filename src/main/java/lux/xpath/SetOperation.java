package lux.xpath;

import lux.xpath.BinaryOperation.Operator;

public class SetOperation extends AbstractExpression {
    
    private final Operator operator;    
    
    public SetOperation (Operator operator, AbstractExpression ... ops) {
        super (Type.SetOperation);
        subs = ops;
        this.operator = operator;
    }
    
    public String toString () {
        StringBuilder buf = new StringBuilder ();
        buf.append('(');
        Sequence.appendSeq(buf, subs, ' ' + operator.toString() + ' ');
        buf.append (')');
        return buf.toString();
    }
    
    public AbstractExpression[] getsubs() {
        return subs;
    }
    
    public Operator getOperator () {
        return operator;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
}
