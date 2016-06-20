Instructions for processing input repositories:
`tac-kbp-eal` refers to your clone of the github repository or the `tac-kbp-eal` subproject.
A docidmap is a file which specifies a mapping between document IDs and 
file system paths. Each mapping entry appears as a single line consisting of the
document ID followed by a tab character followed by the absolute path to
the corresponding file.

1. Convert to the canonical IDs used by the scorer  using `tac-kbp-eal/target/appassembler/bin/importForeignIDs`:  
    Params:

    ```
    input: /path/to/your/system/output
    output: /path/to/canonical/id/output
    doMultipleStores: false
    outputLayout: KBP_EAL_2016
    ```

2. Validate system output using `tac-kbp-eal/target/appassembler/bin/validateSystemOutput2016`:  
    Params:

    ```
    systemOutputStore: /path/to/canonical/id/output
    dump: false
    docIDMap: a map of docid to raw text
    validRoles: tac-kbp-eal/data/2016.types.txt
    linkableTypes: tac-kbp-eal/data/2016.linkable.txt
    ```

3. Filter out responses that appear in quotes:
    Build the quote filter using `tac-kbp-eal/target/appassembler/bin/buildQuoteFilter`:
    Params:
    ```
    quoteFilter: /path/to/output/file
    docIdToFileMap: a map or docid to raw text
    ```

    Filter the responses: using `tac-kbp-eal/target/appassembler/bin/applyQuoteFilter`:
    Params:

    ```
    inputStore:
    outputStore:
    quoteFilter:
    ```

4. Restrict system outputs to just the best justification for each assertion
 using `tac-kbp-eal/target/appassembler/bin/keepOnlyBestResponses`:  
    Params:

    ```
    inputStore: /path/to/canonical/id/output
    outputStore: keepBestOutput
    outputLayout: KBP_EAL_2016
    keepInferenceCases: false
    ```
    
5. Extract corpus-level query responses using `tac-kbp-eal/target/appassembler/bin/queryResponseFromERE`:  
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
