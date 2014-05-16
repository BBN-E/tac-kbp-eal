package com.bbn.kbp.events2014.scorer.bin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.*;

import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KBPScoreTabulator {
    private static final Logger log = LoggerFactory.getLogger(KBPScoreTabulator.class);

    private static String buildTable(List<String> header, List<List<String>> table) {
        
        final StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\">");
        
        sb.append("<tr>");
        for (String cell : header) {
            sb.append("<th>");
            sb.append(cell);
            sb.append("</th>");
        }
        sb.append("</tr>");
        
        for (List<String> row : table) {
            sb.append("<tr>");
            for (String cell : row) {
                sb.append("<td>");
                sb.append(cell);
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
    
    public static HashMap<String, String> getConfigurationDescriptions(File masterAnnotationRepo) throws IOException {
        HashMap<String, String> configDescriptions = new HashMap<String, String>();
        File outputStoreDir = new File(masterAnnotationRepo, "outputStores");
        String[] configDirs = outputStoreDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        for (String config : configDirs) {
            File descFile = new File(outputStoreDir, config);
            descFile = new File(descFile, "description");
            String description = Files.readLines(descFile, Charsets.UTF_8).get(0);
            configDescriptions.put(config, description);
        }
        return configDescriptions;
    }
    

    public static void main(String[] args)  {
        /* See: /nfs/mercury-04/u10/kbp/masterAnnotationRepository */
        File masterAnnotationRepo = new File(args[0]);
        
        /* See: /nfs/mercury-04/u10/kbp/scoreTracker/expts/scoreTracker-2014-05-12_14-09-49 as an example */
        File scoreTrackerDirectory = new File(args[1]);
        
        File outputHTMLFile = new File(args[2]);

        final JacksonSerializer jacksonSerializer = JacksonSerializer.forNormalJSON();

        // one table per dataset
        // figure out what the datasets are
        File datasetsDir = new File(masterAnnotationRepo, "datasets");
        List<List<List<String>>> datasetTables = new ArrayList<List<List<String>>>();
        try {
        HashMap<String, String> systemDescriptions = getConfigurationDescriptions(masterAnnotationRepo);
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputHTMLFile),"UTF-8");
            List<String> datasets = Files.readLines(new File(datasetsDir, "datasets.list"), Charsets.UTF_8);
            for (String dataset : datasets) {
                ArrayList<List<String>> datasetTable = new ArrayList<List<String>>();
                File datasetScoreDir = new File(scoreTrackerDirectory, dataset);
                if (!datasetScoreDir.exists()) {
                    continue;
                }
                // find the system configurations
                String[] configurationDirs = datasetScoreDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File current, String name) {
                      return new File(current, name).isDirectory();
                    }
                  });
                List<String> configurations = Arrays.asList(configurationDirs);
                Collections.sort(configurations);
                for (String configuration : configurations) {
                    List<String> rowValues = new ArrayList<String>();
                    rowValues.add(configuration);
                    //rowValues.add(systemDescriptions.get(configuration) != null? systemDescriptions.get(configuration) : "N/A");
                    rowValues.add("Standard");
                    File scoreDir = new File(datasetScoreDir, configuration);
                    scoreDir = new File(scoreDir, "score");
                    //File scoreDir = new File(configuration, "score");
                    File standardDir = new File(scoreDir, "Standard");
                    File scoreJsonFile = new File(standardDir, "Aggregate.json");
                    log.info("Loading scores from {}", scoreJsonFile);
                    final Map<String, FMeasureCounts> fMeasureCountsMap =
                            (Map<String, FMeasureCounts>)jacksonSerializer.deserializeFrom(Files.asByteSource(scoreJsonFile));
                    final FMeasureCounts fMeasureCounts = fMeasureCountsMap.get("Aggregate");
                    rowValues.add(Float.toString(fMeasureCounts.truePositives()));
                    rowValues.add(Float.toString(fMeasureCounts.falsePositives()));
                    rowValues.add(Float.toString(fMeasureCounts.falseNegatives()));
                    rowValues.add(Float.toString(fMeasureCounts.precision()));
                    rowValues.add(Float.toString(fMeasureCounts.recall()));
                    rowValues.add(Float.toString(fMeasureCounts.F1()));
                    datasetTable.add(rowValues);
                }
                datasetTables.add(datasetTable);
                
            }
            
            String[] header = {"System config", "Scoring Type",  "TP", "FP", "FN", "P", "R", "F1" }; 
            writer.write("<html><body>");
            writer.write("<br><br>");
            int i = 0;
            for (List<List<String>> table : datasetTables) {
                writer.write("<h0>");
                writer.write(datasets.get(i) + ":");
                writer.write("</h0>");
                writer.write("<br><br>");
                String htmlTableString = buildTable(Arrays.asList(header), table);
                writer.write(htmlTableString);
                writer.write("<br><br>");
                i++;
            }
            
            writer.write(buildDescriptionTable(systemDescriptions));
            
            writer.write("</body></html>");
            writer.close();
            
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static String buildDescriptionTable(
            HashMap<String, String> systemDescriptions) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\">");

        sb.append("</tr>");
        String[] header = {"System config", "Description"};        
        for (String cell : header) {
            sb.append("<th>");
            sb.append(cell);
            sb.append("</th>");
        }
        List<String> configs = new ArrayList<String>(systemDescriptions.keySet());
        Collections.sort(configs);
        for (String config : configs) {
            sb.append("<tr>");
            sb.append("<td>");
            sb.append(config);
            sb.append("</td>");
            sb.append("<td>");
            sb.append(systemDescriptions.get(config));
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }


}
