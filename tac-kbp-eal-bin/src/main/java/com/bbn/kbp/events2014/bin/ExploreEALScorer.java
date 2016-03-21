package com.bbn.kbp.events2014.bin;

import java.io.File;
import java.io.IOException;





import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.Files;

public class ExploreEALScorer {

  public static void main(String[] args) throws Exception {
    
    final String eaDir = "/nfs/mercury-04/u22/kbp-annotation/annotation";  
    final String[] targetDocs = {"AFP_ENG_20030304.0250", "AFP_ENG_20100414.0615", "AFP_ENG_20100601.0724", "APW_ENG_20030520.0757", "APW_ENG_20090611.0697"};
    final String outputDir = "/nfs/mercury-04/u22/kbp-2015/experiments/scorer/exploreEALScorer/html";
    
    final ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> keyClusterings = readClusteringFromDirectory("/nfs/mercury-04/u22/kbp-2015/experiments/scorer/exploreEALScorer/key_eq", targetDocs);
    final ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> sysClusterings = readClusteringFromDirectory("/nfs/mercury-04/u22/kbp-2015/experiments/scorer/exploreEALScorer/sameEventType_eq", targetDocs);
    
    final ImmutableMap<String, String> hashToSimpleIds = toSimpleId(keyClusterings);
    
    for(final String docId : sysClusterings.keySet()) {
      final String keyClusteringString = clusteringToString( keyClusterings.get(docId), hashToSimpleIds);
      final String sysClusteringString = clusteringToString( sysClusterings.get(docId), hashToSimpleIds);
      
      final String outfilename = outputDir+"/"+docId;
      PrintWriter writer = new PrintWriter(outfilename+".txt", "UTF-8");
      writer.println("key "+keyClusteringString);
      writer.println("sys "+sysClusteringString);
      writer.close();
    }
  }
  
  private static void sameEventTypeLinking(final String eaDir, final String[] targetDocs) throws IOException {
    final String ealDir = "/nfs/mercury-04/u22/kbp-annotation/nonprimary/mfreedma-rgabbard";
    final String outputDir = "/nfs/mercury-04/u22/kbp-2015/experiments/scorer/exploreEALScorer/sameEventType";
    
    final ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> goldClusterings = readClusteringFromDirectory(ealDir, targetDocs);
    
    final ImmutableMap<String, String> hashToSimpleIds = toSimpleId(goldClusterings);
    
    final ImmutableTable<String, String, String> eaEventTypes = getEventTypeOfEATuples(eaDir, targetDocs);  // docid ea-hashId eventType
    
    
    final ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> systemClusterings =  sameEventTypeClustering(eaEventTypes, hashToSimpleIds);
    
    for(final String docId : systemClusterings.keySet()) {
      final ImmutableSet<ImmutableSet<String>> clustering = systemClusterings.get(docId);
      
      final String outfilename = outputDir+"/"+docId;
      PrintWriter writer = new PrintWriter(outfilename, "UTF-8");
      
      for(final ImmutableSet<String> cluster : clustering) {
        StringBuffer s = new StringBuffer("");
        for(final String hashId : cluster) {
          if(s.length() > 0) {
            s.append("\t");
          }
          s.append(hashId);
        }
        writer.println(s.toString());
      }
      writer.close();
    }
    
    /*
    for(final String docId : goldClusterings.keySet()) {
      final String keyClusteringString = clusteringToString( goldClusterings.get(docId), hashToSimpleIds);
      final String sysClusteringString = clusteringToString( systemClusterings.get(docId), hashToSimpleIds);
      System.out.println(docId);
      final String outfilename = outputDir+"/"+docId;
      PrintWriter writer = new PrintWriter(outfilename+".txt", "UTF-8");
      writer.println("key "+keyClusteringString);
      writer.println("sys "+sysClusteringString);
      writer.close();
    }
    */
  }
  
  
  
  private static String clusteringToString(
      final ImmutableSet<ImmutableSet<String>> clustering, final ImmutableMap<String, String> hashToSimpleIds) {
    StringBuffer ret = new StringBuffer("");
    
    for(final ImmutableSet<String> cluster : clustering) {
      if(cluster.size() > 0) {
        if(ret.length() > 0) {
          ret.append(" ");
        }
        
        StringBuffer s = new StringBuffer("");
        for(final String hashId : cluster) {
          if(s.length() > 0) {
            s.append(",");
          }
          final String simpleId = hashToSimpleIds.get(hashId);
          s.append(simpleId);
        }
        ret.append(s.toString());
      }
    }
    
    return ret.toString();
  }
  
