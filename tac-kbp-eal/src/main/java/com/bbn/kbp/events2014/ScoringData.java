package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class ScoringData {

  private final AnswerKey answerKey;
  private final ArgumentOutput argumentOutput;
  private final ResponseLinking systemLinking;
  private final ResponseLinking referenceLinking;

  protected ScoringData(ArgumentOutput argumentOutput, AnswerKey answerKey,
      ResponseLinking systemLinking, ResponseLinking referenceLinking) {
    this.answerKey = answerKey;
    this.argumentOutput = argumentOutput;
    // nullable
    this.systemLinking = systemLinking;
    // nullable
    this.referenceLinking = referenceLinking;
    checkDocIDsMatch();
  }

  public Optional<AnswerKey> answerKey() {
    return Optional.fromNullable(answerKey);
  }

  public Optional<ArgumentOutput> argumentOutput() {
    return Optional.fromNullable(argumentOutput);
  }

  public Optional<ResponseLinking> systemLinking() {
    return Optional.fromNullable(systemLinking);
  }

  public Optional<ResponseLinking> referenceLinking() {
    return Optional.fromNullable(referenceLinking);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder modifiedCopy() {
    Builder ret = new Builder();
    if (answerKey != null) {
      ret. withAnswerKey(answerKey);
    }
    if (argumentOutput != null) {
      ret.withArgumentOutput(argumentOutput);
    }
    if (systemLinking != null) {
      ret.withSystemLinking(systemLinking);
    }
    if (referenceLinking != null) {
      ret.withReferenceLinking(referenceLinking);
    }
    return ret;
  }

  public static final class Builder {
    private AnswerKey answerKey = null;
    private ArgumentOutput argumentOutput = null;
    private ResponseLinking systemLinking = null;
    private ResponseLinking referenceLinking = null;

    private Builder() {
    }

    public Builder withAnswerKey(AnswerKey answerKey) {
      this.answerKey = checkNotNull(answerKey);
      return this;
    }

    public Builder withArgumentOutput(ArgumentOutput argumentOutput) {
      this.argumentOutput = checkNotNull(argumentOutput);
      return this;
    }

    public Builder withSystemLinking(ResponseLinking systemLinking) {
      this.systemLinking = checkNotNull(systemLinking);
      return this;
    }

    public Builder withReferenceLinking(ResponseLinking referenceLinking) {
      this.referenceLinking = checkNotNull(referenceLinking);
      return this;
    }

    public ScoringData build() {
      return new ScoringData(argumentOutput, answerKey, systemLinking, referenceLinking);
    }
  }


  private void checkDocIDsMatch() {
    final Set<Symbol> docIDsPresent = Sets.newHashSet();
    if (answerKey != null) {
      docIDsPresent.add(answerKey.docId());
    }
    if (argumentOutput != null) {
      docIDsPresent.add(argumentOutput.docId());
    }
    if (systemLinking != null) {
      docIDsPresent.add(systemLinking.docID());
    }
    if (referenceLinking != null) {
      docIDsPresent.add(referenceLinking.docID());
    }
    checkArgument(docIDsPresent.size() < 2, "ScoringData has mixed docIDs: %s", docIDsPresent);
  }
}
