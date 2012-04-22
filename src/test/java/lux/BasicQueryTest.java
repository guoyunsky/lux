package lux;

import static org.junit.Assert.*;

import java.util.ArrayList;

import lux.api.ValueType;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.junit.Test;

/**
 * Tests the parsing of XPath expressions and the generation
 * of a supporting Lucene query using only node name indexes.
 * 
 * TODO: add some tests with empty steps like self::* and self::node() axis - these should maintain
 * minimality.
 */
public class BasicQueryTest {
    
    private static final String Q_ATTR = "lux_att_name_ms:attr";
    private static final String Q_BAR = "lux_elt_name_ms:bar";
    private static final String Q_FOO_BAR = "+lux_elt_name_ms:foo +lux_elt_name_ms:bar";
    private static final String Q_FOO_OR_BAR = "lux_elt_name_ms:foo lux_elt_name_ms:bar";
    private static final String Q_FOO = "lux_elt_name_ms:foo";

    public static void assertQuery (String xpath, int facts, String ... queries) {
        assertQuery (xpath, facts, null, queries);
    }
    
    /**
     * asserts that the given xpath generates the lucene query string, and 
     * that the asserted facts are passed to lux:search
     * 
     * @param xpath the expression to be tested
     * @param facts facts expected for the first query
     * @param type return type expected for the first query
     * @param queries the expected lucene query strings
     */

    public static void assertQuery (String xpath, int facts, ValueType type, String ... queries) {
        Saxon saxon = new Saxon();
        SaxonExpr expr = saxon.compile(xpath);
        AbstractExpression ex = saxon.getTranslator().exprFor(expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        SearchExtractor extractor = new SearchExtractor();
        ex.accept(extractor);
        assertEquals ("wrong number of queries for " + xpath, queries.length, extractor.queries.size());
        for (int i = 0; i < queries.length; i++) {
            assertEquals (queries[i], extractor.queries.get(i).toString());
        }
        if (queries.length > 0) {
            boolean isMinimal = (facts & XPathQuery.MINIMAL) != 0;
            assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                    isMinimal, extractor.queries.get(0).isFact(XPathQuery.MINIMAL));
            assertEquals ("query counting for xpath " + xpath, (facts & XPathQuery.COUNTING) != 0, 
                    extractor.queries.get(0).isFact(XPathQuery.COUNTING));
            if (type != null) {
                if ( ! (type == ValueType.DOCUMENT || type == ValueType.BOOLEAN)) {
                    // the other types don't get passed to lux:search as facts
                    type = ValueType.VALUE;
                }
                assertSame (type, extractor.queries.get(0).getResultType());
            }
        }
    }

    static class MockQuery extends XPathQuery {
        private String queryString;

        MockQuery (String queryString, long facts) {
            super (null, null, facts, ValueType.VALUE);
            this.queryString = queryString;
        }

        public String toString() {
            return queryString;
        }
        
        @Override
        public ValueType getResultType () {
            if (isFact(XPathQuery.DOCUMENT_RESULTS)) {
                return ValueType.DOCUMENT;
            }
            if (isFact(XPathQuery.BOOLEAN_FALSE) || isFact(XPathQuery.BOOLEAN_TRUE)) {
                return ValueType.BOOLEAN;
            }
            return ValueType.VALUE;
        }
    }

    static class SearchExtractor extends ExpressionVisitorBase {
        ArrayList<XPathQuery> queries = new ArrayList<XPathQuery>();
        
        public FunCall visit (FunCall funcall) {
            if (funcall.getQName().equals (FunCall.luxSearchQName)
                    || funcall.getQName().equals (FunCall.luxCountQName) 
                    || funcall.getQName().equals (FunCall.luxExistsQName)) {
                String q = ((LiteralExpression)funcall.getSubs()[0]).getValue().toString();
                long facts = (Long) ((LiteralExpression)funcall.getSubs()[1]).getValue();
                queries.add( new MockQuery (q, facts));
            }
            return funcall;
        }
        
    }
    
    protected static final String MATCH_ALL = new MatchAllDocsQuery().toString();
    
