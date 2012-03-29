package lux.saxon;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import lux.XPathQuery;
import lux.XPathCollector;
import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;
import lux.api.QueryStats;
import lux.xml.XmlBuilder;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XdmItem;

import org.apache.lucene.search.IndexSearcher;

/**
 * Implementation of a lux query/expression evaluator using Saxon
 *
 */
public class Saxon extends Evaluator  {
    
    // private ExpressionParser parser;
    private Processor processor;
    private XPathCompiler xpathCompiler;
    private SaxonBuilder saxonBuilder;
    private static Config config;
    
    public Saxon() {
        if (config == null) {
            config = new Config();
        }
        processor = new Processor (config);
        xpathCompiler = processor.newXPathCompiler();
        saxonBuilder = new SaxonBuilder();
    }

    @Override
    public SaxonExpr compile(String exprString) throws LuxException {
        XPathExecutable xpath;
        try {
           xpath = xpathCompiler.compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + exprString, e);
        }
        return new SaxonExpr(xpath, this);
    }

    @Override
    public Object evaluate(Expression expr) {
        return evaluate (expr, getContext().getContextItem());       
    }
    
    @Override
    public Object evaluate(Expression expr, Object contextItem) {
        return iterate (expr, contextItem);
    }

    @Override
    public Iterable<?> iterate(Expression expr, Object contextItem) { 
        SaxonExpr saxonExpr = (SaxonExpr) expr;
        if (contextItem == null) {
            return evaluateQuery(saxonExpr, getContext());
        }
        try {
            return saxonExpr.evaluate((XdmItem) contextItem);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }

    private List<Object> evaluateQuery(SaxonExpr saxonExpr, SaxonContext context) {
        // TODO: include a context query 
        // Query query = queryContext.getQuery();
        long t = System.nanoTime();
        IndexSearcher searcher = context.getSearcher();            
        XPathQuery xpq = saxonExpr.getXPathQuery();
        queryStats = new QueryStats();
        XPathCollector collector = new XPathCollector(this, saxonExpr, queryStats);
        try {
            searcher.search (xpq.getQuery(), collector);
        } catch (IOException e) {
            throw new LuxException("error searching for query: " + xpq, e);
        }
        queryStats.totalTime = System.nanoTime() - t;
        queryStats.docCount = collector.getDocCount();
        queryStats.query = xpq.getQuery().toString();
        return collector.getResults();
    }

    public SaxonBuilder getBuilder() {
        return saxonBuilder;
    }
    
    public SaxonContext getContext() {
        return (SaxonContext) super.getContext();
    }
    
    public Config getConfig() {
        return config;
    }
    
    public class SaxonBuilder extends XmlBuilder {
        private DocumentBuilder documentBuilder;

        SaxonBuilder () {
            documentBuilder = processor.newDocumentBuilder();
        }

        @Override
        public Object build(Reader reader) {
            try {
                return documentBuilder.build(new StreamSource (reader));
            } catch (SaxonApiException e) {
               throw new LuxException(e);
            }
        }
    }

}
