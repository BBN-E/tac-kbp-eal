package com.bbn.kbp.events2014;

import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class KBPTIMEXExpressionTest {
    @Test
    public void testLessSpecific() {
        final KBPTIMEXExpression specific = KBPTIMEXExpression.parseTIMEX("1982-12-31");
        final Set<KBPTIMEXExpression> lessSpecific = specific.lessSpecificCompatibleTimes();
        final Set<String> reference = ImmutableSet.of("1982-12-3X", "1982-12-XX",
                "1982-1X-XX", "1982-XX-XX", "198X-XX-XX", "19XX-XX-XX", "1XXX-XX-XX");
        assertEquals(reference,
                FluentIterable.from(lessSpecific)
                        .transform(Functions.toStringFunction())
                        .toSet());
    }

    @Test
    public void testLessSpecific2() {
        final KBPTIMEXExpression specific = KBPTIMEXExpression.parseTIMEX("1982-1X-XX");
        final Set<KBPTIMEXExpression> lessSpecific = specific.lessSpecificCompatibleTimes();
        final Set<String> reference = ImmutableSet.of("1982-XX-XX", "198X-XX-XX", "19XX-XX-XX", "1XXX-XX-XX");
        assertEquals(reference,
                FluentIterable.from(lessSpecific)
                        .transform(Functions.toStringFunction())
                        .toSet());
    }
}
