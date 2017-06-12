#1/bin/bash

# This script is based around the instructions in Evaluation.md, modified to suit running with multiple systems
# It is a sample script that shows how to run each of the intermediate steps; it is recommended that you write
# separate parameter files and run the steps individually instead of through this script.


# enables debug mode
set -x
set -e
set -o nounset
set -o pipefail


function param_value() {
    params_file="$1"
    param_name="$2"
    # get a param and delete the first space.
    cat $params_file | grep "^$param_name" | cut -d':' -f 2- | sed -re 's/\s+//'
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

# the input params should also inlcude (for extracting corpus level queries)
# com.bbn.tac.eal.queryFile: # queries from the LDC
# com.bbn.tac.eal.slack: 300 # how much difference in PJ offsets we allow
# com.bbn.tac.eal.matchBestCASTypesOnly: false # Only NAM or allow PRO/NOM?
# com.bbn.tac.eal.minNominalCASOverlap: 0.3 # the minimum fraction of overlap requires to match nominal CASes against each other
# com.bbn.tac.eal.maxResponsesPerQueryPerSystem: 200 # to cut the outputs so the LDC can actually finish


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

for system in "$PARTICIPANTS"/* ; do
    echo $system
    system_name=$(basename $system)
    LOG="$SCRATCH/log/$system_name"
    mkdir -p "$SCRATCH/params/$system_name"
    mkdir -p "$SCRATCH/processing/$system_name"
    mkdir -p $LOG
    # evaluation step 1: convert to canonical ids
    convert_params="$SCRATCH/params/$system_name/convert.params"
    converted="$SCRATCH/processing/$system_name/converted"
cat <<EOF > $convert_params
input: $system
output: $converted
doMultipleStores: false
outputLayout: KBP_EAL_2016
EOF
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/importForeignIDs $convert_params 2>&1 | tee $LOG/convert.log

    # evaluation step 2: validate the system output store
    validate_params="$SCRATCH/params/$system_name/validate.params"
cat <<EOF > $validate_params
systemOutputStore: $converted
dump: false
docIDMap: $RAW_TEXT_MAP
validRoles: $KBPOPENREPO/data/2016.types.txt
linkableTypes: $KBPOPENREPO/data/2016.linkable.txt
EOF
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/validateSystemOutput2016 $validate_params 2>&1 | tee $LOG/validate.log

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
# these three parameters control using corenlp for scoring. If you don't intend
# on using coreNLP then relaxUsingCoreNLP should be set to false, and the other
# two parameters (coreNLPDocIDMap, useExactMatchForCoreNLPRelaxation) become
# moot and can excluded.
coreNLPDocIDMap: $CORENLP_MAP
relaxUsingCoreNLP: true
useExactMatchForCoreNLPRelaxation: false

quoteFilter: $QUOTEFILTER
bannedRoles: NONE
eventTypesToScore: $KBPOPENREPO/data/2016.types.txt
language: eng
EOF
    $KBPOPENREPO/tac-kbp-eal-scorer/target/appassembler/bin/scoreKBPAgainstERE $score_kbp_params 2>&1 | tee $LOG/scoreKBPAgainstERE.log

    # evaluation step 6, kind of: aggregate the system outputs for the query extraction list
    ln -s $system $SCRATCH/finalStores/$system_name

done

# finish step 6
# gathering query responses happens at the end since it's a per system step
query_response_from_ere_params=$SCRATCH/params/queryResponse.params
system_outputs_list=$(echo $(readlink -e $SCRATCH/finalStores/)/* | xargs -I{} basename {} | tr '\n' ',' | sed -e 's/,$//')
cat <<EOF  > $query_response_from_ere_params
com.bbn.tac.eal.storeDir: $SCRATCH/finalStores/
com.bbn.tac.eal.storesToProcess: $system_outputs_list
com.bbn.tac.eal.outputFile: $SCRATCH/queryResponses
INCLUDE $INPUT_PARAMS
EOF
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/queryResponseFromERE $query_response_from_ere_params 2>&1 | tee $SCRATCH/generateQueryResponses.log 2>&1 | tee $SCRATCH/generateQueryResponses.log
