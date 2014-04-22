package com.bbn.kbp.events2014.filters;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Created by rgabbard on 4/22/14.
 */
public class TestKeepBestJustificationOnly {

    @Test
    public void testKeepBestJustificationOnly() {
        final Symbol docid = Symbol.from("doc");
        final Symbol type1 = Symbol.from("Type1");
        final Symbol type2 = Symbol.from("Type2");
        final Symbol role1 = Symbol.from("Role1");
        final Symbol role2 = Symbol.from("Role2");
        final KBPRealis realis1 = KBPRealis.Actual;
        final KBPRealis realis2 = KBPRealis.Generic;
        final KBPString CAS1 = KBPString.from("CAS1", 0, 1);
        final KBPString CAS1_different_offsets = KBPString.from("CAS1", 0, 2);
        final KBPString CAS2 = KBPString.from("CAS2", 0, 1);
        final CharOffsetSpan baseFiller1 = CharOffsetSpan.fromOffsetsOnly(42,43);
        final CharOffsetSpan baseFiller2 = CharOffsetSpan.fromOffsetsOnly(52,53);
        final Set<CharOffsetSpan> argumentJustifications1 = ImmutableSet.of();
        final Set<CharOffsetSpan> argumentJustifications2 = ImmutableSet.of(
                CharOffsetSpan.fromOffsetsOnly(0, 100));

        final Set<CharOffsetSpan> predicateJustifications1 = ImmutableSet.of(
                CharOffsetSpan.fromOffsetsOnly(98, 99));
        final Set<CharOffsetSpan> predicateJustifications2 = ImmutableSet.of(
                CharOffsetSpan.fromOffsetsOnly(70, 80));

        final Scored<Response> best = Scored.from(
                Response.createFrom(docid, type1, role1, CAS1, baseFiller2,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        // this differs from best only in base filler. It ties on score, but should
        // lose out to best by the tiebreaker based on hash code (response ID)
        final Scored<Response> tiesBestButLosesTiebreakByHash = Scored.from(
                Response.createFrom(docid, type1, role1, CAS1, baseFiller1,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        // these will also lose to best due to having a lower score
        final Scored<Response> differentPJWithLowerScore = Scored.from(
                Response.createFrom(docid, type1, role1, CAS1, baseFiller1,
                        argumentJustifications1, predicateJustifications2, realis1), 0.8);

        final Scored<Response> differentBFWithLowerScore = Scored.from(
                Response.createFrom(docid, type1, role1, CAS1, baseFiller2,
                        argumentJustifications1, predicateJustifications1, realis1), 0.8);

        final Scored<Response> differentAJWithLowerScore = Scored.from(
                Response.createFrom(docid, type1, role1, CAS1, baseFiller1,
                        argumentJustifications2, predicateJustifications1, realis1), 0.8);

        // all of the below shouldn't be in competition with anything else
        // due to unique (docid, type, role, CAS, Realis) tuples
        final Scored<Response> differByType = Scored.from(
                Response.createFrom(docid, type2, role1, CAS1, baseFiller1,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        final Scored<Response> differByRole = Scored.from(
                Response.createFrom(docid, type2, role1, CAS1, baseFiller1,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        final Scored<Response> differByRealis = Scored.from(
                Response.createFrom(docid, type2, role1, CAS1, baseFiller1,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        final Scored<Response> differByCASOffsets = Scored.from(
                Response.createFrom(docid, type2, role1, CAS1_different_offsets, baseFiller1,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        final Scored<Response> differByCASString = Scored.from(
                Response.createFrom(docid, type2, role1, CAS2, baseFiller1,
                        argumentJustifications1, predicateJustifications1, realis1), 0.9);

        final SystemOutput toDeduplicate = SystemOutput.from(docid, ImmutableList.of(
                best, tiesBestButLosesTiebreakByHash, differentPJWithLowerScore,
                differentBFWithLowerScore, differentAJWithLowerScore, differByType,
                differByRole, differByRealis, differByCASOffsets, differByCASString));
        // reference drops two losers
        final SystemOutput reference = SystemOutput.from(docid, ImmutableList.of(
                best, differByType,
                differByRole, differByRealis, differByCASOffsets, differByCASString));

        final SystemOutput deduplicated = KeepBestJustificationOnly.create().apply(toDeduplicate);
        assertEquals(reference, deduplicated);
    }
}
