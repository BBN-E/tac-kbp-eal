#1/bin/bash

### WARNING / TODO / README - this script is a work in progress for the 2016 eval and only supports working with the provided test store!

# enables debug mode
set -x
set -e
set -o nounset
set -o pipefail

EXPAND=true
QUOTEFILTER=true
KEEPBEST=true

# currently just 2016_test
CONFIG=$1


: ${KBPOPENREPO:?"Need to set KBPOPENREPO to path to working copy of kbp-2014-event-arguments"}

EVALDIR=${KBPOPENREPO}/output/${CONFIG}
LOG=$EVALDIR/log
PARTICIPANTS=${EVALDIR}/systemsOutput
ASSESSMENTS=${EVALDIR}/assessments

PARAMSDIR=${KBPOPENREPO}/params/${CONFIG}

mkdir -p ${PARAMSDIR}/generated/${CONFIG}


echo "Using working copy $KBPOPENREPO"
echo "Running config $CONFIG"
echo "Writing log to $EVALDIR/log"

# clear previous run, if any
echo "Output will be written to $EVALDIR"
echo "Clearing previous output, if any"
rm -rf $EVALDIR/expanded
rm -rf $EVALDIR/graphAnalyses
rm -rf $EVALDIR/keepBest
rm -rf $EVALDIR/log
rm -rf $EVALDIR/quoteFiltered
rm -rf $EVALDIR/score


echo "Creating output directory"
mkdir -p $EVALDIR/log

# TODO restore decompressing participant submissions

# quote filter participant submissions
if [ "$QUOTEFILTER" = true ]; then
    echo "Applying quote filter to submissions..."
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/applyQuoteFilter $PARAMSDIR/quoteFilter.params > $LOG/quoteFilter.log
fi

# do keepBest
if [ "$KEEPBEST" = true ] ; then
    echo "Applying keep best to systems"

    for f in $EVALDIR/quoteFiltered/*; do
      if [ -d ${f} ]; then
        sysId=$(basename $f)
        mkdir -p $EVALDIR/keepBest/$sysId

	cat <<EOF > $PARAMSDIR/generated/$CONFIG/keepBest_${sysId}.params
inputStore: $f
outputStore: $EVALDIR/keepBest/$sysId
keepInferenceCases: false
outputLayout: KBP_EAL_2016
EOF

        $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/keepOnlyBestResponses $PARAMSDIR/generated/$CONFIG/keepBest_${sysId}.params > $LOG/keepBest_${sysId}.log
      fi
    done
fi


$KBPOPENREPO/tac-kbp-eal-scorer/target/appassembler/bin/scoreKBPAgainstERE $PARAMSDIR/scoreAgainstERE.params > $LOG/scorer2016.log