    @Test public void testNoQuery () throws Exception {

        try {
            assertQuery ("", XPathQuery.MINIMAL, ValueType.DOCUMENT);
            assertFalse ("expected syntax error", true);
        } catch (Exception e) { }

        try {
            assertQuery (null, XPathQuery.MINIMAL, ValueType.DOCUMENT);
            assertFalse ("expected NPE", true);
        } catch (NullPointerException e) {}
    }
    
    @Test public void testParseError () throws Exception {
        try {
            assertQuery ("bad xpath here", 0);
            assertFalse ("expected syntax error", true);
        } catch (Exception ex) { }
    }

    @Test public void testMatchAll () throws Exception {
        // Can you have an XML document with no elements?  I don't think so
        //assertQuery ("*", MATCH_ALL, true, ValueType.ELEMENT);
        //assertQuery ("node()", MATCH_ALL, true, ValueType.NODE);
        assertQuery ("/*", XPathQuery.MINIMAL, ValueType.ELEMENT, MATCH_ALL);
        assertQuery ("/node()", XPathQuery.MINIMAL, ValueType.NODE, MATCH_ALL);
        assertQuery ("/self::node()", XPathQuery.MINIMAL, ValueType.DOCUMENT, MATCH_ALL);
        //assertQuery ("self::node()", MATCH_ALL, XPathQuery.MINIMAL);
    }
    
    @Test public void testSlash() throws Exception {
        assertQuery ("/", XPathQuery.MINIMAL, ValueType.DOCUMENT, MATCH_ALL);
    }
    
    @Test public void testElementPredicate() throws Exception {
        assertQuery ("(/)[.//foo]", XPathQuery.MINIMAL, ValueType.DOCUMENT, Q_FOO); 
    }

    @Test public void testElementPaths () throws Exception {
       
        assertQuery ("//foo", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_FOO);

        assertQuery ("/*/foo", 0, ValueType.ELEMENT, Q_FOO);
        
        assertQuery ("/foo//*", 0, ValueType.ELEMENT, Q_FOO);

        assertQuery ("/foo", 0, ValueType.ELEMENT, Q_FOO);
        
        assertQuery ("/foo/text()", 0, ValueType.ELEMENT, Q_FOO);
        // assertQuery ("foo/text()", Q_FOO, false public void testAttributePaths ( {
        // FIXME: compute minimality properly for attributes
        
        assertQuery ("//*/@attr", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q_ATTR);
        
        assertQuery ("//node()/@attr", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q_ATTR);
    }
    
