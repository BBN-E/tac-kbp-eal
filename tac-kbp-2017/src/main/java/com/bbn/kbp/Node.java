package com.bbn.kbp;

/**
 * - IDs for entities and events and document (note: objects for these)
 * - nodes: entity nodes, event nodes, string nodes
 *   - predicate with string object must take string node rather than quoted string
 *     - except: type, link, mention predicates
 * - node names
 *   - (QUESTION) IDs can be ASCII alphanumeric value but check
 *     - ID doesn't have to start with a "_" but we would like to ensure that in output it does
 *   - entity nodes: :Entity<ID>
 *   - event nodes: :Event<ID>
 *   - string nodes: :String<ID>
 *
 * Nodes must use identity-based equals and hashcode
 */
interface Node {
}

interface EventNode extends Node {
}

interface EntityNode extends Node {
}

interface StringNode extends Node {
}
