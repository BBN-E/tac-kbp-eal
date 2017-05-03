package com.bbn.kbp;

/**
 * A reified object in the KB (can be either an entity node, an event node, or a string node) that
 * can be used as the subject or object of an {@code Assertion}. Node IDs all start with a colon and
 * more specifically have the format ":Entity[ID]" for entity nodes, ":Event[ID]" for event nodes,
 * and ":String[ID]". All examples given have an underscore starting the ID (E.G. :Entity_0043,
 * :Event_045f, :String_4567), but the specification does not mandate this explicitly.
 */
interface Node {

}
