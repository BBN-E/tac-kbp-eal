package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The information needed to run the document-level scorer.
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@Value.Immutable
@Functional
@TextGroupPublicImmutable
abstract class _ScoringData {
  @Value.Parameter
  public abstract Optional<ArgumentOutput> argumentOutput();
  @Value.Parameter
  public abstract Optional<AnswerKey> answerKey();
  @Value.Parameter
  public abstract Optional<ResponseLinking> systemLinking();
  @Value.Parameter
  public abstract Optional<ResponseLinking> referenceLinking();

  @Value.Check
  protected void checkDocIDsMatch() {
    final Set<Symbol> docIDsPresent = Sets.newHashSet();
    if (answerKey().isPresent()) {
      docIDsPresent.add(answerKey().get().docId());
    }
    if (argumentOutput().isPresent()) {
      docIDsPresent.add(argumentOutput().get().docId());
    }
    if (systemLinking().isPresent()) {
      docIDsPresent.add(systemLinking().get().docID());
    }
    if (referenceLinking().isPresent()) {
      docIDsPresent.add(referenceLinking().get().docID());
    }
    checkArgument(docIDsPresent.size() < 2, "ScoringData has mixed docIDs: %s", docIDsPresent);
  }
}