    @Test public void testAttributePredicates () throws Exception {
        assertQuery ("//*[@attr]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_ATTR);

        assertQuery ("(/)[.//*/@attr]", XPathQuery.MINIMAL, ValueType.DOCUMENT, Q_ATTR);        
    }

    @Test public void testElementAttributePaths () throws Exception {
        
        assertQuery ("//foo/@id", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, "+lux_elt_name_ms:foo +lux_att_name_ms:id");

        assertQuery ("//foo/@*", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q_FOO);
    }

    @Test public void testTwoElementPaths () throws Exception {
        
        assertQuery ("//*/foo/bar", 0, ValueType.ELEMENT, Q_FOO_BAR);

        assertQuery ("/foo//bar", 0, ValueType.ELEMENT, Q_FOO_BAR);
    }
    
    @Test public void testTwoElementPredicates () throws Exception {
        assertQuery ("(/)[.//foo][.//bar]", XPathQuery.MINIMAL, ValueType.DOCUMENT, Q_FOO_BAR);
    }
    
    @Test public void testUnion () throws Exception {
        assertQuery ("foo|bar", 0, ValueType.ELEMENT);

        assertQuery ("//foo|//bar", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_FOO_OR_BAR);
    }

    @Test public void testPositionalPredicate () throws Exception {
        // FIXME: when running the whole test suite this fails.  It looks like
        // the immutable MATCH_ALL query is having its return type modified?
        assertQuery ("//foo/bar[1]", 0, ValueType.ELEMENT, Q_FOO_BAR);
        assertQuery ("/descendant-or-self::bar[1]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_BAR);
        assertQuery ("//bar[1]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_BAR);
        
        assertQuery ("//bar[last()]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_BAR);

        // not minimal, since there may not be a second bar element
        assertQuery ("//bar[2]", 0, ValueType.ELEMENT, Q_BAR);
    }
    
    @Test public void testSubsequence () throws Exception {
        assertQuery ("subsequence (//foo, 1, 1)", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_FOO);
        // save this until we have implemented some kind of pagination in XPathQuery
        // lux:search and XPathCollector
        // assertQuery ("subsequence (//foo, 2)", 0, Q_FOO);
        assertQuery ("subsequence (//foo, 1, 10)", XPathQuery.MINIMAL, ValueType.ELEMENT, Q_FOO);
        //assertQuery ("subsequence (//foo, 10, 10)", 0, Q_FOO);
    }

    @Test public void testMultiElementPaths () throws Exception {
        assertQuery ("//foo/title | //bar/title | //baz/title", 
                     0,
                     ValueType.ELEMENT,
                     "+lux_elt_name_ms:foo +lux_elt_name_ms:title",
                     "+lux_elt_name_ms:bar +lux_elt_name_ms:title",
                     "+lux_elt_name_ms:baz +lux_elt_name_ms:title"); 
    }

    @Test public void testElementValueNoPath () throws Exception {
        // depends on context expression when there is none
        assertQuery ("foo[.='content']", 0, ValueType.ELEMENT);
    }
    
    @Test public void testElementValue () throws Exception {
        assertQuery ("/foo[.='content']", 0, ValueType.ELEMENT, Q_FOO);

        assertQuery ("/foo[bar='content']", 0, ValueType.ELEMENT, Q_FOO_BAR);

        assertQuery ("//foo[.='content']", 0, ValueType.ELEMENT, Q_FOO);

        assertQuery ("//foo[bar='content']", 0, ValueType.ELEMENT, Q_FOO_BAR);

    }
    
    @Test public void testAncestorOrSelf () throws Exception {
        assertQuery ("/ancestor-or-self::node()", XPathQuery.MINIMAL, ValueType.DOCUMENT, "*:*");
    }
    
    @Test public void testSelf () throws Exception {
        assertQuery ("/self::node()", XPathQuery.MINIMAL, ValueType.DOCUMENT, "*:*");
    }
    
    @Test public void testAtomicResult () throws Exception {
        assertQuery ("number(/doc/test[1])", 0, ValueType.ATOMIC, "+lux_elt_name_ms:doc +lux_elt_name_ms:test");
        assertQuery ("number(//test[1])", XPathQuery.MINIMAL, ValueType.ATOMIC, "lux_elt_name_ms:test");
    }
    
    @Test public void testCounting () throws Exception {
        assertQuery ("count(/)", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC, "*:*");
        assertQuery ("count(//foo)", XPathQuery.MINIMAL, ValueType.ATOMIC, Q_FOO);
        assertQuery ("count(//foo/root())", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC, Q_FOO);
        assertQuery ("count(//foo/ancestor::document-node())", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC, Q_FOO);
        assertQuery ("count(//foo/bar/ancestor::document-node())", 0, ValueType.ATOMIC, Q_FOO_BAR);
    }
    
    @Test public void testExistenceFunctions() throws Exception {
        assertQuery ("exists(/)", XPathQuery.MINIMAL, ValueType.BOOLEAN, "*:*");
        assertQuery ("exists(//foo)", XPathQuery.MINIMAL, ValueType.BOOLEAN, Q_FOO);
        assertQuery ("exists(//foo) and exist(//bar)", XPathQuery.MINIMAL, ValueType.BOOLEAN, Q_FOO_OR_BAR);
        assertQuery ("exists(//foo/root()//bar)", XPathQuery.MINIMAL, ValueType.BOOLEAN, Q_FOO_BAR);
        assertQuery ("exists(//foo/root() intersect //bar/root())", XPathQuery.MINIMAL, ValueType.BOOLEAN, Q_FOO_BAR);
    }

    @Test public void testPredicateNegation () throws Exception {
        assertQuery ("//foo[not(bar)]", 0, ValueType.ELEMENT, Q_FOO);
        assertQuery ("//foo[count(bar) = 0]", 0, ValueType.ELEMENT, "lux_elt_name_ms:foo");
    }

}