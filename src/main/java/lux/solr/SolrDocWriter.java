package lux.solr;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.DocWriter;
import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.UpdateHandler;

public class SolrDocWriter implements DocWriter {

    private final SolrCore core;
    private final XQueryComponent xqueryComponent;
    private final String uriFieldName;
    private final String xmlFieldName;

    SolrDocWriter(XQueryComponent xQueryComponent, SolrCore core) {
        this.core = core;
        this.xqueryComponent = xQueryComponent;
        IndexConfiguration indexConfig = xQueryComponent.getSolrIndexConfig().getIndexConfig();
        uriFieldName = indexConfig.getFieldName(FieldName.URI);
        xmlFieldName = indexConfig.getFieldName(FieldName.XML_STORE);
    }

    @Override
    public void write(NodeInfo node, String uri) {
        XmlIndexer indexer = null;
        try {
            indexer = xqueryComponent.checkoutXmlIndexer();
            try {
                indexer.index (node, uri);
            } catch (XMLStreamException e) {
                throw new LuxException(e);
            }
            UpdateDocCommand cmd = new UpdateDocCommand(core, indexer.createLuceneDocument(), uri);
            UpdateHandler updateHandler = core.getUpdateHandler();
            if (updateHandler.getUpdateLog() != null) {
                // Create a serialized version of the document for saving to the transaction log
                // FIXME: find a more efficient serialization scheme
                // at the very least use a Serializer to preserve white space etc
                // better still use TinyBinary (or PTree)
                // TODO: test replay from the log - how?
                String xml = node.toString();
                SolrInputDocument solrDoc = new SolrInputDocument();
                solrDoc.addField(uriFieldName, uri);
                solrDoc.addField(xmlFieldName, xml);
                cmd.solrDoc = solrDoc;
            }
            
            try {
                updateHandler.addDoc(cmd);
            } catch (IOException e) {
                throw new LuxException (e);
            }
        } finally {
            if (indexer != null) {
                xqueryComponent.returnXmlIndexer(indexer);
            }
        }
    }

    @Override
    public void delete(String uri) {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand(makeSolrQueryRequest());
        /*
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        */
        cmd.id = uri;
        try {
            core.getUpdateHandler().delete(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    @Override
    public void deleteAll() {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand( makeSolrQueryRequest());
        /*
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        */
        cmd.query = "*:*";
        try {
            core.getUpdateHandler().deleteByQuery(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    /**
     * @return
     */
    private SolrQueryRequestBase makeSolrQueryRequest() {
        return new SolrQueryRequestBase(core, new ModifiableSolrParams()) {};
    }

    @Override
    public void commit() {
        CommitUpdateCommand cmd = new CommitUpdateCommand(makeSolrQueryRequest(), false);
        cmd.expungeDeletes = false;
        // cmd.waitFlush = true;
        cmd.waitSearcher = true;
        try {
            core.getUpdateHandler().commit(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */