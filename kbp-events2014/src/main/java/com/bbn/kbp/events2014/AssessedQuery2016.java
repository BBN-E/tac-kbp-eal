package com.bbn.kbp.events2014;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jdeyoung on 3/21/16.
 */
public class AssessedQuery2016 {

  private final Query2016 query;
  private final QueryAssessment assessment;

  private AssessedQuery2016(final Query2016 query, final QueryAssessment assessment) {
    this.query = checkNotNull(query);
    this.assessment = checkNotNull(assessment);
  }

  public static AssessedQuery2016 create(final Query2016 query,
      final QueryAssessment assessment) {
    return new AssessedQuery2016(query, assessment);
  }

  public Query2016 query() {
    return query;
  }

  public QueryAssessment assessment() {
    return assessment;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AssessedQuery2016 that = (AssessedQuery2016) o;
    return Objects.equals(query, that.query) &&
        assessment == that.assessment;
  }

  @Override
  public int hashCode() {
    return Objects.hash(query, assessment);
  }
}
