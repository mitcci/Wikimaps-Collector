package util;

import java.util.List;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Generate Lists of Dates based on a number of weeks or months starting
 * at the given date
 */
public final class DateListGenerator {

    private static final DateListGenerator MONTH_GEN = new DateListGenerator(new DateMinusCalc() {
        @Override
        public DateTime minusUnit(final DateTime startDate,
                                  final int units) {
            return startDate.minusMonths(units);
        }
    });

    private static final DateListGenerator WEEK_GEN = new DateListGenerator(new DateMinusCalc() {
        @Override
        public DateTime minusUnit(final DateTime startDate,
                                  final int units) {
            return startDate.minusWeeks(units);
        }
    });

    private static final Logger LOG = LoggerFactory.getLogger(DateListGenerator.class.getName());
    private final DateMinusCalc minusCalculator;

    private DateListGenerator(final DateMinusCalc minusCalculator) {
        this.minusCalculator = minusCalculator;
    }

    public static DateListGenerator getMonthGenerator() {
        return MONTH_GEN;
    }

    public static DateListGenerator getWeekGenerator() {
        return WEEK_GEN;
    }

    /**
     * @return List of Dates most recent to oldest
     */
    public List<DateTime> getDateList(final int numberOfRevisions,
                                      final DateTime startDate) {
        List<DateTime> allDatesToFetch = Lists.newArrayList();
        LOG.info("Fetching the following Dates: ");
        for (int units = 0; units < numberOfRevisions; units++) {
            final DateTime fetchDate = minusCalculator.minusUnit(startDate, units); // startDate.minusMonths(months);
            LOG.info(fetchDate.toString());
            allDatesToFetch.add(fetchDate);
        }
        return allDatesToFetch;
    }

    /**
     * interface for various date unit implemenations
     */
    protected interface DateMinusCalc {
        DateTime minusUnit(DateTime startDate, int units);
    }
}
