#1/bin/bash

# uncomment the following line for debug mode
#set -x
set -e

EXPAND=true
QUOTEFILTER=true

: ${KBPOPENREPO:?"Need to set KBPOPENREPO to path to working copy of kbp-2014-event-arguments"}
: ${PARTICIPANTS:?"Need to set PARTICIPANTS to /nfs/mercury-04/u10/kbp/eval/KBP2014_event-argument_runs_20140819"}
: ${ASSESSMENTS:?"Need to set $ASSESSMENTS to path of a copy of LDC2014E40_TAC_2014_KBP_Event_Argument_Extraction_Pilot_Assessment_Results.tgz"}

EVALDIR=${KBPOPENREPO}/output/final_post_qc
LOG=$EVALDIR/log/final_post_qc.log 

echo "Using working copy $KBPOPENREPO"
echo "Writing log to $EVALDIR/log"

# clear previous run, if any
echo "Output will be written to $EVALDIR"
echo "Clearing previous output, if any"
rm -rf $EVALDIR

echo "Creating output directory"
mkdir -p $EVALDIR
mkdir -p $EVALDIR/log

# uncompress participant submissions
PARTICIPANTCOPY=$EVALDIR/participantSubmissions
mkdir -p $PARTICIPANTCOPY
echo "Copying participant submissions from $PARTICIPANTS to $PARATICIPANTCOPY"
#echo "Uncompressing participant submissions from $PARTICIPANTS to $PARTICIPANTCOPY"
#tar xzf $PARTICIPANTS -C $PARTICIPANTCOPY  --strip-components=1
shopt -s extglob
cp -r $PARTICIPANTS/!(.*)/*.{tgz,zip,tar.gz} $PARTICIPANTCOPY
shopt -u extglob

pushd $PARTICIPANTCOPY
echo "Uncompressing .zip submissions in $PARTICIPANTCOPY"
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
ASSESSDIR=$LDCCOPY/data/LDC_assessments
#mkdir -p $LDCCOPY
#echo "extracting LDC assessments from $ASSESSMENTS to $LDCCOPY"
#tar xzf $ASSESSMENTS -C $LDCCOPY --strip-components=2
mkdir -p $ASSESSDIR
echo "Copying LDC assessments from $ASSESSMENTS to $ASSESSDIR"
cp -r $ASSESSMENTS/* $ASSESSDIR

# apply realis expansion to LDC assessments
if [ "$EXPAND" = true ];
then
    echo "Expanded assessment store using realis assessments..."
    $KBPOPENREPO/kbp-events2014-bin/target/appassembler/bin/expandByRealis $KBPOPENREPO/params/final/expand.params >> $LOG
fi

# quote filter participant submissions
if [ "$QUOTEFILTER" = true ];
then
    echo "Applying quote filter to submissions..."
    $KBPOPENREPO/kbp-events2014-bin/target/appassembler/bin/applyQuoteFilter $KBPOPENREPO/params/final/quoteFilter.params >> $LOG
fi

# score
echo "scoring..."
$KBPOPENREPO/kbp-events2014-bin/target/appassembler/bin/kbpScorer $KBPOPENREPO/params/final/score.params >> $LOG

SUMMARYFILE=$EVALDIR/summary.txt
echo "Summarizing scores to $SUMMARYFILE"
tail  $EVALDIR/scoreOutput/*/Standard/Aggregate  >> $SUMMARYFILE
