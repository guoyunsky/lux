package lux;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

public class DirectDocWriter implements DocWriter {

    private final XmlIndexer indexer;
    private final IndexWriter indexWriter;
    private final String uriFieldName;
    
    public DirectDocWriter (XmlIndexer indexer, IndexWriter indexWriter) {
        this.indexer = indexer;
        this.indexWriter = indexWriter;
        uriFieldName = indexer.getConfiguration().getFieldName(IndexConfiguration.URI);
    }
    
    @Override
    public void write(NodeInfo node, String uri) {
        try {
            indexer.indexDocument(indexWriter, uri, node);
        } catch (XMLStreamException e) {
            throw new LuxException (e);
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }

    @Override
    public void delete(String uri) {
        Term term = new Term(uriFieldName, uri);
        try {
            indexWriter.deleteDocuments(term);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    @Override
    public void commit() {
        try {
            indexWriter.commit();
        }  catch (IOException e) {
            throw new LuxException (e);
        }
    }

}