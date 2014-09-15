package com.bbn.kbp.events2014.linking;

import java.util.Collection;
import java.util.Set;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutput;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;


public final class LinkingStrategy {
	
	private LinkingStrategy() {
		throw new UnsupportedOperationException();
	}
	
	public static ResponseLinking sameEventTypeLinking(SystemOutput systemOutput) {
		final Symbol docId = systemOutput.docId();
		final Set<Response> responses = systemOutput.responses();
		
		final ImmutableSet.Builder<ResponseSet> ret = ImmutableSet.builder();
		
		final Multimap<Symbol, Response> responsesByEventType = groupByEventType(responses);
		for(final Collection<Response> responseSet : responsesByEventType.asMap().values()) {
			ret.add(ResponseSet.from(responseSet));
		}
		
		return ResponseLinking.from(docId, ret.build(), ImmutableSet.<Response>of());
	}
	
	
	private static ImmutableMultimap<Symbol,Response> groupByEventType(final Set<Response> responses) {
		final ImmutableMultimap.Builder<Symbol, Response> ret = ImmutableMultimap.builder();
		
		for(final Response response : responses) {
			ret.put(response.type(), response);
		}
		
		return ret.build();
	}
	
}