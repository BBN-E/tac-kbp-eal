Instructions for evaluating system outputs. 

These instructions are for the 2016 evalutation.  The 2017 evaluation is similar except there is no
corpus-level scoring step.  The actual evaluation scoring was done using the scripts `bin/evaluate201{6,7}.sh`

`$KBPOPENREPO` refers to your clone of the github repository available at https://github.com/BBN-E/tac-kbp-eal
`tac-kbp-eal` refers to the subproject `tac-kbp-eal` of the `$KBPOPENREPO`
A docidmap is a file which specifies a mapping between LDC document IDs and 
file system paths. Each mapping entry appears as a single line consisting of the
document ID followed by a tab character followed by the absolute path to
the corresponding file.

To verify your submission and run the evaluator:
```bash
export KBPOPENREPO=$KBPOPENREPO
$KBPOPENREPO/bin/evaluate2016.sh params
```

Where params is similar to the following sample:
```
# a scratch directory for writing your output and results
com.bbn.tac.eal.scratch: /nfs/mercury-06/u17/data/kbp-2016/dry-run/june-validation-script/scratch
# the uncompressed submissions, this should contain only system outputs and nothing else, e.g. our 
# system might output /nfs/mercury-06/u17/data/kbp-2016/dry-run/june-validation-script/stores/{BBN1,BBN2},
# then we would use param:
com.bbn.tac.eal.participants: /nfs/mercury-06/u17/data/kbp-2016/dry-run/june-validation-script/stores
# a docid to file map for the entire input corpus.
com.bbn.tac.eal.rawTextMap: /nfs/mercury-06/u17/data/kbp-2016/dry-run/input.docidmap
# a quote filter, built as described in (3) below. This should point to a non-existent file in an existing directory.
com.bbn.tac.eal.quoteFilter: /nfs/mercury-06/u17/data/kbp-2016/dry-run/june-validation-script/quoteFilter
# docids in the richERE
com.bbn.tac.eal.docIDsToScore: /nfs/mercury-04/u10/kbp/2016/dry-run/docIDsToScore.list
# richERE documents
com.bbn.tac.eal.eremap: /nfs/mercury-06/u17/data/kbp-2016/dry-run/processing/LDC2016R14_Rich_ERE_English_Training_Data_R1_with_Augmented_Events.docidmap
# coreNLP processed raw source documents, see [README](README.md#Using CoreNLP)
com.bbn.tac.eal.coreNLPDocIDMap: /nfs/mercury-06/u17/data/kbp-2016/dry-run/corenlp/LDC2016R14_Rich_ERE_English_Training_Data_R1_with_Augmented_Events.docidmap
# LDC query file
com.bbn.tac.eal.queryFile: /nfs/mercury-06/u17/data/kbp-2016/dry-run/LDC2016E51_TAC_KBP_2016_English_Event_Argument_Linking_Pilot_Queries_and_Manual_Run/data/tac_kbp_2016_english_event_argument_linking_pilot_queries.tab
# how much the predicate justifications may differ
com.bbn.tac.eal.slack: 300
# restrict entry points to NAM (true) or allow NOM, PRO (false) as well?
com.bbn.tac.eal.matchBestCASTypesOnly: false
com.bbn.tac.eal.minNominalCASOverlap: 0.3
com.bbn.tac.eal.maxResponsesPerQueryPerSystem: 200
```


## Evaluation Steps

Here are the steps that `bin/evaluate2016.sh` performs:

1. Convert to the canonical IDs used by the scorer  using `$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/importForeignIDs`:  
    Params:

    ```
    input: /path/to/your/system/output
    output: /path/to/canonical/id/output
    doMultipleStores: false
    outputLayout: KBP_EAL_2016
    ```

2. Validate system output using `$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/validateSystemOutput2016`:  
    Params:

    ```
    systemOutputStore: /path/to/canonical/id/output
    dump: false
    docIDMap: a map of docid to raw text
    validRoles: $KBPOPENREPO/data/2016.types.txt
    linkableTypes: $KBPOPENREPO/data/2016.linkable.txt
    ```

3. Filter out responses that appear in quotes:
    Build the quote filter using `$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/buildQuoteFilter`:
    Params:
    ```
    quoteFilter: /path/to/output/file
    docIdToFileMap: a map or docid to raw text
    ```

    Filter the responses: using `$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/applyQuoteFilter`:
    Params:

    ```
    inputStore:
    outputStore:
    quoteFilter:
    ```

4. Restrict system outputs to just the best justification for each assertion
 using `$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/keepOnlyBestResponses`:  
    Params:

    ```
    inputStore: /path/to/canonical/id/output
    outputStore: keepBestOutput
    outputLayout: KBP_EAL_2016
    keepInferenceCases: false
    ```
    
5. Score System output against ERE using `$KBPOPENREPO/tac-kbp-eal-scorer/target/appassembler/bin/scoreKBPAgainstERE`:
    Params (these will change, we encourage trying different values and sending us feedback):
    ```
    outputLayout: KBP_EAL_2016
    systemOutput: keepBestOutput
    docIDsToScore:
    goldDocIDToFileMap: # docid map of richere annotation
    ereScoringOutput: # output directory
    # true or false; corresponds to whether or not we use relaxed offsets in scoring
    relaxUsingCoreNLP: true
    coreNLPDocIDMap: # map of core nlp processed documents, only necessary if relaxUsingCoreNLP is true
    useExactMatchForCoreNLPRelaxation: false
    ```


6. Extract corpus-level query responses using `$KBPOPENREPO/tac-kbp-eal/target/appassembler/bin/queryResponseFromERE`:  
    Params (these may change come the evaluation and feedback from the LDC):
    ```
    com.bbn.tac.eal.storeDir: /output/dir/of/keep/Best
    com.bbn.tac.eal.storesToProcess: systemNamesInTheAboveDirectory
    com.bbn.tac.eal.outputFile: outputFile
    com.bbn.tac.eal.eremap: ere docid to filepath map.
    com.bbn.tac.eal.queryFile: input queries
    com.bbn.tac.eal.slack: 300
    com.bbn.tac.eal.matchBestCASTypesOnly: false
    com.bbn.tac.eal.minNominalCASOverlap: 0.3
    com.bbn.tac.eal.maxResponsesPerQueryPerSystem: 200
    ```

7. These will be sent to the LDC for evaluation. When repsonses are received, the corpus-level scorer will be run as follows:

    ```
    CorpusScorer
    
    com.bbn.tac.eal.outputDir: directory to output scoring information to
    com.bbn.tac.eal.queryFile: file describing queries to execute
    com.bbn.tac.eal.queryAssessmentsFile: file containing LDC assessments of query matches
    com.bbn.tac.eal.systemOutputDir: system output to score
    com.bbn.tac.eal.eremap: ere docid to filepath map.
    com.bbn.tac.eal.queryFile: input queries
    com.bbn.tac.eal.slack: 300
    com.bbn.tac.eal.matchBestCASTypesOnly: false
    com.bbn.tac.eal.minNominalCASOverlap: 0.3
    com.bbn.tac.eal.maxResponsesPerQueryPerSystem: 200
    ```
