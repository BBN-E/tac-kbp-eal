package com.bbn.kbp.events2014;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
* Represents a TIMEX string in the KBP restricted format of YYYY-MM-DD where all parts must be
 * present but less specific missing parts may be replaced with Xes. For example,
 * 198X-XX-XX (the 1980s).
 *
*/
public final class KBPTIMEXExpression {
    private String year;
    private String month;
    private String day;

    public KBPTIMEXExpression(String year, String month, String day) {
        this.year = checkNotNull(year);
        this.month = checkNotNull(month);
        this.day = checkNotNull(day);
        checkArgument(!allXes(year), "KBP TIMEX year may not be all Xes: %s", year);
        checkArgument(digitsThenXes(year), "Invalid KBP TIMEX year: %s", year);
        checkArgument(year.length() == 4, "KBPTime year wrong size: %s", year);
        checkArgument(digitsThenXes(month), "Invalid KBP TIMEX month: %s", month);
        checkArgument(month.length() == 2, "KBPTime month wrong size: %s", month);
        checkArgument(digitsThenXes(day), "Invalid KBP TIMEX day: %s", day);
        checkArgument(day.length() == 2, "KBPTime day wrong size: %s", day);
    }

    private boolean isX(char c) {
        return c == 'X' || c =='x';
    }

    /**
     * returns false for an empty string.
     * @param s
     * @return
     */
    private boolean allXes(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i=0; i<s.length(); ++i) {
            if (!isX(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks string is made up of digits and Xes, where
     * not digit is permitted to follow an X.
     * @param s
     * @return
     */
    private boolean digitsThenXes(String s) {
        if (s.isEmpty()) {
            return false;
        }

        boolean seenX = false;
        for (int i=0; i<s.length(); ++i) {
            final char c= s.charAt(i);
            if (isX(c)) {
                seenX = true;
            } else if (Character.isDigit(c)) {
                if (seenX) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return year +"-" + month + "-" + day;
    }

    public ImmutableSet<KBPTIMEXExpression> lessSpecificCompatibleTimes() {
        final ImmutableSet.Builder<KBPTIMEXExpression> ret = ImmutableSet.builder();

        final StringBuilder curDay = new StringBuilder(day);
        final StringBuilder curMonth = new StringBuilder(month);
        final StringBuilder curYear = new StringBuilder(year);

        for (int i=1; i>=0; --i) {
            if (!isX(curDay.charAt(i))) {
                curDay.setCharAt(i, 'X');
                ret.add(KBPTIMEXExpression.fromYMD(curYear.toString(),
                        curMonth.toString(), curDay.toString()));
            }
        }

        for (int i=1; i>=0; --i) {
            if (!isX(curMonth.charAt(i))) {
                curMonth.setCharAt(i, 'X');
                ret.add(KBPTIMEXExpression.fromYMD(curYear.toString(),
                        curMonth.toString(), curDay.toString()));
            }
        }

        // don't terminate at 0 because all Xes is an invalid year
        for (int i=3; i>=1; --i) {
            if (!isX(curYear.charAt(i))) {
                curYear.setCharAt(i, 'X');
                ret.add(KBPTIMEXExpression.fromYMD(curYear.toString(),
                        curMonth.toString(), curDay.toString()));
            }
        }

        return ret.build();
    }

    private static KBPTIMEXExpression fromYMD(String yyyy, String mm, String dd) {
        return new KBPTIMEXExpression(yyyy, mm, dd);
    }

    private static final Pattern stuffDashStuffDashStuff = Pattern.compile("(....)-(..)-(..)");
    public static KBPTIMEXExpression parseTIMEX(String s) {
        final Matcher m = stuffDashStuffDashStuff.matcher(s);
        if (m.matches()) {
            return new KBPTIMEXExpression(m.group(1), m.group(2), m.group(3));
        } else {
            throw new IllegalArgumentException(String.format(
                    "Cannot parse %s as a KBP Timex expresison", s));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(year, month, day);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final KBPTIMEXExpression other = (KBPTIMEXExpression) obj;
        return Objects.equal(this.year, other.year) && Objects.equal(this.month, other.month) && Objects.equal(this.day, other.day);
    }
}
