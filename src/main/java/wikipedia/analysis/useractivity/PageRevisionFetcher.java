package wikipedia.analysis.useractivity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.Const;
import wikipedia.http.WikiAPIClient;
import wikipedia.xml.Api;
import wikipedia.xml.Rev;
import wikipedia.xml.XMLTransformer;

/**
 * Downloads all revisions of a single article
 */
public final class PageRevisionFetcher {

    private static final int PAGE_SIZE = 500;

    private static final Logger LOG = LoggerFactory.getLogger(PageRevisionFetcher.class.getName());

    private static final int MAX_REVISIONS = 4000;

    private final DefaultHttpClient httpclient = new DefaultHttpClient();
    private final WikiAPIClient wikiAPIClient = new WikiAPIClient(httpclient);

    private final String pageTitle;
    private final String lang;
    private final int numberOfRevisions;

    public PageRevisionFetcher(final String lang, final String pageTitle, final int numberOfRevisions) {
        this.lang = lang;
        this.numberOfRevisions = numberOfRevisions;
        this.pageTitle = pageTitle.replaceAll(" ", "_");
    }

    public PageRevisionFetcher(final String lang, final String pageTitle) {
        this(lang, pageTitle, MAX_REVISIONS);
    }

    public Revisions getArticleRevisions() {
        Revisions revisionsResult = new Revisions(pageTitle);
        try {
            addAllRevisionsToList(revisionsResult);
        } catch (Exception e) {
            LOG.error("Error while executing HTTP request", e);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
        return revisionsResult;
    }

    private void addAllRevisionsToList(final Revisions revisionsResult) throws Exception {
        Api revisionFromXML = null;
        String queryContinueID = "";
        int counter = 0;
        while (counter < numberOfRevisions) {
            final String xml = getArticleRevisionsXML(queryContinueID);
            counter = counter + PAGE_SIZE;
            revisionFromXML = XMLTransformer.getRevisionFromXML(xml);
            for (Rev rev : revisionFromXML.getQuery().getPages().get(0).getRevisions()) {
                revisionsResult.addEditEntry(new Revision(rev.getUser(), rev.getTimestamp(), rev.getSize()));
            }
            if (revisionFromXML.isLastPageInRequestSeries()) {
                break;
            } else {
                queryContinueID = revisionFromXML.getQueryContinue().getRevisions().getRvstartid();
            }
        }
    }

    private String getArticleRevisionsXML(final String nextId) {
        String rvstartid = "&rvstartid=" + nextId;
        if (nextId.equals("")) {
            rvstartid = "";
        }
        String pageid = "";

        try {
            pageid = URLEncoder.encode(pageTitle, Const.ENCODING);
        } catch (UnsupportedEncodingException e) {
            LOG.error("Encoding failed!");
        }

        String urlStr = "http://" + lang
                + ".wikipedia.org/w/api.php?format=xml&action=query&prop=revisions&titles=" + pageid
                + "&rvlimit=" + PAGE_SIZE + "&rvprop=flags%7Ctimestamp%7Cuser%7Csize&rvdir=older" + rvstartid;
        LOG.info("Requesting URL: " + urlStr);
        return wikiAPIClient.executeHTTPRequest(urlStr);
    }

}
