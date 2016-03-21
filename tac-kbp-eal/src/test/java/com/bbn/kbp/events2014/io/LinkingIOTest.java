package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligners;
import com.bbn.kbp.events2014.linking.LinkingUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinkingIOTest {

  private static final ImmutableSet<KBPRealis> RELEVANT_REALISES =
      ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other);

  private static final EventArgumentLinkingAligner linkingAligner =
      EventArgumentLinkingAligners.getExactMatchEventArgumentLinkingAligner();

  @Test
  public void testRoundtrip()
      throws IOException, EventArgumentLinkingAligner.InconsistentLinkingException {
    final File dir = new File(
        LinkingIOTest.class.getResource("/com/bbn/kbp/events2014/io/linkingTest").getFile());
    final AnnotationStore annotationStore =
        AssessmentSpecFormats.openAnnotationStore(dir, AssessmentSpecFormats.Format.KBP2015);
    final AnswerKey answerKey = annotationStore.read(Symbol.from("AFP_ENG_20091024.0206"));
    final AnswerKey onlyLinkableAnswerKey =
        answerKey.filter(LinkingUtils.linkableResponseFilter2015ForGold());

    final EventArgumentLinking original = EventArgumentLinking.createMinimalLinkingFrom(
        onlyLinkableAnswerKey);

    final File tmpDir = Files.createTempDir();
    tmpDir.deleteOnExit();
    final LinkingStore linkingStore = LinkingSpecFormats.openOrCreateLinkingStore(tmpDir);
    linkingStore.write(linkingAligner.alignToResponseLinking(original, onlyLinkableAnswerKey));

    final Optional<ResponseLinking> reloadedResponseLinking =
        linkingStore.read(onlyLinkableAnswerKey);
    assertTrue(reloadedResponseLinking.isPresent());
    final EventArgumentLinking reloadedEAL =
        linkingAligner.align(reloadedResponseLinking.get(), onlyLinkableAnswerKey);
    assertEquals(original, reloadedEAL);
  }
}
