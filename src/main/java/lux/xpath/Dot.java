package lux.xpath;

public class Dot extends AbstractExpression {

    public Dot () {
        super (Type.Dot);
    }
    
    @Override
    public String toString() {        
        return ".";
    }

}
