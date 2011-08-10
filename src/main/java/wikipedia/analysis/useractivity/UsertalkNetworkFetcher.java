package wikipedia.analysis.useractivity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.HTTPUtil;
import wikipedia.database.DBUtil;
import wikipedia.http.WikiAPIClient;
import wikipedia.network.GraphEdge;
import wikipedia.xml.Api;
import wikipedia.xml.XMLTransformer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Returns a list of all User-Talk edges for the given list
 * of wikipedia authors
 */
public final class UsertalkNetworkFetcher {

    private static final int NUM_THREADS = 16;
    private static final Logger LOG = LoggerFactory.getLogger(UsertalkNetworkFetcher.class.getName());

    private final String lang;
    private final List<String> sanitizedUserIDs;
    private final int numberOfUsersInNetwork;
    private final Map<GraphEdge, Integer> talkMatrix = Maps.newConcurrentMap();
    private final ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(NUM_THREADS);
    private final DBUtil database = new DBUtil();

    public UsertalkNetworkFetcher(final String lang, final List<String> userIDs) {
        this.lang = lang;
        sanitizedUserIDs = sanitizeUserIDs(userIDs);
        numberOfUsersInNetwork = sanitizedUserIDs.size();
    }

    public List<GraphEdge> getNetwork() {
        try {
            for (String userID1 : sanitizedUserIDs) {
                for (String userID2 : sanitizedUserIDs) {
                    if (StringUtils.equals(userID1, userID2)) {
                        continue;
                    }
                    newFixedThreadPool.execute(new GetUserTalk(userID1, userID2));
                }
            }
        } finally {
            shutdownThreadPool();
        }
        return collectUserTalkEdges();
    }

    private void shutdownThreadPool() {
        newFixedThreadPool.shutdown();
        try {
            newFixedThreadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            LOG.error("Error while shutting down Threadpool", e);
        }
        while (!newFixedThreadPool.isTerminated()) {
            LOG.debug("Waiting for ThreadPool termination");
        }
    }

    private List<GraphEdge> collectUserTalkEdges() {
        List<GraphEdge> userTalkEdges = Lists.newArrayList();
        for (int i = 0; i < numberOfUsersInNetwork; i++) {
            for (int j = i + 1; j < numberOfUsersInNetwork; j++) {
                final String from = sanitizedUserIDs.get(i);
                final String to = sanitizedUserIDs.get(j);
                final Integer direction1 = talkMatrix.get(new GraphEdge(from, to));
                final Integer direction2 = talkMatrix.get(new GraphEdge(to, from));
                int totalConversations = direction1 + direction2; // sum of talk
                                                                  // in both
                                                                  // directions
                if (totalConversations > 0) {
                    userTalkEdges.add(new GraphEdge(from, to));
                }
            }
        }
        return userTalkEdges;
    }

    private List<String> sanitizeUserIDs(final List<String> userIDs) {
        List<String> ids = Lists.newArrayList();
        for (String id : userIDs) {
            ids.add(id.replaceAll("_", "_")); // XXX SANI FAIL?
        }
        return ids;
    }

    private final class GetUserTalk implements Runnable {

        private final GraphEdge userCommunicationPair;

        public GetUserTalk(final String from, final String to) {
            userCommunicationPair = new GraphEdge(from, to);
        }

        @Override
        public void run() {
            int numberOfRevisions;
            if (database.userConversationInCache(userCommunicationPair)) {
                LOG.info("Pair in Cache: " + userCommunicationPair);
                numberOfRevisions = database.getUserConversationFromCache(userCommunicationPair);
            } else {
                LOG.info("Downloading Pair: " + userCommunicationPair);
                numberOfRevisions = downloadPairCommunication();
                database.cacheUserConversation(userCommunicationPair, numberOfRevisions);
            }
            talkMatrix.put(userCommunicationPair, numberOfRevisions);
        }

        private int downloadPairCommunication() {
            final WikiAPIClient wikiAPIClient = new WikiAPIClient(new DefaultHttpClient(), false);
            final String xml = getUserTalkContribs(wikiAPIClient);
            int numberOfRevisions = 0;
            if (xml.indexOf("<revisions>") > 0) {
                Api revisionFromXML;
                try {
                    revisionFromXML = XMLTransformer.getRevisionFromXML(xml);
                    numberOfRevisions = revisionFromXML.getQuery().getPages().get(0).getRevisions().size();
                } catch (Exception e) {
                    LOG.error("Exception while executing Fetch Thread", e);
                }
            }
            return numberOfRevisions;
        }

        private String getUserTalkContribs(final WikiAPIClient wikiAPIClient) {
            final String from = HTTPUtil.urlEncode(userCommunicationPair.getFrom());
            final String to = HTTPUtil.urlEncode(userCommunicationPair.getTo());
            String urlStr = "http://" + lang
                    + ".wikipedia.org/w/api.php?format=xml&action=query&prop=revisions&titles=User%20talk:"
                    + to + "&rvlimit=500&rvprop=flags%7Ctimestamp%7Cuser%7Csize&rvuser=" + from;
            return wikiAPIClient.executeHTTPRequest(urlStr);
        }
    }
}
