Instructions for processing input repositories:
`tac-kbp-eal` refers to your clone of the github repository or the `tac-kbp-eal` subproject.

1. Convert to BBN IDs using `tac-kbp-eal/target/appassembler/bin/importForeignIDs`:
Params:
```
input: /path/to/your/system/output
output: /path/to/internal/id/output
doMultipleStores: false
outputLayout: KBP_EAL_2016
```

2. Filter out invalid event types/roles using `tac-kbp-eal/target/appassembler/bin/filterOutInvalidArguments`:
Params: 
```
outputLayout: KBP_EAL_2016
layout: KBP_EAL_2016
input: output of last step
output: where ever you want it
alwaysValidRoles: Place,Time
validRoles: tac-kbp-eal/data/2016.types.txt
```


3. Filter `Generic` responses from linking store & remove anything that dies from the CorpusLinking using `tac-kbp-eal/target/appassembler/bin/filterLinkingStore`:
Params:
```
inputStore: last stage
outputStore: where do you want it?
```

4. Validate system output using `tac-kbp-eal/target/appassembler/bin/validateSystemOutput2016`:
Params:
```
systemOutputStore: last stage
dump: false
docIDMap: a map of docid to raw text
validRoles: tac-kbp-eal/data/2016.types.txt
linkableTypes: tac-kbp-eal/data/2016.linkable.txt
```

5. Minimize system outputs to just the best it produced using `tac-kbp-eal/target/appassembler/bin/keepOnlyBestResponses`:
Params:
```
inputStore: input to validation stage
outputStore: keepBestOutput
outputLayout: KBP_EAL_2016
keepInferenceCases: false
```

6. Extract query responses using `tac-kbp-eal/target/appassembler/bin/queryResponseFromERE`:
Params:
```
com.bbn.tac.eal.storeDir: /directory/containing/your/output/store
com.bbn.tac.eal.storesToProcess: nameOfYourOutputStore
com.bbn.tac.eal.outputFile: some path on your filesystem
com.bbn.tac.eal.eremap: a map of docid to LDC input ere
com.bbn.tac.eal.queryFile: the input LDC query file
# this is a temporary measure; we may replace it in the future with using corenlp sentence breaking to find a more approriate window
com.bbn.tac.eal.pjWindow: 150
```
