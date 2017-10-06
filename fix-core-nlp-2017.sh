#!/bin/bash

# For Chinese and Spanish CoreNLP does not handle parentehese properly and happily writes
# parses we can't load.  Run these this script from the CoreNLP output directory to
# fix them up.

perl -i -pe 's/\(PU \)/\(PU -RRB-/g' *.xml
perl -i -pe 's/\(PU \(/\(PU -LRB-/g' *.xml
perl -i -pe 's/<word>\(<\/word>/<word>-LRB-<\/word>/g' *.xml
perl -i -pe 's/<word>\)<\/word>/<word>-RRB-<\/word>/g' *.xml

