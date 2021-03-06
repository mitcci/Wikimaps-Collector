package wikipedia.http;

import org.joda.time.DateTime;

import util.HTTPUtil;
import wikipedia.xml.Api;
import wikipedia.xml.XMLTransformer;

/**
 * Downloads the date of the first (oldest) revision of a page
 */
public final class FirstRevisionFetcher {

    //private static final Logger LOG = LoggerFactory.getLogger(FirstRevisionFetcher.class.getName());

    private final String pageTitle;
    private final String lang;

    private final WikiAPIClient wikiAPIClient;

    public FirstRevisionFetcher(final String pageTitle, final String lang, final WikiAPIClient wikiAPIClient) {
        this.lang = lang;
        this.pageTitle = pageTitle;
        this.wikiAPIClient = wikiAPIClient;
    }

    public DateTime getFirstRevisionDate() {
        final String url = getURL();
        //LOG.info("Fetching URL: " + url);
        String xmlResponse = wikiAPIClient.executeHTTPRequest(url);
        //LOG.info(xmlResponse);
        Api revisionFromXML = XMLTransformer.getRevisionFromXML(xmlResponse);
        String timeStamp = revisionFromXML.getQuery().getPages().get(0).getRevisions().get(0).getTimestamp();
        return new DateTime(timeStamp);
    }

    private String getURL() {
        //LOG.info("Pagetitle was: " + pageTitle);
        final String encodedPageName = HTTPUtil.urlEncode(pageTitle);
        return "http://" + lang +
        ".wikipedia.org/w/api.php?format=xml&action=query&prop=revisions&titles=" + encodedPageName +
        "&rvlimit=1&rvprop=timestamp&rvdir=newer";
    }

}
