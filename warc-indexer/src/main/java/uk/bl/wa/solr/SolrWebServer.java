package uk.bl.wa.solr;

/*
 * #%L
 * warc-indexer
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2013 - 2020 The webarchive-discovery project contributors
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import com.typesafe.config.Config;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Solr Server Wrapper
 * 
 * @author anj
 */
public class SolrWebServer {
    private static Log log = LogFactory.getLog(SolrWebServer.class);

    @Command(name = "solr-options", description = "Setting up the Solr connection.")
    public
    static class SolrOptions {
        // Must specify either one or more Solr endpoints, or Zookeeper hosts
        // and a Solr collection
        @ArgGroup(exclusive = true, multiplicity = "1")
        SolrServiceOptions service;

        public
        static class SolrServiceOptions {
            @Option(names = { "-S",
                    "--solr-endpoint" }, description = "The HTTP Solr endpoints to use. Set this multiple times to use a load-balanced group of endpoints.")
            String[] endpoint;

            @ArgGroup(exclusive = false)
            ZKCollection zk;
        }

        public
        static class ZKCollection {
            @Option(names = { "-Z",
                    "--solr-zookeepers" }, description = "A list of comma-separated HOST:PORT values for the Zookeepers. e.g. 'zk1:2121,zk2:2121'")
            String zookeepers;

            @Option(names = { "-C",
                    "--solr-collection" }, description = "The Solr collection to populate.")
            String collection;
        }

        @Option(names = "--solr-batch-size", description = "Size of batches of documents to send to Solr.", defaultValue = "100")
        public int batchSize;

    }

    public static void main(String[] args) {
        SolrOptions cmd = new SolrOptions();
        new CommandLine(cmd).usage(System.out);
    }

    private SolrClient solrServer;
    
    /**
     * Initializes the Solr connection
     */
    public SolrWebServer(SolrOptions opts) {
        // Setup based on options:
        try {
            if (opts.service.endpoint != null) {
                if (opts.service.endpoint.length == 1) {
                    log.info("Setting up HttpSolrServer client from a url: "
                            + opts.service.endpoint[0]);
                    solrServer = new HttpSolrClient(opts.service.endpoint[0]);
                } else {
                    log.info(
                            "Setting up LBHttpSolrServer client from servers list: "
                                    + opts.service.endpoint);
                    solrServer = new LBHttpSolrClient(opts.service.endpoint);
                }
            } else {
                log.info("Setting up CloudSolrServer client via zookeepers.");
                solrServer = new CloudSolrClient(opts.service.zk.zookeepers);
                ((CloudSolrClient) solrServer)
                        .setDefaultCollection(opts.service.zk.collection);
            }
        } catch (MalformedURLException e) {
            log.error("WARCIndexerReducer.configure(): " + e.getMessage());
        }

        if (solrServer == null) {
            throw new RuntimeException("Cannot connect to Solr Server!");
        }
    }

    public static final String CONF_ZOOKEEPERS = "warc.solr.zookeepers";

    public static final String CONF_HTTP_SERVERS = "warc.solr.servers";

    public static final String CONF_HTTP_SERVER = "warc.solr.server";

    public static final String COLLECTION = "warc.solr.collection";

    public SolrWebServer(Config conf) {

        try {
            if( conf.hasPath(CONF_HTTP_SERVER)) {
                log.info("Setting up HttpSolrServer client from a url: "+conf.getString(CONF_HTTP_SERVER));
                solrServer = new HttpSolrClient(
                        conf.getString(CONF_HTTP_SERVER));
                
            } else if (conf.hasPath(CONF_ZOOKEEPERS)) {
                log.info("Setting up CloudSolrServer client via zookeepers.");
                solrServer = new CloudSolrClient(
                        conf.getString(CONF_ZOOKEEPERS));
                ((CloudSolrClient) solrServer)
                        .setDefaultCollection(conf
                        .getString(COLLECTION));
                
            } else if (conf.hasPath(CONF_HTTP_SERVERS)) {
                log.info("Setting up LBHttpSolrServer client from servers list.");
                solrServer = new LBHttpSolrClient(
                        conf.getString(
                        CONF_HTTP_SERVERS).split(","));
                
            } else {
                log.error("No valid SOLR config found.");
            }
        } catch (MalformedURLException e) {
            log.error("WARCIndexerReducer.configure(): " + e.getMessage());
        }

        if (solrServer == null) {
            System.out.println("Cannot connect to Solr Server!");
        }
    }

    public SolrClient getSolrServer() {
        return this.solrServer;
    }

    /**
     * Post a List of docs.
     * 
     * @param solrDoc
     * @return 
     * @throws SolrServerException
     * @throws IOException
     */
    public  UpdateResponse add(List<SolrInputDocument> docs)
            throws SolrServerException, IOException {
        /*
         * for (SolrInputDocument doc : docs) { log.info("DOC:" +
         * doc.toString()); solrServer.add(doc); } return null;
         */
        return solrServer.add(docs);
    }

    /**
     * Post a single documents.
     * 
     * @param solrDoc
     * @throws SolrServerException
     * @throws IOException
     */
    public void updateSolrDoc(SolrInputDocument doc)
            throws SolrServerException, IOException {
        solrServer.add(doc);

    }

    /**
     * Commit the SolrServer.
     * 
     * @throws SolrServerException
     * @throws IOException
     */
    public void commit() throws SolrServerException, IOException {

        solrServer.commit();

    }

    /**
     * Sends the prepared query to solr and returns the result;
     * 
     * @param query
     * @return
     * @throws SolrServerException
     * @throws IOException
     */

    public QueryResponse query(SolrQuery query)
            throws SolrServerException, IOException {
        QueryResponse rsp = solrServer.query(query);
        return rsp;
    }

    /**
     * Overrides the generic destroy method. Closes all Solrj connections.
     */
    public void destroy() {
        solrServer = null;
    }
}
