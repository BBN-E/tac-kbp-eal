package com.bbn.kbp.events;

import com.bbn.kbp.events2014.CASType;
import com.bbn.kbp.events2014.TACKBPEALException;

public enum ScoringEntityType {
  Name {
    @Override
    public boolean isEntityType() {
      return true;
    }
  }, Nominal {
    @Override
    public boolean isEntityType() {
      return true;
    }
  }, Pronoun {
    @Override
    public boolean isEntityType() {
      return true;
    }
  }, Filler {
    @Override
    public boolean isEntityType() {
      return false;
    }
  }, AlignmentFailure {
    @Override
    public boolean isEntityType() {
      return false;
    }
  }, Time {
    @Override
    public boolean isEntityType() {
      return false;
    }
  };

  public static ScoringEntityType fromCASType(CASType casType) {
    switch (casType) {
      case NAME:
        return ScoringEntityType.Name;
      case NOMINAL:
        return ScoringEntityType.Nominal;
      case PRONOUN:
        return ScoringEntityType.Pronoun;
      default:
        throw new TACKBPEALException("Unknown CAS type " + casType);
    }
  }

  public abstract boolean isEntityType();
}
