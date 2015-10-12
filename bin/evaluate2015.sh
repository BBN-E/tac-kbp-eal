#1/bin/bash

# uncomment the following line for debug mode
set -x
set -e
set -o nounset

EXPAND=true
QUOTEFILTER=true
KEEPBEST=true

: ${KBPREPO:?"Need to set KBPREPO to path to working copy of kbp"}
: ${KBPOPENREPO:?"Need to set KBPOPENREPO to path to working copy of kbp-2014-event-arguments"}
: ${PARTICIPANTS:?"Need to set PARTICIPANTS to /nfs/mercury-04/u22/kbp-2015/eval_analysis/interim_2015/systemsOutput"}
: ${ASSESSMENTS:?"Need to set $ASSESSMENTS to /nfs/mercury-04/u22/kbp-2015/eval_analysis/interim_2015/assessments"}

EVALDIR=${KBPOPENREPO}/output/interim_2015
LOG=$EVALDIR/log/interim_2015 

echo "Using working copy $KBPOPENREPO"
echo "Writing log to $EVALDIR/log"

# clear previous run, if any
echo "Output will be written to $EVALDIR"
echo "Clearing previous output, if any"
rm -rf $EVALDIR

echo "Creating output directory"
mkdir -p $EVALDIR/log

# uncompress participant submissions
PARTICIPANTCOPY=$EVALDIR/participantSubmissions
mkdir -p $PARTICIPANTCOPY
echo "Copying participant submissions from $PARTICIPANTS to $PARTICIPANTCOPY"
#shopt -s extglob
cp -r $PARTICIPANTS/* $PARTICIPANTCOPY
#shopt -u extglob


#shopt -u nullglob

# copy LDC assessments
LDCCOPY=$EVALDIR/ldcAssessment
ASSESSDIR=$LDCCOPY/data/LDC_assessments
#mkdir -p $LDCCOPY
#echo "extracting LDC assessments from $ASSESSMENTS to $LDCCOPY"
#tar xzf $ASSESSMENTS -C $LDCCOPY --strip-components=2
echo "Copying LDC assessments from $ASSESSMENTS to $ASSESSDIR"
mkdir -p $ASSESSDIR
cp -r $ASSESSMENTS/{annotation,linkingStore} $ASSESSDIR/

# apply realis expansion to LDC assessments
if [ "$EXPAND" = true ]; then
    echo "Expanded assessment store using realis assessments..."
    $KBPOPENREPO/kbp-events2014-bin/target/appassembler/bin/expandByRealis $KBPOPENREPO/params/interim_2015/expand.params > $LOG.expand.log

    mkdir -p $EVALDIR/expanded/linkingStore
    cp $ASSESSDIR/linkingStore/* $EVALDIR/expanded/linkingStore
fi

# quote filter participant submissions
if [ "$QUOTEFILTER" = true ]; then
    echo "Applying quote filter to submissions..."
    $KBPOPENREPO/kbp-events2014-bin/target/appassembler/bin/applyQuoteFilter $KBPOPENREPO/params/interim_2015/quoteFilter.params > $LOG.quoteFilter.log
fi

# do keepBest
if [ "$KEEPBEST" = true ] ; then
    echo "Applying keep best to assessment stores"

    for f in $EVALDIR/quoteFiltered/*; do
      if [ -d ${f} ]; then
        sysId=$(basename $f)
        mkdir -p $EVALDIR/keepBest/$sysId

	cat <<EOF > $KBPOPENREPO/params/interim_2015/generated/keepBest_${sysId}.params
	inputStore: $f
	outputStore: $EVALDIR/keepBest/$sysId
	keepInferenceCases: false
	outputLayout: KBP_EA_2015
	EOF

        $KBPOPENREPO/kbp-events2014-bin/target/appassembler/bin/keepOnlyBestResponses $KBPOPENREPO/params/interim_2015/generated/keepBest_${sysId}.params > $LOG.keepBest_${sysId}.log
      fi
    done
fi


mkdir -p $EVALDIR/score/withRealis
$KBPREPO/kbp-scorer-bbn/target/appassembler/bin/kbpScorer2015 $KBPOPENREPO/params/interim_2015/BBNKBPScorer2015.params > $LOG.scorer2015.log

mkdir -p $EVALDIR/score/neutralizeRealis
$KBPREPO/kbp-scorer-bbn/target/appassembler/bin/kbpScorer2015 $KBPOPENREPO/params/interim_2015/BBNKBPScorer2015.neutralizeRealis.params > $LOG.scorer2015_neutralizeRealis.log



