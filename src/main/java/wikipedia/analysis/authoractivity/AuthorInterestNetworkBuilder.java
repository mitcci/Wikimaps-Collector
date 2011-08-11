package wikipedia.analysis.authoractivity;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.implementations.DefaultGraph;
import org.graphstream.graph.implementations.MultiGraph;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.MapSorter;
import wikipedia.analysis.useractivity.UserContribFetcher;
import wikipedia.database.DBUtil;
import wikipedia.network.GraphEdge;
import wikipedia.network.TimeFrameGraph;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Builds a Network structure based on all pages in the given categories
 */
public final class AuthorInterestNetworkBuilder {

    private static final int MAX_NODES = 60;
    /**
     * Constants for Node-Filtering
     */
    private static final int INDEG_MULTIPLICATOR = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(AuthorInterestNetworkBuilder.class.getName());
    private static final int NUM_THREADS = 8;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);

    private final String searchTerm;
    private final Set<String> allInvolvedAuthors;

    public AuthorInterestNetworkBuilder(final Set<String> allInvolvedAuthors,
                          final String searchTerm) {
        this.allInvolvedAuthors = allInvolvedAuthors;
        this.searchTerm = searchTerm;
    }

    public TimeFrameGraph getGraphAtDate(final DateTime dateTime) {
        String revisionDateTime = dateTime.toString(DBUtil.MYSQL_DATETIME_FORMATTER);
        List<GraphEdge> allLinksInNetwork = buildAllLinksWithinNetwork(revisionDateTime);
        allLinksInNetwork = Lists.newArrayList(Sets.newHashSet(allLinksInNetwork)); //remove duplicates

//        try {
//            FileUtils.writeLines(new File("out/allLinksAutorsDsk.txt"), allLinksInNetwork);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        Map<String, List<String>> indegreeMatrix = initIndegreeMatrix(allLinksInNetwork);

        Map<String, Float> pageIndegMap = createIndegreeMap(allLinksInNetwork, indegreeMatrix);

        Set<String> mutuallyConnectedNeighbors = findMutuallyConnectedNeighbors(allLinksInNetwork);

        //DefaultGraph graph = prepareGraph(allLinksInNetwork);
        //Dijkstra shortestPath = prepareShortestPath(graph);

        final MapSorter<String, Float> mapSorter = new MapSorter<String, Float>();
        Map<String, Float> allPagesOrderedByIndeg = mapSorter.sortByValue(pageIndegMap);

        Map<String, Float> allMutuallyConnectdNeighborsByIndeg = mapSorter
                .sortByValue(generateIndegMapForMutuallyConnectedNeighbors(
                        mutuallyConnectedNeighbors, allPagesOrderedByIndeg));

        List<Entry<String, Float>> topIndeg = Lists.newArrayList(allMutuallyConnectdNeighborsByIndeg.entrySet()).subList(0, 100);
        try {
            FileUtils.writeLines(new File("out/topIndegDSK.txt"), topIndeg);
        } catch (IOException e) {
            e.printStackTrace();
        }

//        Map<String, Integer> allDirectNeighborsByShortestPath = new MapSorter<String, Integer>()
//                .sortByValue(generateSPMapForDirectNeighbors(allPagesOrderedByIndeg.keySet(),
//                                shortestPath, graph), true);
//
//        Map<String, Integer> nameIndexMap = addQualifiedNodesToMap(allPagesOrderedByIndeg,
//                allMutuallyConnectdNeighborsByIndeg, allDirectNeighborsByShortestPath);
//
//        List<GraphEdge> edgeOutput = prepareEdgeList(indegreeMatrix, nameIndexMap);

//        return new TimeFrameGraph(nameIndexMap, edgeOutput, dateTime);
        return null;
    }

    private Map<String, Integer> addQualifiedNodesToMap(final Map<String, Float> allPagesOrderedByIndeg,
                                                        final Map<String, Float> allMutuallyConnectdNeighborsByIndeg,
                                                        final Map<String, Integer> allDirectNeighborsByShortestPath) {
        Map<String, Integer> nameIndexMap = Maps.newLinkedHashMap();
        int nodeIndex = 0;
        int mutualLimit = 0;
        for (String pageName : allMutuallyConnectdNeighborsByIndeg.keySet()) {
            if (mutualLimit++ > 25) {
                break;
            }
            nameIndexMap.put(pageName, nodeIndex++);
        }

        int directLimit = 0;
        for (String pageName : allDirectNeighborsByShortestPath.keySet()) {
            if (directLimit > 20) {
                break;
            }
            if (!nameIndexMap.containsKey(pageName)) {
                nameIndexMap.put(pageName, nodeIndex++);
                directLimit++;
            }
        }

        //indeg, fill up
        for (Entry<String, Float> entry : allPagesOrderedByIndeg.entrySet()) {
            if (nodeIndex > MAX_NODES) {
                break;
            }
            final String pageName = entry.getKey();
            if (!nameIndexMap.containsKey(pageName)) {
                nameIndexMap.put(pageName, nodeIndex++);
            }
        }
        return nameIndexMap;
    }

    private Map<String, Float> createIndegreeMap(final List<GraphEdge> allLinksInNetwork,
                                                 final Map<String, List<String>> indegreeMatrix) {
        final int numberOfLinks = allLinksInNetwork.size();
        Map<String, Float> pageIndegMap = Maps.newHashMap();
        for (String targetPage : indegreeMatrix.keySet()) {
            final float indegValue = calculateIndegree(numberOfLinks, indegreeMatrix.get(targetPage));
            pageIndegMap.put(targetPage, indegValue);
        }
        return pageIndegMap;
    }

    private Set<String> findMutuallyConnectedNeighbors(final List<GraphEdge> allLinksInNetwork) {
        Set<String> mutuallyConnectedNeighbors = Sets.newHashSet();
        int numberOfTasks = 0;
        Set<GraphEdge> containsCheck = Sets.newHashSet(allLinksInNetwork);
        for (GraphEdge graphEdge : allLinksInNetwork) {
            if (containsCheck.contains(new GraphEdge(graphEdge.getTo(), graphEdge.getFrom()))) {
                if (graphEdge.getFrom().equals(searchTerm) || graphEdge.getTo().equals(searchTerm)) {
                    mutuallyConnectedNeighbors.add(graphEdge.getFrom());
                    mutuallyConnectedNeighbors.add(graphEdge.getTo());
                }
            }
            numberOfTasks++;
            if (numberOfTasks % 1000 == 0) {
                System.out.println(numberOfTasks);
            }
        }
        return mutuallyConnectedNeighbors;
    }

    private List<GraphEdge> prepareEdgeList(final Map<String, List<String>> indegreeMatrix,
                                            final Map<String, Integer> nameIndexMap) {
        List<GraphEdge> edgeOutput = Lists.newArrayList();
        for (Entry<String, List<String>> entry : indegreeMatrix.entrySet()) {
            String targetPageName = entry.getKey();
            List<String> incommingLinks = entry.getValue();
            for (String sourcePageName : incommingLinks) {
                if (sourcePageName.equals(targetPageName)) {
                    continue;
                }
                if (nameIndexMap.get(sourcePageName) != null && nameIndexMap.get(targetPageName) != null) {
                    edgeOutput.add(new GraphEdge(sourcePageName, targetPageName));
                }
            }
        }
        return edgeOutput;
    }

    private Map<String, Integer> generateSPMapForDirectNeighbors(final Set<String> allPages,
                                                                 final Dijkstra shortestPath,
                                                                 final DefaultGraph graph) {
        Map<String, Integer> neighbors = Maps.newHashMap();
        for (String pageName : allPages) {
            int shortestPathLength = (int) shortestPath.getShortestPathLength(graph.getNode(pageName));
            neighbors.put(pageName, shortestPathLength);
        }
        return neighbors;
    }

    private Map<String, Float> generateIndegMapForMutuallyConnectedNeighbors(
            final Set<String> mutuallyConnectedNeighbors,
            final Map<String, Float> allPagesOrderedByIndeg) {
        Map<String, Float> neighbors = Maps.newHashMap();
        for (String pageName : mutuallyConnectedNeighbors) {
            neighbors.put(pageName, allPagesOrderedByIndeg.get(pageName));
        }
        return neighbors;
    }

    private Dijkstra prepareShortestPath(final DefaultGraph graph) {
        Dijkstra shortestPath = new Dijkstra(Dijkstra.Element.edge, "weight", searchTerm);
        shortestPath.init(graph);
        shortestPath.compute();
        return shortestPath;
    }

    private DefaultGraph prepareGraph(final List<GraphEdge> allLinksInNetwork) {
        // create graph using gs
        DefaultGraph graph = new MultiGraph("graphForShortestPath", false, true);
        Random r = new Random();
        for (GraphEdge edge : allLinksInNetwork) {
            graph.addEdge(edge.getFrom() + edge.getTo() + r.nextDouble(), edge.getFrom(), edge.getTo(), true);
        }
        return graph;
    }

    private float calculateIndegree(final int numberOfLinks,
                                    final List<String> allIncommingLinks) {
        int totalNumberOfLinks = allIncommingLinks.size();
        return ((float) totalNumberOfLinks / (float) numberOfLinks) * INDEG_MULTIPLICATOR;
    }

    /**
     * @param allLinksInNetwork
     * @return In-degree matrix key:target_page value: all pages that point to target_page (key)
     */
    private Map<String, List<String>> initIndegreeMatrix(final List<GraphEdge> allLinksInNetwork) {
        Map<String, List<String>> indegreeMatrix = Maps.newHashMap();
        for (GraphEdge link : allLinksInNetwork) {
            String from = link.getFrom();
            String to = link.getTo();
            if (indegreeMatrix.containsKey(to)) {
                indegreeMatrix.get(to).add(from);
            } else {
                indegreeMatrix.put(to, Lists.newArrayList(from));
            }
        }
        return indegreeMatrix;
    }

    private List<GraphEdge> buildAllLinksWithinNetwork(final String revisionDateTime) {
        final List<GraphEdge> allLinksInNetwork = Collections.synchronizedList(Lists.<GraphEdge>newArrayList());
        LOG.info("Number of Tasks: " + allInvolvedAuthors.size());
        int taskCounter = 1;
        try {
            for (String authorName : allInvolvedAuthors) {
                threadPool.execute(new LinkCollector(authorName, allLinksInNetwork, taskCounter++, revisionDateTime));
            }
        } finally {
            shutdownThreadPool();
        }
        return allLinksInNetwork;
    }

    private void shutdownThreadPool() {
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            LOG.error("Error while shutting down Threadpool", e);
        }
        while (!threadPool.isTerminated()) {
            LOG.debug("Waiting for all Tasks to terminate");
        }
    }

    /**
     * Fetches Information from DB and calculates all incomming links to a given
     * page in the network
     */
    private final class LinkCollector implements Runnable {
        private static final int LOG_MODULO = 4000;
        private static final String LANG = "en"; //FIXME
        private final String authorName;
        private final List<GraphEdge> allLinksInNetwork;
        private final int counter;
        private final String revisionDateTime;

        private LinkCollector(final String authorName, final List<GraphEdge> allLinksInNetwork, final int counter,
                final String revisionDateTime) {
            this.authorName = authorName;
            this.allLinksInNetwork = allLinksInNetwork;
            this.counter = counter;
            this.revisionDateTime = revisionDateTime;
        }

        @Override
        public void run() {
            if (counter % LOG_MODULO == 0) {
                LOG.info("Task: " + counter);
            }
            UserContribFetcher fetcher = new UserContribFetcher(LANG, authorName);
            Set<String> mostEditedPages = fetcher.getMostEditedPages();
            Set<GraphEdge> allEdgesByUser = Sets.newHashSet();
            for (String pageNameSource : mostEditedPages) {
                for (String pageNameTarget : mostEditedPages) {
                    if (!StringUtils.equals(pageNameSource, pageNameTarget)) {
                        allEdgesByUser.add(new GraphEdge(pageNameSource, pageNameTarget));
                    }
                }
            }

            allLinksInNetwork.addAll(allEdgesByUser);
//            Collection<String> allOutgoingLinksOnPage = database.getAllLinksForRevision(pageId, revisionDateTime);
//            for (String outgoingLink : allOutgoingLinksOnPage) {
//                if (allPageNamesInNetwork.contains(outgoingLink)) {
//                    allLinksInNetwork.add(new GraphEdge(authorName, outgoingLink));
//                }
//            }
        }
    }

}
