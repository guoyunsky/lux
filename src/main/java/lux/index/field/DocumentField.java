package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;

import org.apache.lucene.document.Field.Store;

/**
 * A stored field that is used to store the entire XML document.
 *
 */
public class DocumentField extends FieldDefinition {
    
    private static final DocumentField instance = new DocumentField();
    
    public static DocumentField getInstance() {
        return instance;
    }
    
    protected DocumentField () {
        super ("lux_xml", null, Store.YES, Type.BYTES, true);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        byte[] bytes = indexer.getDocumentBytes();
        if (bytes != null) {
            return Collections.singleton(bytes);
        }
        return Collections.singleton(indexer.getDocumentText());
    }

}
