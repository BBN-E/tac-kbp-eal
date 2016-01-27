/**
 * A library for reading CoreNLP output files.
 *
 * See the citation below for the software whose output is used as input.
 *
 * Manning, Christopher D., Mihai Surdeanu, John Bauer, Jenny Finkel, Steven J. Bethard, and David
 * McClosky. 2014. The Stanford CoreNLP Natural Language Processing Toolkit In Proceedings of the
 * 52nd Annual Meeting of the Association for Computational Linguistics: System Demonstrations, pp.
 * 55-60.
 *
 * This should be run like: {@code java -cp "*" -Xmx2g edu.stanford.nlp.pipeline.StanfordCoreNLP
 * -annotators tokenize,cleanxml,ssplit,parse -filelist <file with paths> -tokenize.options
 * invertible  -outputFormat xml -outputDirectory <output> }
 */
package com.bbn.nlp.corenlp;
