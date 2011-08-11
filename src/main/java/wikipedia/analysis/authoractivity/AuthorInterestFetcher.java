package wikipedia.analysis.authoractivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.Const;
import util.DateListGenerator;
import util.MapSorter;
import wikipedia.analysis.drilldown.BasicSearch;
import wikipedia.analysis.drilldown.NumberOfRecentEditsFetcher;
import wikipedia.analysis.useractivity.PageRevisionFetcher;
import wikipedia.analysis.useractivity.Revisions;
import wikipedia.database.DBUtil;
import wikipedia.http.PageHistoryFetcher;
import wikipedia.http.PageLinkInfoFetcher;
import wikipedia.http.WikiAPIClient;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


public final class AuthorInterestFetcher {

    private static final int MAX_SEARCHRESULTS = 12;

    private static final Logger LOG = LoggerFactory.getLogger(AuthorInterestFetcher.class.getName());

    private static final String INTERVAL_CONFIG_KEY = "INTERVAL";
    private static final String INTERVAL_CONFIG_MONTHS = "MONTHS";
    private static final String INTERVAL_CONFIG_DEFAULT = INTERVAL_CONFIG_MONTHS;

    private static final String NUMBER_OF_REVISIONS_KEY = "NUMBER_OF_REVISIONS";
    private static final String NUMBER_OF_REVISIONS_DEFAULT = "1";

    private static final String START_DATE_KEY = "START_DATE";
    private static final String START_DATE_DEFAULT = "2011, 7, 1";

    private static final String SEARCH_TERM_KEY = "SEARCH_TERM";
    private static final String SEARCH_TERM_DEFAULT = "MIT Center for Collective Intelligence";

    private static final String WIKIPEDIA_LANG_KEY = "WIKIPEDIA_LANG";
    private static final String WIKIPEDIA_LANG_DEFAULT = "en";

    private static final int NBR_WEEKS = 4;

    private final String lang;
    private final String searchTerm;
    private final DBUtil database = new DBUtil();

    private final WikiAPIClient wikiAPIClient;

    private final List<DateTime> allTimeFrames;
    private final DateTime mostRecentDate;

    /**
     *
     * @param searchTerm
     * @param lang
     * @param allTimeFrames length >= 1
     */
    public AuthorInterestFetcher(final String searchTerm,
                                 final String lang,
                                 final List<DateTime> allTimeFrames) {
        this.searchTerm = searchTerm;
        this.lang = lang;
        wikiAPIClient = new WikiAPIClient(new DefaultHttpClient());
        this.allTimeFrames = allTimeFrames;
        mostRecentDate = allTimeFrames.get(allTimeFrames.size() - 1);
    }

    private Map<String, Integer> getActivityMap(final int topResults) {
        final Collection<String> searchResults = new BasicSearch(lang, searchTerm).executeSearch();
        Map<String, Integer> activityResults = Maps.newHashMap();
        final NumberOfRecentEditsFetcher numberOfRecentEditsFetcher = new NumberOfRecentEditsFetcher(lang);
        for (String pageTitle : searchResults) {
            int nbrEdits = numberOfRecentEditsFetcher.getNumberOfEditsInLastWeeks(NBR_WEEKS, pageTitle);
            activityResults.put(pageTitle, nbrEdits);
        }
        return getTopEntries(topResults, activityResults);
    }

    public static void main(final String[] args) throws IOException {
        String configFileName = readConfigFileName(args);
        Properties configFile = new Properties();

        if (!StringUtils.isEmpty(configFileName)) {
            try {
                FileInputStream openInputStream = FileUtils.openInputStream(new File(configFileName));
                configFile.load(openInputStream);
            } catch (Exception e) {
                System.out.println("Usage: ProgramName ConfigfileName");
                e.printStackTrace();
            }
        }

        if (!configFile.isEmpty()) {
            AuthorInterestFetcher fetcher = null;
            try {
                fetcher = getSettingsAndPrepareFetcher(configFile);
            } catch (Exception e) {
                System.out.println("Could not load all required settings from config. Exiting.");
                e.printStackTrace();
            }
            if (fetcher != null) {
                fetcher.buildCompleteGraph();
            }
        } else {
            System.out.println("Could not load all required settings from config. Exiting.");
        }
    }

    private static AuthorInterestFetcher getSettingsAndPrepareFetcher(final Properties configFile) {
        final int numberOfRevisionsBack = Integer.valueOf(
                configFile.getProperty(NUMBER_OF_REVISIONS_KEY, NUMBER_OF_REVISIONS_DEFAULT));
        final String dateString = configFile.getProperty(START_DATE_KEY, START_DATE_DEFAULT);
        String[] dateParts = StringUtils.split(dateString, ",");
        final Integer year = Integer.valueOf(dateParts[0].trim());
        final Integer month = Integer.valueOf(dateParts[1].trim());
        final Integer day = Integer.valueOf(dateParts[2].trim());
        DateTime startDate = new DateMidnight(year, month, day).toDateTime();
        final String searchTerm = configFile.getProperty(SEARCH_TERM_KEY, SEARCH_TERM_DEFAULT);
        final String wikiLang = configFile.getProperty(WIKIPEDIA_LANG_KEY, WIKIPEDIA_LANG_DEFAULT);
        List<DateTime> allDates = prepareDateList(
                configFile.getProperty(INTERVAL_CONFIG_KEY, INTERVAL_CONFIG_DEFAULT), startDate,
                numberOfRevisionsBack);
        return new AuthorInterestFetcher(searchTerm, wikiLang, allDates);
    }