  private static ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> sameEventTypeClustering(
      final ImmutableTable<String, String, String> eaEventTypes, final ImmutableMap<String, String> hashToSimpleIds) {
    final ImmutableMap.Builder<String, ImmutableSet<ImmutableSet<String>>> ret = ImmutableMap.builder();
    
    for(final String docId : eaEventTypes.rowKeySet()) {
      final ImmutableMap<String, String> tupleEventTypes = eaEventTypes.row(docId); // ea-tuple-hashid -> event-type
      final ImmutableSet<ImmutableSet<String>> docClustering = sameEventTypeClusteringForDoc(tupleEventTypes, hashToSimpleIds);
      
      ret.put(docId, docClustering);
    }
    
    return ret.build();
  }
  
  private static ImmutableSet<ImmutableSet<String>> sameEventTypeClusteringForDoc(
      final ImmutableMap<String, String> tupleEventTypes, final ImmutableMap<String, String> hashToSimpleIds) {
    final ImmutableSet.Builder<ImmutableSet<String>> ret = ImmutableSet.builder();
    
    Multimap<String, String> eventTypeClustering = ArrayListMultimap.create();
    for(final Map.Entry<String, String> entry : tupleEventTypes.entrySet()) {
      final String hashId = entry.getKey();
      final String eventType = entry.getValue();
      if(hashToSimpleIds.containsKey(hashId)) {
        eventTypeClustering.put(eventType, hashId);
      }
    }
      
    for(final Entry<String, Collection<String>> entry : eventTypeClustering.asMap().entrySet()) {
      final String eventType = entry.getKey();
      ret.add(ImmutableSet.copyOf(entry.getValue()));
    }
   
    return ret.build();
  }
  
  private static ImmutableTable<String, String, String> getEventTypeOfEATuples(
      final String annotationDir, final String[] docs) throws IOException {
    final ImmutableTable.Builder<String, String, String> ret = ImmutableTable.builder();
    
    for(int i=0; i<docs.length; i++) {
      final String filename = annotationDir+"/"+docs[i];
      final ImmutableList<String> lines = Files.asCharSource(new File(filename), Charsets.UTF_8).readLines();
      for(final String line : lines) {
        final String[] tokens = line.split("\t");
        final String hashId = tokens[0];
        final String eventType = tokens[2];
        ret.put(docs[i], hashId, eventType);
      }
    }
    
    return ret.build();
  }
  
  
  // mainly for later printing purposes, as printing out clusters of hash ids is too tedious to look at
  private static ImmutableMap<String, String> toSimpleId(ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> clusterings) {
    final ImmutableMap.Builder<String, String> ret = ImmutableMap.builder();
    
    int index = 1;
    for(final ImmutableSet<ImmutableSet<String>> clustering : clusterings.values()) {
      for(final String hashId : ImmutableSet.copyOf(Iterables.concat(clustering))) {
        ret.put(hashId, new Integer(index).toString());
        index += 1;
      }
    }
    
    return ret.build();
  }

  
  private static ImmutableMap<String, ImmutableSet<ImmutableSet<String>>> readClusteringFromDirectory(
      final String dir, final String[] docs) throws IOException {
    final ImmutableMap.Builder<String, ImmutableSet<ImmutableSet<String>>> ret = ImmutableMap.builder();
    
    for(int i=0; i<docs.length; i++) {
      final ImmutableSet<ImmutableSet<String>> clustering = readClusteringFromFile(dir+"/"+docs[i]);
      ret.put(docs[i], clustering);
    }
    
    return ret.build();
  }
  
  
  private static ImmutableSet<ImmutableSet<String>> readClusteringFromFile(final String filename) throws IOException {
    final ImmutableSet.Builder<ImmutableSet<String>> ret = ImmutableSet.builder();
    
    final ImmutableList<String> lines = Files.asCharSource(new File(filename), Charsets.UTF_8).readLines();
    for(final String line : lines) {
      if(line.indexOf("INCOMPLETE")==-1) {
        final ImmutableSet<String> cluster = ImmutableSet.copyOf(line.split("\t"));
        if(cluster.size() > 0) {
          ret.add(cluster);
        }
      }
    }
    
    return ret.build();
  }
  
}

