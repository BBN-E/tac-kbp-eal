
#1/bin/bash

# run this script on participant output first to put it in canonical form and 
# split apart the three languages.  Then run evaluate2017.sh on each language.
# This script also builds a record of what regions in DF documents are in quotes
# and should not be scored.

# enables debug mode
set -x
set -e
set -o nounset
set -o pipefail


function param_value() {
    params_file="$1"
    param_name="$2"
    # get a param and delete the first space.
    # uncommented line below is for Mac OS X, where 2017 eval was scored
    # for Linux, switch which line is uncommented
    #cat $params_file | grep "^$param_name" | cut -d':' -f 2- | sed -re 's/\s+//'
    cat $params_file | grep "^$param_name" | cut -d':' -f 2- | sed -E 's/[[:space:]]+//'
}


: ${KBPOPENREPO:?"Need to set KBPOPENREPO to path to working copy of tac-kbp-eal"}

INPUT_PARAMS="$1"
# input params must define:
SCRATCH=$(param_value $INPUT_PARAMS com.bbn.tac.eal.scratch)
PARTICIPANTS=$(param_value $INPUT_PARAMS com.bbn.tac.eal.participants)
RAW_TEXT_MAP=$(param_value $INPUT_PARAMS com.bbn.tac.eal.rawTextMap)
QUOTEFILTER=$(param_value $INPUT_PARAMS com.bbn.tac.eal.quoteFilter)

rm -fr "$SCRATCH/processing"
rm -fr "$SCRATCH/params"
rm -fr "$SCRATCH/log"
rm -fr "$SCRATCH/finalStores"

mkdir -p "$SCRATCH/finalStores"
mkdir -p "$SCRATCH/params"
LOG="$SCRATCH/log"
mkdir -p $LOG

# step 3 part 1: build a quoteFilter (this only needs to be done once and can be reused):
build_quote_filter_params="$SCRATCH/params/build_quote_filter.params"
cat <<EOF > $build_quote_filter_params
docIdToFileMap: $RAW_TEXT_MAP
quoteFilter: $QUOTEFILTER
EOF
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/buildQuoteFilter $build_quote_filter_params

echo "Converting all submissions to use canonical form for IDs"
convert_params="$SCRATCH/params/canonicalIds.params"
converted="$SCRATCH/processing/withCanonicalIds"
cat <<EOF > $convert_params
inputBase: $PARTICIPANTS
outputBase: $converted
doMultipleStores: true
outputLayout: KBP_EAL_2016
EOF
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/importForeignIDs $convert_params 2>&1 | tee $LOG/convert.log

echo "Splitting up by language"
split_by_lang_params="$SCRATCH/params/splitByLang.params"
splitByLangDir="$SCRATCH/processing/splitByLang"
cat <<EOF > $split_by_lang_params
inputDir: $converted
baseOutputDir: $splitByLangDir
EOF

$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/splitByLanguage $split_by_lang_params 2>&1 | tee $LOG/split_by_lang.log

echo "Set participants for each per-language param file for evaluate2017.sh to the appropriate language directory under $splitByLangDir"
