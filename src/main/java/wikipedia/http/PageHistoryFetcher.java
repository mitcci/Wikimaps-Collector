package wikipedia.http;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.DateListGenerator;
import wikipedia.analysis.pagenetwork.CategoryLists;
import wikipedia.database.DBUtil;
import wikipedia.network.PageLinkInfo;

/**
 * Downloads all link revisions of a given page
 */
public final class PageHistoryFetcher {

    //private static final int THREAD_SLEEP_MSEC = 1200;
    private static final int THREADPOOL_TERMINATION_WAIT_MINUTES = 1;
    private static final int NUM_THREADS = 8;

    private static final Logger LOG = LoggerFactory.getLogger(PageHistoryFetcher.class.getName());

    private final List<DateTime> allRelevantTimeStamps;
    private final DBUtil dataBaseUtil = new DBUtil();

    private final DefaultHttpClient httpClient = new DefaultHttpClient(
            new ThreadSafeClientConnManager());

    private final ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);
    private final Map<Integer, String> allPagesInAllCategories;
    private final String lang;

    public PageHistoryFetcher(final List<String> categories,
                              final String lang,
                              final List<DateTime> allRelevantTimeStamps) {
        this(new CategoryMemberFetcher(categories, lang, new DBUtil()).getAllPagesInAllCategories(),
                lang, allRelevantTimeStamps);
    }

    public PageHistoryFetcher(final Map<Integer, String> pages,
                              final String lang,
                              final List<DateTime> allRelevantTimeStamps) {
        this.lang = lang;
        this.allRelevantTimeStamps = allRelevantTimeStamps;
        allPagesInAllCategories = pages;
    }

    protected void fetchAllRecords(final int pageId,
                                   final String pageTitle) {
        // get oldest revision of article, if it didnt exist yet, do not execute
        // http request!
        String storedCreationDate = dataBaseUtil.getFirstRevisionDate(pageId);
        DateTime firstRevisionDate;
        if (StringUtils.isEmpty(storedCreationDate)) {
            final WikiAPIClient wikiAPIClient = new WikiAPIClient(httpClient);
            try {
                firstRevisionDate = new FirstRevisionFetcher(pageTitle, lang, wikiAPIClient)
                        .getFirstRevisionDate();
            } catch (Exception e) {
                LOG.error("Error while fetching first revision date for: " + pageTitle);
                return;
            }
        } else {
            firstRevisionDate = DBUtil.MYSQL_DATETIME_FORMATTER.parseDateTime(StringUtils.removeEnd(
                    storedCreationDate, ".0"));
        }

        final WikiAPIClient wikiAPIClient = new WikiAPIClient(httpClient);
        for (DateTime dateToFetch : allRelevantTimeStamps) {
            downloadLinkInfoIfNecessary(pageId, pageTitle, firstRevisionDate, wikiAPIClient,
                    dateToFetch);
        }
    }

    private void downloadLinkInfoIfNecessary(final int pageId,
                                             final String pageTitle,
                                             final DateTime firstRevisionDate,
                                             final WikiAPIClient wikiAPIClient,
                                             final DateTime dateToFetch) {
        if (dateToFetch.isAfter(firstRevisionDate.plusWeeks(1))) { // ignore
                                                                   // first week
                                                                   // of
                                                                   // article,
                                                                   // not stable
                                                                   // yet
            if (dataBaseUtil.localDataForRecordUnavailable(pageId, dateToFetch)) {
                /*try {
                    Thread.sleep(THREAD_SLEEP_MSEC); // with a poolsize of 8,
                                                     // this should lead to ~7
                                                     // request pro second
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
                PageLinkInfoFetcher plif = new PageLinkInfoFetcher(pageTitle, lang, dateToFetch, wikiAPIClient);
                PageLinkInfo linkInformation;
                try {
                    linkInformation = plif.getLinkInformation();
                    dataBaseUtil.storePageLinkInfo(linkInformation, firstRevisionDate);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void shutdownThreadPool() {
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(THREADPOOL_TERMINATION_WAIT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            LOG.error("Error while shutting down Threadpool", e);
        }
        while (!threadPool.isTerminated()) {
            LOG.debug("Waiting for Thread-Pool termination");
        }
    }

    /**
     *
     */
    public static void main(final String[] args) {
        final DateTime mostRecent = new DateMidnight(2011, 7, 1).toDateTime();
        List<DateTime> allDatesForHistory = DateListGenerator.getMonthGenerator().getDateList(2, mostRecent);
        new PageHistoryFetcher(CategoryLists.BORN_IN_THE_80IES, "en", allDatesForHistory).fetchCompleteCategories();
    }

    public void fetchCompleteCategories() {
        LOG.info("Total Number of Tasks: " + allPagesInAllCategories.size());
        int counter = 1;
        try {
            for (final Entry<Integer, String> pageEntry : allPagesInAllCategories.entrySet()) {
                threadPool.execute(new ExecutorTask(this, pageEntry, counter++));
            }
        } finally {
            shutdownThreadPool();
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * Runner for the download task of a pages records
     */
    private static final class ExecutorTask implements Runnable {
        private static final int MODULO_LOG = 100;
        private final PageHistoryFetcher pageHistoryFetcher;
        private final Entry<Integer, String> pageEntry;
        private final int taskCounter;

        private ExecutorTask(final PageHistoryFetcher pageHistoryFetcher,
                             final Entry<Integer, String> pageEntry,
                             final int taskCounter) {
            this.pageHistoryFetcher = pageHistoryFetcher;
            this.pageEntry = pageEntry;
            this.taskCounter = taskCounter;
        }

        @Override
        public void run() {
            if (taskCounter % MODULO_LOG == 0) {
                LOG.info("Starting Thread for Page: " + pageEntry.getValue() + " (Task Number: "
                        + taskCounter + ")");
            }
            pageHistoryFetcher.fetchAllRecords(pageEntry.getKey(), pageEntry.getValue());
        }
    }

}
