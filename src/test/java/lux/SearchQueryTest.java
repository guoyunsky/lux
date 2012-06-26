package lux;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import lux.api.LuxException;
import lux.api.ResultSet;
import lux.api.ValueType;
import lux.index.XmlIndexer;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.saxon.UnOptimizer;
import lux.xpath.AbstractExpression;
import net.sf.saxon.s9api.XdmItem;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * executes all the BasicQueryTest test cases using path indexes and compares results against
 * an unindexed baseline.
 *
 */
public class SearchQueryTest extends BasicQueryTest {
    private static IndexTestSupport index;
    
    @BeforeClass
    public static void setup () throws Exception {
        index = new IndexTestSupport();
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        index.close();
    }
    
    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case SCENE: return "\"SCENE\"";
        default: throw new UnsupportedOperationException("No query string for " + q + " in " + getClass().getSimpleName());
        }
    }

    
    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_SCENE: return "<SpanNear ordered=\"true\" slop=\"1\">" +
        		"<SpanTerm>ACT</SpanTerm>" +
        		"<SpanTerm>SCENE</SpanTerm>" +
        		"</SpanNear>";
        case SCENE: return "SCENE";
        default: throw new UnsupportedOperationException("No query string for " + q + " in " + getClass().getSimpleName());
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer (XmlIndexer.INDEX_PATHS);
    }

    /**
     * @param xpath the path to test
     * @param optimized ignored
     * @param facts ignored
     * @param queries ignored
     */
    public void assertQuery (String xpath, String optimized, int facts, ValueType type, Q ... queries) {
        Saxon saxon = index.getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        optimized = saxonExpr.toString();
        long t = System.currentTimeMillis();
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        if (results.getException() != null) {
            throw new LuxException(results.getException());
        }
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " results from " +
        saxon.getQueryStats().docCount + " documents");
        AbstractExpression aex = saxonExpr.getXPath();
        aex = new UnOptimizer(getIndexer().getOptions()).unoptimize(aex);
        // TODO: don't re-optimize here !!
        SaxonExpr baseline = saxon.compile(aex.toString());
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch for: " + optimized, baseResult.size(), results.size());        
        Iterator<?> baseIter = baseResult.iterator();
        Iterator<?> resultIter = results.iterator();
        for (int i = 0 ; i < results.size(); i++) {
            XdmItem base = (XdmItem) baseIter.next();
            XdmItem r = (XdmItem) resultIter.next();
            assertEquals (base.isAtomicValue(), r.isAtomicValue());
            assertEquals (base.getStringValue(), r.getStringValue());
        }
        // TODO: also assert facts about query optimizations
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */