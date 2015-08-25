package com.bbn.kbp.events2014.scorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import java.util.Collection;

/**
 * This was generated via Immutables like the others, but their approach to
 * Jackson serialization didn't realy work
 */
@SuppressWarnings("all")
public final class ImmutableAggregate2015ScoringResult
    extends Aggregate2015ScoringResult {
  private final ImmutableAggregate2015ArgScoringResult argument;
  private final ImmutableAggregate2015LinkScoringResult linking;
  private final double overall;

  private ImmutableAggregate2015ScoringResult(
      ImmutableAggregate2015ArgScoringResult argument,
      ImmutableAggregate2015LinkScoringResult linking,
      double overall) {
    this.argument = argument;
    this.linking = linking;
    this.overall = overall;
  }

  /**
   * @return value of {@code argument} attribute
   */
  @JsonProperty("argument")
  @Override
  public ImmutableAggregate2015ArgScoringResult argument() {
    return argument;
  }

  /**
   * @return value of {@code linking} attribute
   */
  @Override
  @JsonProperty("linking")
  public ImmutableAggregate2015LinkScoringResult linking() {
    return linking;
  }

  /**
   * @return value of {@code overall} attribute
   */
  @Override
  @JsonProperty("overall")
  public double overall() {
    return overall;
  }

  /**
   * Copy current immutable object by setting value for {@link Aggregate2015ScoringResult#argument() argument}.
   * Shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value new value for argument
   * @return modified copy of the {@code this} object
   */
  public final ImmutableAggregate2015ScoringResult withArgument(ImmutableAggregate2015ArgScoringResult value) {
    if (this.argument == value) {
      return this;
    }
    ImmutableAggregate2015ArgScoringResult newValue = Preconditions.checkNotNull(value);
    return validate(new ImmutableAggregate2015ScoringResult(newValue, this.linking, this.overall));
  }

  /**
   * Copy current immutable object by setting value for {@link Aggregate2015ScoringResult#linking() linking}.
   * Shallow reference equality check is used to prevent copying of the same value by returning {@code this}.
   * @param value new value for linking
   * @return modified copy of the {@code this} object
   */
  public final ImmutableAggregate2015ScoringResult withLinking(ImmutableAggregate2015LinkScoringResult value) {
    if (this.linking == value) {
      return this;
    }
    ImmutableAggregate2015LinkScoringResult newValue = Preconditions.checkNotNull(value);
    return validate(new ImmutableAggregate2015ScoringResult(this.argument, newValue, this.overall));
  }

  /**
   * Copy current immutable object by setting value for {@link Aggregate2015ScoringResult#overall() overall}.
   * @param value new value for overall
   * @return modified copy of the {@code this} object
   */
  public final ImmutableAggregate2015ScoringResult withOverall(double value) {
    double newValue = value;
    return validate(new ImmutableAggregate2015ScoringResult(this.argument, this.linking, newValue));
  }

  /**
   * This instance is equal to instances of {@code ImmutableAggregate2015ScoringResult} with equal attribute values.
   * @return {@code true} if {@code this} is equal to {@code another} instance
   */
  @Override
  public boolean equals(Object another) {
    return this == another
        || (another instanceof ImmutableAggregate2015ScoringResult && equalTo((ImmutableAggregate2015ScoringResult) another));
  }

  private boolean equalTo(ImmutableAggregate2015ScoringResult another) {
    return argument.equals(another.argument)
        && linking.equals(another.linking)
        && Double.doubleToLongBits(overall) == Double.doubleToLongBits(another.overall);
  }

  /**
   * Computes hash code from attributes: {@code argument}, {@code linking}, {@code overall}.
   * @return hashCode value
   */
  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + argument.hashCode();
    h = h * 17 + linking.hashCode();
    h = h * 17 + Doubles.hashCode(overall);
    return h;
  }

  /**
   * Prints immutable value {@code Aggregate2015ScoringResult{...}} with attribute values,
   * excluding any non-generated and auxiliary attributes.
   * @return string representation of value
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper("Aggregate2015ScoringResult")
        .add("argument", argument)
        .add("linking", linking)
        .add("overall", overall)
        .toString();
  }

  /**
   * @param json JSON-bindable data structure
   * @return immutable value type
   * @deprecated Do not use this method directly, it exists only for <em>Jackson</em>-binding infrastructure
   */
  @Deprecated
  @JsonCreator
  static ImmutableAggregate2015ScoringResult fromJson(
      @JsonProperty("argument") ImmutableAggregate2015ArgScoringResult argument,
      @JsonProperty("linking") ImmutableAggregate2015LinkScoringResult linking,
      @JsonProperty("overall") double overall) {
    Builder builder = ImmutableAggregate2015ScoringResult.builder();
    return builder.argument(argument).linking(linking).overall(overall).build();
  }

  private static ImmutableAggregate2015ScoringResult validate(ImmutableAggregate2015ScoringResult instance) {
    instance.check();
    return instance;
  }

  /**
   * Creates immutable copy of {@link Aggregate2015ScoringResult}.
   * Uses accessors to get values to initialize immutable instance.
   * If an instance is already immutable, it is returned as is.
   * @param instance instance to copy
   * @return copied immutable Aggregate2015ScoringResult instance
   */
  public static ImmutableAggregate2015ScoringResult copyOf(Aggregate2015ScoringResult instance) {
    if (instance instanceof ImmutableAggregate2015ScoringResult) {
      return (ImmutableAggregate2015ScoringResult) instance;
    }
    return ImmutableAggregate2015ScoringResult.builder()
        .from(instance)
        .build();
  }

  /**
   * Creates builder for {@link com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ScoringResult ImmutableAggregate2015ScoringResult}.
   * @return new ImmutableAggregate2015ScoringResult builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builds instances of {@link com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ScoringResult ImmutableAggregate2015ScoringResult}.
   * Initialize attributes and then invoke {@link #build()} method to create
   * immutable instance.
   * <p><em>Builder is not thread safe and generally should not be stored in field or collection,
   * but used immediately to create instances.</em>
   */
  public static final class Builder {
    private static final long INITIALIZED_BITSET_ALL = 0x7;
    private static final long INITIALIZED_BIT_ARGUMENT = 0x1L;
    private static final long INITIALIZED_BIT_LINKING = 0x2L;
    private static final long INITIALIZED_BIT_OVERALL = 0x4L;
    private long initializedBitset;
    private ImmutableAggregate2015ArgScoringResult argument;
    private ImmutableAggregate2015LinkScoringResult linking;
    private double overall;

    private Builder() {}

    /**
     * Fill builder with attribute values from provided {@link Aggregate2015ScoringResult} instance.
     * Regular attribute values will be overridden, i.e. replaced with ones of an instance.
     * Instance's absent optional values will not be copied (will not override current).
     * Collection elements and entries will be added, not replaced.
     * @param instance instance to copy values from
     * @return {@code this} builder for chained invocation
     */
    public final Builder from(Aggregate2015ScoringResult instance) {
      Preconditions.checkNotNull(instance);
      argument(instance.argument());
      linking(instance.linking());
      overall(instance.overall());
      return this;
    }

    /**
     * Initializes value for {@link Aggregate2015ScoringResult#argument() argument}.
     * @param argument value for argument
     * @return {@code this} builder for chained invocation
     */
    public final Builder argument(ImmutableAggregate2015ArgScoringResult argument) {
      this.argument = Preconditions.checkNotNull(argument);
      initializedBitset |= INITIALIZED_BIT_ARGUMENT;
      return this;
    }

    /**
     * Initializes value for {@link Aggregate2015ScoringResult#linking() linking}.
     * @param linking value for linking
     * @return {@code this} builder for chained invocation
     */
    public final Builder linking(ImmutableAggregate2015LinkScoringResult linking) {
      this.linking = Preconditions.checkNotNull(linking);
      initializedBitset |= INITIALIZED_BIT_LINKING;
      return this;
    }

    /**
     * Initializes value for {@link Aggregate2015ScoringResult#overall() overall}.
     * @param overall value for overall
     * @return {@code this} builder for chained invocation
     */
    public final Builder overall(double overall) {
      this.overall = overall;
      initializedBitset |= INITIALIZED_BIT_OVERALL;
      return this;
    }

    /**
     * Builds new {@link com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ScoringResult ImmutableAggregate2015ScoringResult}.
     * @return immutable instance of Aggregate2015ScoringResult
     */
    public ImmutableAggregate2015ScoringResult build() {
      checkRequiredAttributes();
      return ImmutableAggregate2015ScoringResult.validate(new ImmutableAggregate2015ScoringResult(argument, linking, overall));
    }

    private boolean argumentIsSet() {
      return (initializedBitset & INITIALIZED_BIT_ARGUMENT) != 0;
    }

    private boolean linkingIsSet() {
      return (initializedBitset & INITIALIZED_BIT_LINKING) != 0;
    }

    private boolean overallIsSet() {
      return (initializedBitset & INITIALIZED_BIT_OVERALL) != 0;
    }

    private void checkRequiredAttributes() {
      if (initializedBitset != INITIALIZED_BITSET_ALL) {
        throw new IllegalStateException(formatRequiredAttributesMessage());
      }
    }

    private String formatRequiredAttributesMessage() {
      Collection<String> attributes = Lists.newArrayList();
      if (!argumentIsSet()) {
        attributes.add("argument");
      }
      if (!linkingIsSet()) {
        attributes.add("linking");
      }
      if (!overallIsSet()) {
        attributes.add("overall");
      }
      return "Cannot build Aggregate2015ScoringResult, some of required attributes are not set " + attributes;
    }
  }
}
