package com.bbn.kbp;

import java.util.Set;

/**
 * An assertion about some reified object, or node, in the knowledge-base. At the very least, an
 * assertion is a "subject predicate object" triple, but it may contain other fields. The subject is
 * always a node, but the predicate and object can be multiple different things depending on the
 * type of triple. Possible additional fields for certain assertions include "provenance" and
 * "confidence".
 */
interface Assertion {

  Node subject();

  Set<Node> allNodes();
}
