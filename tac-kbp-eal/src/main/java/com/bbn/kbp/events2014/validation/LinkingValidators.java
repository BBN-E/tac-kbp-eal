package com.bbn.kbp.events2014.validation;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public class LinkingValidators {

  public static LinkingValidator validatorFromMap(final Multimap<Symbol, Symbol> linkableTypes) {
    return MapLinkingValidator.create(linkableTypes);
  }

  public static LinkingValidator alwaysValidValidator() {
    return AlwaysValid.create();
  }
}

final class AlwaysValid implements LinkingValidator {

  private AlwaysValid() {

  }

  public static AlwaysValid create() {
    return new AlwaysValid();
  }

  @Override
  public void validate(final ResponseLinking linking) {

  }

  @Override
  public boolean validToLink(final Response a, final Response b) {
    return true;
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
  public void validate(final ResponseLinking linking) {
    for (final ResponseSet rs : linking.responseSets()) {
      for (final Response r : rs) {
        for (final Response s : rs) {
          if (!validToLink(r, s)) {
            throw new RuntimeException(
                String.format("Response %s and %s were unlinkable", r.toString(), s.toString()));
          }
        }
      }
    }
  }

  @Override
  public boolean validToLink(final Response a, final Response b) {
    return linkableTypes.get(a.type()).contains(b.type()) && linkableTypes.get(b.type())
        .contains(a.type());
  }

}

