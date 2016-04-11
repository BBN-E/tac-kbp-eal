This is code developed by BBN to support the
2016 TAC KBP Event Argument and Linking Shared Task.
A description of this task will be released soon.

This repository contains three artifacts:
* `tac-kbp-eal` contains classes to represent system responses and assessments for
the task. While these are mainly to support the executables in this repository,
if your system is based on Java or another JVM language, you are strongly encouraged
to use them.  Note that they can be used from Python via Jython.
* `tac-kbp-eal-scorer` contains the scoring code.
* `tac-kbp-eal-bin` contains all the non-scorer executables: the validator, the pooler, the scorer, etc.

## Building

Requirements:
* [Maven](http://maven.apache.org/)

Build steps:
* Do `mvn install` from the root of this repository.
* do `chmod +x tac-kbp-eal-bin/target/appassembler/bin/*` (you only need to do this the first time)
* do `chmod +x tac-kbp-eal-scorer/target/appassembler/bin/*` (you only need to do this the first time)

[![Build Status](https://semaphoreci.com/api/v1/projects/a11da8fb-4e16-48bf-af3a-a4e1680f161e/467466/badge.svg)](https://semaphoreci.com/rgabbard-bbn/kbp-2014-event-arguments)

## Using

The event argument linking is a complex task with a necessarily complex scoring process.  We will
present a brief overview here, but the to-be-released task description is
the authoritative description.

Systems submitted to the evaluation will produce three outputs given a corpus:
* for each document, the set of document-level event arguments.  For example, a system may say
"Bob is the `Agent` of a `Movement.Transport-Artifact` event in document `foo123`
with realis `Other` and this claim is justified by the following text 'lorem ipsum....'"".  We
call claims like the preceding sentence **system responses** (Java class `Response`).
The collection of system responses given by a system for a document is an `ArgumentOutput`.  In
document-level scoring we will also talk about **TRFR**s (for **type-role-filler-realis**; Java
`TypeRoleFillerRealis`).  A TRFR is an assertion like "Bob is the `Agent` of a
`Movement.Transport-Artifact` event in document `foo123` with realis
`Other``"*independent of any text justifying this claim*.  So a system could potentially output
multiple justifying responses for each TRFR; for example, it could find one mention of the above
event using the string *Bob Smith* as the participant and another mention in another sentence with
*Mr. Robert Smith*. These would be two responses which correspond to a single TRFR.
* for each document, an event linking.  This groups all the document-level event arguments which are
part of the same event.  The groups are called **event frames** or **event hoppers**.  There are two
two ways of looking at event frames and therefore two ways of representing them in the code. You can
look at them at the level of `Response`s, in which case you have `ResponseSet`s grouped into a
`ResponseLinking`, or you can think of them in terms of TRFRs, in which case you have
`TypeRoleFillerRealisSet`s grouped into an `EventArgumentLinking`.  An event frame can be
referred to by its document ID and event frame ID together, yielding a `DocEventFrameReference`.
* over the whole corpus, a grouping of document-level event frames which correspond to the same event.
 Each group of event frames is a `CorpusEventFrame` made up of `DocEventFrameReference`s.  All the
 `CorpusEventFrame`s for a corpus together make a `CorpusLinking`.

 A `ArgumentOutput` and a `ResponseLinking` for a single document can be joined to make a
 `DocumentSystemOutput2015`.  The collection of all `DocumentSystemOutput2015`s and the
 `CorpusLinking` together form a `SystemOutputStore2016`.

 Scoring is done in two ways:
 * document-level arguments and event frames are scored against document annotations in ERE format.
 * corpus-level event frames are scored by query and assessment.

 ### Scoring document-level event frames

 The program `ScoreAgainstERE` can take a `SystemOutputStore2016` and documents in the LDC's ERE
 format and produce a document-level scores.  More details will be added soon.

 In the code you will occasionally see references to scoring document-level event arguments by
 assessment. This was done in 2014 and 2015 and the code has been kepy for backwards compatibility.

### Scoring document-level event frames

The LDC will prepare a collection of **queries** for the corpus-level evaluation.  Each query
will identify a unique event in the document.  `CorpusQueryExecutor2016` will match each query
against a system's output to see what documents the system believes contain references to the
indicated event.  Each of these matches are then sent to LDC annotators for assessment, producing a
`CorpusQueryAssessments` object.

At scoring time `CorpusScorer` repeats the query matching process, but this time the resulting
matches are compared against LDC assessments and a score is produced.

### Evaluation Workflow

The following workflow will be used during the evaluation.  All executables referenced below may be found in
either `kbp-events2014-bin/target/appassembler/bin` or `kbp-events-2014-scorer/target/appassembler/bin`.

* a 'quote filter' to remove material with CAS and BF offsets in quoted regions
will be built from the original text of the data set.
* competitor submissions will be validated using `validateSystemOutput`.
* all material from quoted regions will be removed from competitor submissions.
* document-level scores will be created using `ScoreAgainstERE` on competitor submissions and LDC
ERE annotations.
* the corpus-level queries will be run against all systems. The LDC will assess the resulting matches.
* `CorpusScorer` will be run against the competitor submissions and the resulting LDC assessments.

### Parameter Files
Most of the executables take parameter files as input.  These have the format
```
key1: value1
# this is a comment!
key2: value2
```

### `validateSystemOutput`
This program will check that your submission:
* has the correct format
* contains only legal event roles and types

If either of these fail, the program will halt with an error message. In the future,
we plan to add enforcement of rules concerning what portions of answers may
come from within `<quote>` regions.

Additionally, this program will dump to standard output a human-readable version
of your responses with all offsets resolved to strings from the original documents
so that you can check for mistakes.

This program takes the following parameters:
* `systemOutputStore`: the path of the system output store to be validated
* `validRoles`: is `data/2016.types.txt` (for KBP 2016)
* `dump`: whether to dump response to `stdout` in a human readable format.
* `docIDMap`: (required if `dump` is `true`) a list of tab-separated pairs of doc ID and path to the
them to standard output.

Note that this currently does not validate document or corpus-level linkings.

### `ScoreAgainstERE`
Scores system output against an annotation store.

### `CorpusScorer`

The program takes the following parameters:
* `com.bbn.tac.eal.outputDir`: directory to output scoring information to
* `com.bbn.tac.eal.queryFile`: file describing queries to execute
* `com.bbn.tac.eal.queryAssessmentsFile`: file containing LDC assessments of query matches
* `com.bbn.tac.eal.systemOutputDir`: system output to score

### Baseline linking
We provide a baseline implementation of event argument linking for those who wish to try out the
scorer but have not yet developed their own algorithm.  This baseline implementation simply links
together all arguments of the same event type in a document.  To run this, use `ApplyLinkingStrategy`.

Parameters:
* `argumentSystemStore`: the system's argument output
* `linkingSystemStore`: the path to write the baseline linking to

## Questions
### How can I use the `Response`, etc. in my system's code?
Add the following to the `dependencies` section of your project's `pom.xml` (or take similar steps if using Gradle, etc.):
```
<dependency>
      <groupId>com.bbn.kbp.eal</groupId>
      <artifactId>tac-kbp-eal</artifactId>
      <version>current version listed in pom.xml of this repository</version>
</dependency>
```
This artifact is not deployed to Maven Central, so you will need to
install it in your local repository as described above.

## Contact
For questions concerning the software, please contact `rgabbard@bbn.com`.  If you
have bugs or feature requests, you can use the GitHub Issue Tracker. The issue tracker is
preferred so future users can benefit from the answers to your questions. To track changes to
this repository, follow https://github.com/BBN-E/tac-kbp-eal/commits/master.atom in an RSS reader.
