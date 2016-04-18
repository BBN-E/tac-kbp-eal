#!/bin/bash

# this is a throwaway script meant for generating javadocs in pdf form
# you *must* comment out the obvious stanza from the pom file for this to work.

set -e
set -x
set -o nounset
set -o pipefail

echo "are you sure you uncommented the right part of the pom file?"
mvn clean install 
mvn javadoc:javaodc
for x in $(find . -name javadoc.rtf) ; do 
    y=$(dirname $(dirname $(dirname $x))) 
    echo $x $y 
    libreoffice --headless --invisible --norestore --convert-to pdf $x
    mv javadoc.pdf $y.pdf
done
