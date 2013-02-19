package lux.xpath;

import java.util.ArrayList;

import lux.SearchResultIterator;
import lux.compiler.XPathQuery;
import lux.index.IndexConfiguration;
import lux.query.BooleanPQuery;
import lux.xml.ValueType;
import lux.xquery.ElementConstructor;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.SortField;

/**
 * A special search function call; this holds a query that is used to accumulate constraints
 * while optimizing.  The function arguments are inferred from the supplied XPathQuery so as to
 * result in the same query when evaluated at run time.
 */
public class SearchCall extends FunCall {

    private AbstractExpression queryArg;
    private XPathQuery query; // for facts and sortFields only
    private boolean fnCollection;
    private final boolean generated; // records whether this search call was generated by the optimizer
    
    /**
     * creates a call to lux:search that encodes information provided by the optimizer, enabling combination
     * with additional filters and sorting criteria
     * @param query containing the information compiled by the optimizer
     * @param config used to determine the default field name
     */
    public SearchCall(XPathQuery query, IndexConfiguration config) {
        this (query.getParseableQuery().toXmlNode(config.getDefaultFieldName()), query.getFacts(), query.getResultType(), query.getSortFields(), true);
    }

    /** used to convert a generic lux:search FunCall into a SearchCall 
     * @param abstractExpression the query, as a string to be parsed by {@link lux.query.parser.LuxQueryParser},
     * or as a node to be parsed by {@link lux.query.parser.XmlQueryParser}.
     */
    public SearchCall(AbstractExpression abstractExpression) {
        this (abstractExpression, XPathQuery.MINIMAL, ValueType.VALUE, null, false);
    }
    
    private SearchCall(AbstractExpression queryArg, long facts, ValueType resultType, SortField[] sortFields, boolean isGenerated) {
        super(FunCall.LUX_SEARCH, resultType);
        this.queryArg = queryArg;
        fnCollection = false;
        query = XPathQuery.getQuery(null, facts, resultType, null, sortFields);
        this.generated = isGenerated;
        generateArguments();
    }
   
    public void combineQuery(XPathQuery additionalQuery, IndexConfiguration config) {
        ElementConstructor additional = additionalQuery.getParseableQuery().toXmlNode(config.getDefaultFieldName());
        if (! additional.getName().getLocalPart().equals("MatchAllDocsQuery")) {
            if (queryArg.getType() == Type.ELEMENT) {
                ElementConstructor addClause = new ElementConstructor(BooleanPQuery.CLAUSE_QNAME, additional, BooleanPQuery.MUST_OCCUR_ATT);
                ElementConstructor thisClause = new ElementConstructor(BooleanPQuery.CLAUSE_QNAME, queryArg, BooleanPQuery.MUST_OCCUR_ATT);
                ElementConstructor combined = new ElementConstructor(BooleanPQuery.BOOLEAN_QUERY_QNAME, new Sequence (thisClause, addClause));
                queryArg = combined;
            }
        }
        // TODO: combine optimizer constraints with user-defined (string) queries 
        this.query = this.query.combineBooleanQueries(Occur.MUST, additionalQuery, Occur.MUST, this.query.getResultType(), config);
        generateArguments();
    }
    
    private void generateArguments () {
        ArrayList<AbstractExpression> args = new ArrayList<AbstractExpression>();
        args.add (queryArg);
        args.add (new LiteralExpression(query.getFacts()));
        SortField[] sortFields = query.getSortFields();
        if (sortFields != null) {
            args.add(new LiteralExpression (createSortString(sortFields)));
        } else if (! generated) {
            // if this is an explicit function call that has no explicit ordering, order by relevance
            args.add(new LiteralExpression ("lux:score descending"));
        }
        subs = args.toArray(new AbstractExpression[args.size()]);
    }

    /**
     * create an string describing sort options to be passed as an argument search
     * @return
     */
    private String createSortString (SortField[] sort) {
        StringBuilder buf = new StringBuilder();
        if (sort != null) {
            for (SortField sortField : sort) {
                buf.append (sortField.getField());
                if (sortField.getReverse()) {
                    buf.append (" descending");
                }
                if (SearchResultIterator.MISSING_LAST.equals(sortField.getComparatorSource())) {
                    buf.append (" empty greatest");
                }
                switch (sortField.getType()) {
                case SortField.INT: buf.append(" int"); break;
                case SortField.LONG: buf.append(" long"); break;
                default: // default is string
                }
                buf.append (",");
            }
            if (buf.length() > 0) {
                buf.setLength(buf.length() - 1);
            }
        }
        return buf.toString();
    }

    /**
     * @return whether this function call will be represented by fn:collection("lux:" + query)
     */
    public boolean isFnCollection() {
        return fnCollection;
    }

    public void setFnCollection(boolean isFnCollection) {
        this.fnCollection = isFnCollection;
    }

}
