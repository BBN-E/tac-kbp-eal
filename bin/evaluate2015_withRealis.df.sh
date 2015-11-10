#1/bin/bash

# uncomment the following line for debug mode
set -x
set -e
set -o nounset

# should be one of: interim_2015_EA_exclude_ZJU  interim_2015_EA_include_ZJU  interim_2015_EAL_exclude_ZJU  interim_2015_EAL_include_ZJU
# interim_2015_EAul_exclude_ZJU  interim_2015_EAul_include_ZJU
CONFIG=$1


: ${KBPREPO:?"Need to set KBPREPO to path to working copy of kbp"}
: ${KBPOPENREPO:?"Need to set KBPOPENREPO to path to working copy of kbp-2014-event-arguments"}

EVALDIR=${KBPOPENREPO}/output/${CONFIG}
LOG=$EVALDIR/log
PARAMSDIR=${KBPOPENREPO}/params/${CONFIG}


mkdir -p $EVALDIR/score_df/withRealis
$KBPREPO/kbp-scorer-bbn/target/appassembler/bin/kbpScorer2015 $PARAMSDIR/BBNKBPScorer2015.df.params > $LOG/scorer2015.df.log

