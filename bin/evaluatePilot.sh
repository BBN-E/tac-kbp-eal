#1/bin/bash

# uncomment the following line for debug mode
#set -x
set -e

EXPAND=true
QUOTEFILTER=true

: ${KBPOPENREPO:?"Need to set KBPOPENREPO to path to working copy of kbp-2014-event-arguments"}
: ${PARTICIPANTS:?"Need to set PARTICIPANTS to path of a copy of KBP2014_event-argument-pilot_runs_20140421.tgz"}
: ${ASSESSMENTS:?"Need to set $ASSESSMENTS to path of a copy of LDC2014E40_TAC_2014_KBP_Event_Argument_Extraction_Pilot_Assessment_Results.tgz"}

EVALDIR=${KBPOPENREPO}/output/pilotEval
LOG=$EVALDIR/log/evaluation.log 

echo "Using working copy $KBPOPENREPO"
echo "Writing log to $EVALDIR/log"

# clear previous run, if any
echo "Output will be written to $EVALDIR"
echo "Clearing previous output, if any"
rm -rf $EVALDIR

echo "Creating output directory"
mkdir -p $EVALDIR
mkdir $EVALDIR/log

# uncompress participant submissions
PARTICIPANTCOPY=$EVALDIR/participantSubmissions
mkdir -p $PARTICIPANTCOPY
echo "Uncompressing participant submissions from $PARTICIPANTS to $PARTICIPANTCOPY"
tar xzf $PARTICIPANTS -C $PARTICIPANTCOPY  --strip-components=1

pushd $PARTICIPANTCOPY
echo "Uncompressing .zip submissions"
shopt -s nullglob
for f in *.zip; do
    strippedName=${f%.zip}
    echo "Unzipping $strippedName"
    unzip -q $f -d $strippedName 
done

echo "Uncompressing .tar.gz submissions"
for f in *.tar.gz; do
    strippedName=${f%.tar.gz}
    mkdir $strippedName
    echo "Unzipping $strippedName"
    tar xzf $f -C $strippedName
done


echo "Uncompressing .tgz submissions"
for f in *.tgz; do
    strippedName=${f%.tgz}
    mkdir $strippedName
    echo "Unzipping $strippedName"
    tar xzf $f -C $strippedName
done
popd
shopt -u nullglob

# copy LDC assessments
LDCCOPY=$EVALDIR/ldcAssessment
mkdir -p $LDCCOPY
echo "extracting LDC assessments from $ASSESSMENTS to $LDCCOPY"
tar xzf $ASSESSMENTS -C $LDCCOPY --strip-components=2

ASSESSDIR=$LDCCOPY/data/LDC_assessments

# remove .out from LDC assessments
echo "Removing .out suffix from LDC assessment files"
pushd $ASSESSDIR
rename .out "" *.out
popd

# repair LDC assessments
echo "Repairing assessment store..."
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/repairAnnotationStore $KBPOPENREPO/params/pilotEvaluation/repair.params >> $LOG

# apply realis expansion to LDC assessments
if [ "$EXPAND" = true ];
then
    echo "Expanded assessment store using realis assessments..."
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/expandByRealis $KBPOPENREPO/params/pilotEvaluation/expand.params >> $LOG
else
    cp -r $EVALDIR/repaired $EVALDIR/expanded
fi

# quote filter participant submissions
if [ "$QUOTEFILTER" = true ];
then
    echo "Applying quote filter to pilot submissions..."
    $KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/applyQuoteFilter $KBPOPENREPO/params/pilotEvaluation/quoteFilter.params >> $LOG
else
    cp -r $EVALDIR/participantSubmissions $EVALDIR/quoteFiltered
fi

# score
echo "scoring..."
$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/kbpScorer $KBPOPENREPO/params/pilotEvaluation/score.params >> $LOG

SUMMARYFILE=$EVALDIR/summary.txt
echo "Summarizing scores to $SUMMARYFILE"
tail  $EVALDIR/scoreOutput/*/Standard/Aggregate  >> $SUMMARYFILE
