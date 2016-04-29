package com.bbn.kbp.events2014.validation;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import java.util.List;

public class LinkingValidators {

  public static LinkingValidator validatorFromMap(final Multimap<Symbol, Symbol> linkableTypes) {
    return MapLinkingValidator.create(linkableTypes);
  }

  public static LinkingValidator alwaysValidValidator() {
    return AlwaysValid.INSTANCE;
  }

  public static LinkingValidator banGeneric() {
    return BanGenericValidator.create();
  }

  public static LinkingValidator compose(LinkingValidator... validators) {
    return ValidatorComposer.create(ImmutableList.copyOf(validators));
  }
}

enum AlwaysValid implements LinkingValidator {

  INSTANCE;
  
  @Override
  public boolean validate(final ResponseLinking linking) {
    return true;
  }

  @Override
  public boolean validToLink(final Response a, final Response b) {
    return true;
  }
}

final class ValidatorComposer implements LinkingValidator {

  final ImmutableList<LinkingValidator> validators;

  private ValidatorComposer(final List<LinkingValidator> validators) {
    this.validators = ImmutableList.copyOf(validators);
  }

  static ValidatorComposer create(final List<LinkingValidator> validators) {
    return new ValidatorComposer(validators);
  }

  @Override
  public boolean validate(final ResponseLinking linking) {
    for (final LinkingValidator v : validators) {
      if (!v.validate(linking)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean validToLink(final Response a, final Response b) {
    for (final LinkingValidator v : validators) {
      if (!v.validToLink(a, b)) {
        return false;
      }
    }
    return true;
  }
}

final class BanGenericValidator implements LinkingValidator {

  private BanGenericValidator() {

  }

  public static BanGenericValidator create() {
    return new BanGenericValidator();
  }

  @Override
  public boolean validate(final ResponseLinking linking) {
    for (final ResponseSet rs : linking.responseSets()) {
      for (final Response r : rs) {
        if (r.realis().equals(KBPRealis.Generic)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public boolean validToLink(final Response a, final Response b) {
    return !a.realis().equals(KBPRealis.Generic) && !b.realis().equals(KBPRealis.Generic);
  }
}

final class MapLinkingValidator implements LinkingValidator {

  private final ImmutableMultimap<Symbol, Symbol> linkableTypes;

  private MapLinkingValidator(final Multimap<Symbol, Symbol> linkableTypes) {
    this.linkableTypes = ImmutableMultimap.copyOf(linkableTypes);
  }

  public static MapLinkingValidator create(final Multimap<Symbol, Symbol> linkableTypes) {
    return new MapLinkingValidator(linkableTypes);
  }

  @Override
  public boolean validate(final ResponseLinking linking) {
    for (final ResponseSet rs : linking.responseSets()) {
      for (final Response r : rs) {
        for (final Response s : rs) {
          if (!validToLink(r, s)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Override
  public boolean validToLink(final Response a, final Response b) {
    return linkableTypes.get(a.type()).contains(b.type()) && linkableTypes.get(b.type())
        .contains(a.type());
  }

}

