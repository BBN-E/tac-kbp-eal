This is code developed by BBN to support the 
[2014 KBP Event Argument Shared Task](http://www.nist.gov/tac/2014/KBP/Event/index.html). 
A draft of the description of this task may be found [here](https://docs.google.com/document/d/1NRrRhttps://docs.google.com/document/d/1NRrRciiPMEZfqdjXEljyzWn-Zlw-jEm0PBqT-t1owJ0/edit?usp=sharing).

This repository contains two artifacts: 
* `kbp-events2014` contains classes to represent system responses and assessments for
the task. If your system is based on Java or another JVM language, feel free to
use them. This repository also contains tools for pooling system answers,
validating system answers, etc.
* `kbp-events-2014-scorer` contains the scorer for the task.

## Building 

Requirements:
* [Maven](http://maven.apache.org/)

First, check out the [`bue-common-open`](https://github.com/rgabbard-bbn/bue-common-open) repository
and do `mvn install` from its root.  Then do `mvn install` from the root of
this repository.


