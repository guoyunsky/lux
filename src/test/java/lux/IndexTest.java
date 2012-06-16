package lux;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlIndexer;
import lux.index.field.XmlField;
import lux.lucene.LuxSearcher;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.RAMDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * measures space and time for different indexing options
 * 
 * The timings are off; we'd need to run this repeatedly to avoid transient startup effects which
 * overwhelm the measurements for a single run.
 * 
 * But the space numbers (in bytes) should be valid (from Directory.sizeInBytes()):
 * XML storage: 3664896 (3.5M)
 * qnames = 3692544 - 3664896 = 27648 = 0.75%
 * paths = 3717120 - 3664896 = 52224 = 1.4%
 * 
 * After refactoring XmlField, etc:
 * XML storage: 2274304  why did this shrink so much?  We're now using serializer instead
 * of JDOM outputter - could this all be whitespace from indentation or something?
 * qnames: 2302976 - 2274304 = 28672 = 1.3%
 * paths: 2328576 - 2274304 = 54272 = 2.4%
 * path-values alone: 755712
 * path-values (w/docs): 2714624 - 2274304 = 19%
 * qname-values (as phrases): 2631680 - 2274304 = 357376 = 16%
 * qname-values (hashed into single tokens): 2542592 - 2274304 = 11.8%
 * qname-words w/o terminal tokens: 2683904 - 2274304 = 18%
 * qname-words + terminal tokens: 2786304 - 2274304 = 22%
 */
public class IndexTest {
    
    private RAMDirectory dir;

    @Test
    public void testIndexQNames() throws Exception {
        buildIndex ("qnames and xml", XmlIndexer.INDEX_QNAMES | XmlIndexer.STORE_XML | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }
    
    @Test
    public void testIndexQNamesOnly () throws Exception {
        buildIndex ("qnames", XmlIndexer.INDEX_QNAMES | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }

    @Test
    public void testIndexPaths() throws Exception {
        buildIndex ("paths and xml", XmlIndexer.INDEX_PATHS | XmlIndexer.STORE_XML | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }
    
    @Test
    public void testIndexPathsOnly () throws Exception {
        buildIndex ("paths", XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);        
        assertTotalDocs ();
    }
    
    @Test @Ignore
    public void testIndexPathValuesOneDoc() throws Exception {
        XmlIndexer indexer = new XmlIndexer (XmlIndexer.INDEX_PATHS | XmlIndexer.INDEX_VALUES);
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        indexer.indexDocument(indexWriter, "/lux/hamlet.xml", getClass().getClassLoader().getResourceAsStream("lux/hamlet.xml"));
        indexWriter.close();
        System.out.println 
             (String.format("indexed path-values for hamlet.xml in %d bytes", dir.sizeInBytes()));
        // hamlet.xml = 288815 bytes; indexed in 215040 bytes seems ok??
        printAllTerms();
    }

    @Test
    public void testIndexPathValuesOnly() throws Exception {
        buildIndex ("path-values", XmlIndexer.INDEX_PATHS | XmlIndexer.INDEX_VALUES | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }
    

    @Test
    public void testIndexQNameValues() throws Exception {
        buildIndex ("qname-values and docs", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_VALUES | XmlIndexer.STORE_XML | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }

    @Test
    public void testIndexQNameText() throws Exception {
        buildIndex ("qname-text and docs", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_FULLTEXT | XmlIndexer.STORE_XML | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }
    
    @Test
    public void testIndexQNameTextOnly() throws Exception {
        buildIndex ("qname-text", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_FULLTEXT | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
        printAllTerms();
    }

    @Test
    public void testIndexPathValues() throws Exception {
        buildIndex ("path-values and docs", XmlIndexer.INDEX_PATHS | XmlIndexer.INDEX_VALUES | XmlIndexer.STORE_XML | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }

    @Test
    public void testIndexQNamesAndPaths() throws Exception {
        buildIndex ("qnames and paths and docs", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS | XmlIndexer.STORE_XML | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
        buildIndex ("qnames and paths", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);
    }

    @Test
    public void testIndexQNamesAndPathsOnly() throws Exception {
        buildIndex ("qnames and paths", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }

    @Test
    public void testStoreDocuments() throws Exception {
        buildIndex ("xml storage", XmlIndexer.STORE_XML| XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }
    
    @Before
    public void setup() {
        dir = new RAMDirectory();
    }
    
    @After
    public void cleanup() {
        dir.close();
    }
    
    private XmlIndexer buildIndex (String desc, int options) throws XMLStreamException, IOException {
        XmlIndexer indexer = new XmlIndexer (options);
        long t0 = System.currentTimeMillis();
        SearchBase.indexAllElements (indexer, dir, "lux/hamlet.xml");
        System.out.println 
             (String.format("indexed %s in %d ms %d bytes", desc, 
                     (System.currentTimeMillis()-t0), dir.sizeInBytes()));
        return indexer;
    }

    private void printAllTerms() throws Exception {
        IndexReader reader = IndexReader.open(dir);
        TermEnum terms = reader.terms();
        System.out.println ("Printing all terms (except uri)");
        while (terms.next()) {
            if (terms.term().field().equals(XmlField.URI.getName())) {
                continue;
            }
            System.out.println (terms.term().toString() + ' ' + terms.docFreq());
        }
        reader.close();
    }
    
    private void assertTotalDocs() throws IOException {
        LuxSearcher searcher = new LuxSearcher(dir);
        DocIdSetIterator results = searcher.search(new MatchAllDocsQuery());
        int count = 0;
        while (results.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++count;
        }
        assertEquals (6636, count);
        /*
        */
        searcher.close();
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
