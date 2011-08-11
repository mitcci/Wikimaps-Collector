package wikipedia.analysis.useractivity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.MapSorter;
import wikipedia.http.WikiAPIClient;
import wikipedia.xml.Api;
import wikipedia.xml.Item;
import wikipedia.xml.XMLTransformer;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * Downloads all the contributions a user has made (on article pages)
 */
public final class UserContribFetcher {

    private static final int PAGE_SIZE = 500;

    private static final Logger LOG = LoggerFactory.getLogger(UserContribFetcher.class.getName());

    private static final int MAX_REVISIONS = 3000;

    private final DefaultHttpClient httpclient = new DefaultHttpClient();
    private final WikiAPIClient wikiAPIClient = new WikiAPIClient(httpclient);

    private final String username;
    private final String lang;
    private final int numberOfRevisions;

    public UserContribFetcher(final String lang, final String userName, final int numberOfRevisions) {
        this.lang = lang;
        this.numberOfRevisions = numberOfRevisions;
        this.username = userName.replaceAll(" ", "_");
    }

    public UserContribFetcher(final String lang, final String userName) {
        this(lang, userName, MAX_REVISIONS);
    }

    public Set<String> getMostEditedPages() {
        Set<String> relatedPages = Sets.newHashSet();
        try {
            addBestRelatedChangesToSet(relatedPages);
        } catch (Exception e) {
            LOG.error("Error while executing HTTP request", e);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
        return relatedPages;
    }

    private void addBestRelatedChangesToSet(final Set<String> relatedPages) throws Exception {
        Api revisionFromXML = null;
        String queryContinueID = "";
        int counter = 0;
        Multiset<String> editsPerPage = HashMultiset.create();
        while (counter < numberOfRevisions) {
            final String xml = getArticleRevisionsXML(queryContinueID);
            counter = counter + PAGE_SIZE;
            revisionFromXML = XMLTransformer.getRevisionFromXML(xml);
            List<Item> usercontribs = revisionFromXML.getQuery().getUsercontribs();
            for (Item item : usercontribs) {
                editsPerPage.add(item.getTitle());
            }
            if (revisionFromXML.isLastPageInRequestSeries()) {
                break;
            } else {
                queryContinueID = revisionFromXML.getQueryContinue().getUsercontribs().getUcstart();
            }
        }
        Map<String, Integer> editsPerPageResult = Maps.newHashMap();
        for (String pageName : editsPerPage) {
            editsPerPageResult.put(pageName, editsPerPage.count(pageName));
        }
        final Set<String> topPages = new MapSorter<String, Integer>().sortByValue(editsPerPageResult).keySet();
        if (topPages.size() < 100) {
            relatedPages.addAll(topPages);
        } else {
            relatedPages.addAll(Lists.newArrayList(topPages).subList(0, 100));
        }
    }

    private String getArticleRevisionsXML(final String nextId) {
        String rvstartid = "&ucstart=" + nextId;
        if (nextId.equals("")) {
            rvstartid = "";
        }

        String urlStr = "http://" + lang
                + ".wikipedia.org/w/api.php?format=xml&action=query&" +
                "list=usercontribs&ucnamespace=0&uclimit=" +
                PAGE_SIZE +
                "&ucprop=title&ucuser=" +
                username + rvstartid;
        LOG.info("Requesting URL: " + urlStr);
        return wikiAPIClient.executeHTTPRequest(urlStr);
    }

}
