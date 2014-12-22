/**
 * 
 */
package uk.bl.wa.annotation;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.SolrInputDocument;
import org.archive.wayback.util.url.AggressiveUrlCanonicalizer;
import org.jdom.JDOMException;

import uk.bl.wa.indexer.WARCIndexer;
import uk.bl.wa.solr.SolrFields;

/**
 * 
 * This is the core annotation class that applies the annotations to a
 * SolrInputDocument.
 * 
 * @author Roger Coram, Andrew Jackson
 * 
 */
public class Annotator {
	private static Log LOG = LogFactory.getLog( Annotator.class );
	
	private Annotations annotations;

	private AggressiveUrlCanonicalizer canon = new AggressiveUrlCanonicalizer();
	

	/**
	 * Factory method to pull annotations from ACT.
	 * 
	 * @throws IOException
	 * @throws JDOMException
	 */
	public static Annotator annotationsFromAct() throws IOException,
			JDOMException {
		AnnotationsFromAct act = new AnnotationsFromAct();
		return new Annotator(act.getAnnotations());
	}

	/**
	 * Allow annotations to be defined outside of ACT.
	 * 
	 * @param annotations
	 */
	public Annotator(Annotations annotations) {
		this.annotations = annotations;
	}

	/**
	 * Runs through the 3 possible scopes, determining the appropriate part
	 * of the URI to match.
	 * 
	 * @param uri
	 * @param solr
	 * @throws URISyntaxException
	 * @throws URIException
	 */
	public void applyAnnotations(URI uri, SolrInputDocument solr)
			throws URISyntaxException, URIException {
		// "Just this URL".
		if (this.annotations.getCollections().get("resource").keySet()
				.contains(canon.urlStringToKey(uri.toString()))) {
			updateCollections(this.annotations.getCollections().get("resource")
					.get(uri.toString()), solr);
		}
		// "All URLs that start like this".
		String prefix = uri.getScheme() + "://" + uri.getHost();
		if (this.annotations.getCollections().get("root").keySet()
				.contains(prefix)) {
			updateCollections(this.annotations.getCollections().get("root")
					.get(prefix), solr);
		}
		// "All URLs that match match this host or any subdomains".
		String host;
		String domain = uri.getHost().replaceAll( "^www\\.", "" );
		HashMap<String, UriCollection> subdomains = this.annotations
				.getCollections().get("subdomains");
		for( String key : subdomains.keySet() ) {
			LOG.info("key: " + key);
			host = new URI( key ).getHost();
			if( host.equals( domain ) || host.endsWith( "." + domain ) ) {
				updateCollections( subdomains.get( key ), solr );
			}
		}
	}

	/**
	 * Updates a given SolrRecord with collections details from a UriCollection.
	 * 
	 * @param collection
	 * @param solr
	 */
	private void updateCollections(UriCollection collection,
			SolrInputDocument solr) {
		// Trac #2243; This should only happen if the record's timestamp is
		// within the range set by the Collection.
		String dateString = (String) solr.getField(SolrFields.CRAWL_DATE)
				.getValue();
		Date date;
		try {
			date = WARCIndexer.formatter.parse(dateString);
		} catch (ParseException e) {
			LOG.error("Could not parse date: " + dateString);
			return;
		}

		LOG.info( "Updating collections for " + solr.getField( SolrFields.SOLR_URL ) );
		// Update the single, main collection
		if (collection.collection != null && collection.collection.length() > 0) {
			if (this.annotations.getCollectionDateRanges().containsKey(
					collection.collection)
					&& this.annotations.getCollectionDateRanges()
							.get(collection.collection)
							.isInDateRange(date)) {
				setUpdateField(solr, SolrFields.SOLR_COLLECTION,
						collection.collection);
				LOG.info("Added collection " + collection.collection + " to "
						+ solr.getField(SolrFields.SOLR_URL));
			}
		}
		// Iterate over the hierarchical collections
		if (collection.collections != null
				&& collection.collections.length > 0) {
			for (String col : collection.collections) {
				if (this.annotations.getCollectionDateRanges().containsKey(col)
						&& this.annotations.getCollectionDateRanges().get(col)
								.isInDateRange(date)) {
					setUpdateField(solr, SolrFields.SOLR_COLLECTIONS, col);
					LOG.info( "Added collection '" + col + "' to " + solr.getField( SolrFields.SOLR_URL ) );
				}
			}
		}
		// Iterate over the subjects
		if( collection.subject != null && collection.subject.length > 0 ) {
			for( String subject : collection.subject ) {
				if (this.annotations.getCollectionDateRanges().containsKey(
						subject)
						&& this.annotations.getCollectionDateRanges()
								.get(subject).isInDateRange(date)) {
					setUpdateField(solr, SolrFields.SOLR_SUBJECT, subject);
					LOG.info( "Added collection '" + subject + "' to " + solr.getField( SolrFields.SOLR_URL ) );
				}
			}
		}
	}


	private static void setUpdateField(SolrInputDocument doc, String field,
			String value) {
		Map<String, String> operation = new HashMap<String, String>();
		operation.put("set", value);
		doc.addField(field, operation);
	}

	/**
	 * Pretty-print each solrDocument in the results to stdout
	 * 
	 * @param out
	 * @param doc
	 */
	private static void prettyPrint(PrintStream out, SolrInputDocument doc) {
		List<String> sortedFieldNames = new ArrayList<String>(
				doc.getFieldNames());
		Collections.sort(sortedFieldNames);
		out.println();
		for (String field : sortedFieldNames) {
			out.println(String.format("\t%s: %s", field,
					doc.getFieldValue(field)));
		}
		out.println();
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws JDOMException 
	 * @throws URISyntaxException 
	 */
	public static void main(String[] args) throws IOException, JDOMException, URISyntaxException {
		
		Annotator ae = Annotator.annotationsFromAct();
		
		URI uri = URI.create("http://news.bbc.co.uk/");
		SolrInputDocument solr = new SolrInputDocument();
		// Needs ID CrawlDate
		// SolrFields.CRAWL_DATE;
		// SolrFields.ID;

		// Uses SOLR_URL for logging only:
		// SolrFields.SOLR_URL;

		ae.applyAnnotations(uri, solr);
		prettyPrint(System.out, solr);
		
		// Loop over URL known to ACT:

		// Search for all matching URLs in SOLR:

		// Update all of those records with the applicable categories etc.
		
	}

}
