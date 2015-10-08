
mkdir -p ../output/interim_2015/graphAnalyses/withRealis
sh ~/repos/kbp/kbp-scorer-bbn/target/appassembler/bin/graphAnalyses ../params/interim_2015/graphAnalyses/graphAnalyses.kbp2015.linking.params
sh ~/repos/kbp/kbp-scorer-bbn/target/appassembler/bin/graphAnalyses ../params/interim_2015/graphAnalyses/graphAnalyses.kbp2015.PR.params
sh ~/repos/kbp/kbp-scorer-bbn/target/appassembler/bin/graphAnalyses ../params/interim_2015/graphAnalyses/graphAnalyses.kbp2015.aggregate.params
sh ~/repos/kbp/kbp-scorer-bbn/target/appassembler/bin/graphAnalyses ../params/interim_2015/graphAnalyses/graphAnalyses.kbp2015.argument.params
sh ~/repos/kbp/kbp-scorer-bbn/target/appassembler/bin/graphAnalyses ../params/interim_2015/graphAnalyses/graphAnalyses.kbp2015.neutralizeRealis.params