    private static List<DateTime> prepareDateList(final String intervalConfig,
                                                  final DateTime startDate,
                                                  final int numberOfRevisions) {
        if (intervalConfig.equals(INTERVAL_CONFIG_MONTHS)) {
            return DateListGenerator.getMonthGenerator().getDateList(numberOfRevisions, startDate);
        } else {
            return DateListGenerator.getWeekGenerator().getDateList(numberOfRevisions, startDate);
        }
    }

    private static String readConfigFileName(final String[] args) {
        if (args.length > 0) {
            return args[0];
        }
        return "";
    }

    public void buildCompleteGraph() throws IOException {
        Set<String> allSeenNodes = Sets.newHashSet();
        Map<String, Integer> initialSearchResults = getActivityMap(MAX_SEARCHRESULTS);
        allSeenNodes.addAll(initialSearchResults.keySet());

        for (String topEntry : initialSearchResults.keySet()) {
            PageLinkInfoFetcher plif = new PageLinkInfoFetcher(topEntry, lang, mostRecentDate, wikiAPIClient);
            allSeenNodes.addAll(plif.getLinkInformation().getFilteredLinks());
        }

        System.out.println("start author search");
        Set<String> allInvolvedAuthors = getAllInvolvedAuthors(initialSearchResults.keySet());
        //Set<String> allInvolvedAuthors = Sets.newHashSet(FileUtils.readLines(new File("out/dsk_net_authors.txt")));
        //allInvolvedAuthors = Sets.newHashSet(Lists.newArrayList(allInvolvedAuthors).subList(0, 400));
        System.out.println("end author search");

        //FileUtils.writeLines(new File("out/dsk_net_authors.txt"), allInvolvedAuthors);
        FileUtils.readLines(new File("out/dsk_net_authors.txt"));
        //LOG.info("Downloading information for " + allSeenNodes.size() + " pages");
        //Map<Integer, String> allPages = prepareNodesForNetwork(allSeenNodes);

        //DeltaPrinter dp = new DeltaPrinter(allInvolvedAuthors, allTimeFrames);
        //String completeJSONForPage = dp.buildNetworksAndGenerateInfo(searchTerm);
        //writeToFile(completeJSONForPage);

        new AuthorInterestNetworkBuilder(allInvolvedAuthors, searchTerm).getGraphAtDate(new DateMidnight(2011, 7, 1).toDateTime());
    }

    /**
     * Pick pages that the top 10 authors also worked on
     *
     * @param allSeenNodes
     * @return
     */
    private Set<String> getAllInvolvedAuthors(final Set<String> allSeenNodes) {
        Set<String> authors = Sets.newHashSet();
        for (String pageName : allSeenNodes) {
            Revisions articleRevisions = new PageRevisionFetcher(lang, pageName, 5000).getArticleRevisions();
            Map<String, Integer> editsPerPageAuthor = articleRevisions.getEditsPerAuthor();
            authors.addAll(editsPerPageAuthor.keySet());
        }
        return authors;
    }

    private void writeToFile(final String completeJSONForPage) {
        try {
            final String fileName = "out/" + StringUtils.replace(searchTerm, " ", "_") + ".json";
            FileUtils.write(new File(fileName), completeJSONForPage, Const.ENCODING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<Integer, String> prepareNodesForNetwork(final Set<String> allSeenNodes) {
        Map<Integer, String> idsAndPages = Maps.newHashMap();
        for (String pageTitle : allSeenNodes) {
            pageTitle = StringUtils.replace(pageTitle, "_", " ");
            try {
                int id = database.getPageIDFromCache(pageTitle, lang);
                if (id != 0) {
                    idsAndPages.put(id, pageTitle);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOG.info("Problem while getting: " + pageTitle);
            }
        }
        new PageHistoryFetcher(idsAndPages, "en", allTimeFrames).fetchCompleteCategories();
        return idsAndPages;
    }

    private Map<String, Integer> getTopEntries(final int nbrResults,
                                               final Map<String, Integer> results) {
        LinkedHashMap<String, Integer> sortetedResults = Maps
                .newLinkedHashMap(new MapSorter<String, Integer>().sortByValue(results));
        LinkedHashMap<String, Integer> filteredResults = Maps.newLinkedHashMap();
        List<String> sortedKeys = Lists.newArrayList(sortetedResults.keySet());
        for (int i = 0; i < nbrResults; i++) {
            final String currentKey = sortedKeys.get(i);
            filteredResults.put(currentKey, sortetedResults.get(currentKey));
        }
        return filteredResults;
    }
}
