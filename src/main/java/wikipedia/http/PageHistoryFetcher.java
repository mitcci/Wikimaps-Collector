package wikipedia.http;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wikipedia.database.DBUtil;
import wikipedia.network.PageLinkInfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class PageHistoryFetcher {

    private final static Logger LOG = LoggerFactory.getLogger(PageHistoryFetcher.class.getName());

    private static final int MAX_YEARS = 7;
    private final List<DateTime> allRelevantTimeStamps;
    private final DBUtil dataBaseUtil;

    private static final int NUM_THREADS = 8;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public PageHistoryFetcher(final DBUtil dataBaseUtil) {
        this.dataBaseUtil = dataBaseUtil;
        DateMidnight startDate = new DateMidnight(2011, 6, 1);
        allRelevantTimeStamps = getAllDatesForHistory(startDate);
    }

    private List<DateTime> getAllDatesForHistory(final DateMidnight latestDate) {
        List<DateTime> allDatesToFetch = Lists.newArrayList();
        DateTime earliestDate = latestDate.minusYears(MAX_YEARS).toDateTime();
        while (earliestDate.isBefore(latestDate)) {
            allDatesToFetch.add(earliestDate);
            earliestDate = earliestDate.plusMonths(1);
        }
        return allDatesToFetch;
    }

    public void fetchAllRecords(final int pageId, final String pageTitle, final String lang) {
        //get oldest revision of article, if it didnt exist yet, do not execute http request!
        String storedCreationDate = dataBaseUtil.getFirstRevisionDate(pageId, lang);
        DateTime firstRevisionDate;
        if(StringUtils.isEmpty(storedCreationDate)) {
            firstRevisionDate = new FirstRevisionFetcher(pageTitle, lang).getFirstRevisionDate();
        } else {
            firstRevisionDate = DBUtil.MYSQL_DATETIME_FORMATTER.parseDateTime(StringUtils.removeEnd(storedCreationDate, ".0") );
        }

        for (DateTime dateToFetch : allRelevantTimeStamps) {
            if(dateToFetch.isAfter(firstRevisionDate.plusWeeks(1))) { //ignore first week of article, not stable yet
                PageLinkInfoFetcher plif = new PageLinkInfoFetcher(pageTitle, pageId, lang, dateToFetch, dataBaseUtil);
                if(plif.localDataUnavailable()) {
                    try {
                        Thread.sleep(1200); // with a poolsize of 8, this should lead to ~7 request pro second
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    PageLinkInfo linkInformation = plif.getLinkInformation();
                    dataBaseUtil.storePageLinkInfo(linkInformation, firstRevisionDate);
                }
            }
        }
    }

    private void shutdownThreadPool() {
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            LOG.error("Error while shutting down Threadpool", e);
        }
        while (!threadPool.isTerminated()) {
            // wait for all tasks or timeout
        }
    }

    public static void main(final String[] args) {
        final String lang = "en";
        long startTime = System.currentTimeMillis();
        fetchCompleteCategory(lang);
        //fetchSingleArticle("Aja Kim", lang);
        System.out.println(((System.currentTimeMillis() - startTime)/1000));
    }

    private static void fetchSingleArticle(final String pageName, final String lang) {
        final PageHistoryFetcher pageHistoryFetcher = new PageHistoryFetcher(new DBUtil());
        final Entry<Integer, String> pageEntry = new Entry<Integer, String>() {

            public String setValue(final String arg0) {
                return null;
            }

            public String getValue() {
                return pageName;
            }

            public Integer getKey() {
                return 12748191;
            }
        };
        try {
                pageHistoryFetcher.getThreadPool().execute(new ExecutorTask(pageHistoryFetcher, lang, pageEntry, 1));
        } finally  {
            pageHistoryFetcher.shutdownThreadPool();
        }
    }

    private static void fetchCompleteCategory(final String lang) {
        CategoryMemberFetcher cmf = new CategoryMemberFetcher(ImmutableList.of("Category:American_female_singers"), lang);
        final Map<Integer, String> allPagesInAllCategories = cmf.getAllPagesInAllCategories();
        LOG.info("Total Number of Tasks: " + allPagesInAllCategories.size());
        int counter = 1;
        final PageHistoryFetcher pageHistoryFetcher = new PageHistoryFetcher(new DBUtil());

        try {
            for (final Entry<Integer, String> pageEntry: allPagesInAllCategories.entrySet()) {
                pageHistoryFetcher.getThreadPool().execute(new ExecutorTask(pageHistoryFetcher, lang, pageEntry, counter++));
            }
        } finally  {
            pageHistoryFetcher.shutdownThreadPool();
        }
    }

    private static final class ExecutorTask implements Runnable {
        private final PageHistoryFetcher pageHistoryFetcher;
        private final String lang;
        private final Entry<Integer, String> pageEntry;
        private final int taskCounter;

        private ExecutorTask(final PageHistoryFetcher pageHistoryFetcher, final String lang,
                final Entry<Integer, String> pageEntry, final int taskCounter) {
            this.pageHistoryFetcher = pageHistoryFetcher;
            this.lang = lang;
            this.pageEntry = pageEntry;
            this.taskCounter = taskCounter;
        }

        public void run() {
            LOG.info("Starting Thread for Page: " + pageEntry.getValue() + " (Task Number: " + taskCounter + ")");
            pageHistoryFetcher.fetchAllRecords(pageEntry.getKey(), pageEntry.getValue(), lang);
        }
    }

}
