#1/bin/bash

# This script is based around the instructions in Evaluation.md, modified to suit running with multiple systems
# This is a copy of evaluate2016.sh modified to remove cross-document scoring since that wasn't part of the 2017 eval
# For 2017 the script has been split apart.  First run evaluate2017_split.sh on participant
# submissions to put them in a canonical form and to split the output for the three languages
# apart.  Then run this script on each language separately.


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
DOCIDS_TO_SCORE=$(param_value $INPUT_PARAMS com.bbn.tac.eal.docIDsToScore)
RICHERE_MAP=$(param_value $INPUT_PARAMS com.bbn.tac.eal.eremap)
CORENLP_MAP=$(param_value $INPUT_PARAMS com.bbn.tac.eal.coreNLPDocIDMap)

rm -fr "$SCRATCH/processing"
rm -fr "$SCRATCH/params"
rm -fr "$SCRATCH/log"
rm -fr "$SCRATCH/finalStores"

mkdir -p "$SCRATCH/finalStores"
mkdir -p "$SCRATCH/params"

# step 3 part 1: build a quoteFilter (this only needs to be done once and can be reused):
build_quote_filter_params="$SCRATCH/params/build_quote_filter.params"
cat <<EOF > $build_quote_filter_params
docIdToFileMap: $RAW_TEXT_MAP
quoteFilter: $QUOTEFILTER
EOF
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/buildQuoteFilter $build_quote_filter_params

echo "Converting all submissions to use canonical form for IDs"
convert_params="$SCRATCH/params/canonicalIds.params"
converted = "$SCRATCH/processing/withCanonicalIds"
cat <<EOF > $convert_params
input: $system
output: $converted
doMultipleStores: true
outputLayout: KBP_EAL_2016
EOF
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/importForeignIDs $convert_params 2>&1 | tee $LOG/convert.log




for system in "$PARTICIPANTS"/* ; do
    echo $system
    system_name=$(basename $system)
    LOG="$SCRATCH/log/$system_name"
    mkdir -p "$SCRATCH/params/$system_name"
    mkdir -p "$SCRATCH/processing/$system_name"
    mkdir -p $LOG
    # evaluation step 1: convert to canonical ids
    converted="$SCRATCH/processing/converted/$system_name"
    # evaluation step 2: validate the system output store
    validate_params="$SCRATCH/params/$system_name/validate.params"
cat <<EOF > $validate_params
systemOutputStore: $converted
dump: false
docIDMap: $RAW_TEXT_MAP
validRoles: $KBPOPENREPO/data/2016.types.txt
linkableTypes: $KBPOPENREPO/data/2016.linkable.txt
EOF
# we skip validation here because it complains about the extra docs for the other two languages
# the submissions were already validated when they were submitted
    #$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/validateSystemOutput2016 $validate_params 2>&1 | tee $LOG/validate.log

    # evaluation step 3: filter out responses in quotes
    quote_filter_params="$SCRATCH/params/$system_name/quoteFilter.params"
    quote_filtered="$SCRATCH/processing/$system_name/quoteFiltered"
cat <<EOF > $quote_filter_params
inputStore: $converted
outputStore: $quote_filtered
quoteFilter: $QUOTEFILTER
outputLayout: KBP_EAL_2016
EOF
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/applyQuoteFilter $quote_filter_params 2>&1 | tee $LOG/quoteFilter.log

    # evaluation step 4: keep best
    keep_best_params="$SCRATCH/params/$system_name/keepBest.params"
    keep_bested="$SCRATCH/processing/$system_name/keepBested"
cat <<EOF > $keep_best_params
inputStore: $quote_filtered
outputStore: $keep_bested
outputLayout: KBP_EAL_2016
keepInferenceCases: false
EOF
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/keepOnlyBestResponses $keep_best_params 2>&1 | tee $LOG/keepBest.log

    # evaluation step 5: scoreKBPAgainstERE
    score_kbp_params="$SCRATCH/params/$system_name/scoreKBPAgainstERE.params"
cat <<EOF > $score_kbp_params
outputLayout: KBP_EAL_2016
systemOutput: $keep_bested
docIDsToScore: $DOCIDS_TO_SCORE
goldDocIDToFileMap: $RICHERE_MAP
ereScoringOutput: $SCRATCH/processing/$system_name/scoreKBPAgainstERE
coreNLPDocIDMap: $CORENLP_MAP
relaxUsingCoreNLP: true
quoteFilter: $QUOTEFILTER
useExactMatchForCoreNLPRelaxation: false
bannedRoles: NONE
# the 2017 evaluation used the same ontology as 2016
eventTypesToScore: $KBPOPENREPO/data/2016.types.txt
language: eng
EOF
    $KBPOPENREPO/tac-kbp-eal-scorer/target/appassembler/bin/scoreKBPAgainstERE $score_kbp_params 2>&1 | tee $LOG/scoreKBPAgainstERE.log

    # evaluation step 6, kind of: aggregate the system outputs for the query extraction list
    ln -s $system $SCRATCH/finalStores/$system_name

done

