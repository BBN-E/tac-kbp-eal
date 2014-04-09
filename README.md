This is code developed by BBN to support the 
[2014 KBP Event Argument Shared Task](http://www.nist.gov/tac/2014/KBP/Event/index.html). 
A draft of the description of this task may be found [here](https://docs.google.com/document/d/1NRrRciiPMEZfqdjXEljyzWn-Zlw-jEm0PBqT-t1owJ0/edit?usp=sharing).

This repository contains three artifacts: 
* `kbp-events2014` contains classes to represent system responses and assessments for
the task. While these are mainly to support the executables in this repository,
if your system is based on Java or another JVM language, feel free to
use them. 
validating system answers, etc.
* `kbp-events-2014-scorer` contains the scoring code (but not scoring binary).
* `kbp-events-2014-bin` contains all the executable programs: the validator, the pooler, the scorer, etc.

## Building 

Requirements:
* [Maven](http://maven.apache.org/)

Build steps:
* Check out the [`bue-common-open`](https://github.com/rgabbard-bbn/bue-common-open) repository
and do `mvn install` from its root.  
* Do `mvn install` from the root of this repository.
* do `chmod +x kbp-events-2014-bin/target/appassembler/bin/*` (you only need to do this the first time)

## Using
### System Output Stores and Annotation Stores
A _system output store_ represents the output for a KBP Event Argument system on
a collection of documents. It consists of a directory containing exactly one
file per input document, named by the docID. The internal format of these files
is described in the task specification linked to above.

An _annotation store_ contains assessments of the answers from a system output
store.  It's format is the same as a system output store except within the files
there are additional assessment columns, as described in the task specification.

### Evaluation Workflow

The following workflow will be used during the pilot and (unless changes are made) 
real evaluations.  All executables referenced below may be found in 
`kbp-events-2014-bin/target/appassembler/bin`.

* competitor submissions will be validated using `validateSystemOutput`.
* all submissions will be combined into a single system output store using 
`poolSystemOutput`.
* this combined system output store will be transformed into an annotation store
using `importSystemOutputToAnnotationStore`.
* LDC annotators will assess this annotation store.
* All competitor submissions will be evaluated against the complete annotation
store using `kbpScorer`.

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
 * `docIDMap`: a list of tab-separated pairs of doc ID and path to the 
 corresponding original text for all files in the output store.
* `validRoles`: is `data/2014.types.txt` (for KBP 2014)

### `poolSystemOutput`
To be described.

### `importSystemOutputToAnnotationStore`
To be described.

### `kbpScorer`
To be described.

## Questions
### How can I use the `Response`, etc. in my system's code?
Add the following to the `dependencies` section of your project's `pom.xml` (or take similar steps if using Gradle, etc.):
```
<dependency>
      <groupId>com.bbn.kbp.events2014</groupId>
      <artifactId>kbp-events2014</artifactId>
      <version>1.0.0-SNAPSHOT</version>
</dependency>
```
This artifact is not deploy to Maven Central or anywhere, so you will need to
install it in your local repository as described above.

## Contact
For questions concerning the software, please contact `rgabbard@bbn.com`.  If you 
have bugs or feature requests, you can use the GitHub Issue Tracker.
