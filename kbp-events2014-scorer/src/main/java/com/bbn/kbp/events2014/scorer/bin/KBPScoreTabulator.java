package com.bbn.kbp.events2014.scorer.bin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KBPScoreTabulator {
    private static final Logger log = LoggerFactory.getLogger(KBPScoreTabulator.class);

    private static String row(String configuration, FMeasureCounts fMeasureCounts, FMeasureCounts baselineFMeasureCounts) {
        final StringBuilder sb = new StringBuilder();
        final DecimalFormat prfFormat = new DecimalFormat("###.0");
        final DecimalFormat deltaFormat = new DecimalFormat("+###.0;-###.0");
        prfFormat.setRoundingMode(RoundingMode.DOWN);
        sb.append("<tr>");
        sb.append(cell(configuration, false));
        String fMeasure = prfFormat.format(fMeasureCounts.F1() * 100);
        String baselineFMeasure = prfFormat.format(baselineFMeasureCounts.F1() * 100);
        BigDecimal baselineFMeasureBd = new BigDecimal(baselineFMeasure);
        BigDecimal fMeasureBd = new BigDecimal(fMeasure);
        BigDecimal delta = fMeasureBd.subtract(baselineFMeasureBd);
        boolean showDelta = baselineFMeasureCounts != null && !configuration.equals("baseline") && !delta.equals(BigDecimal.ZERO);
        fMeasure = showDelta ? String.format("%s (%s)", fMeasure, deltaFormat.format(delta)) : fMeasure;
        sb.append(cell(fMeasure, true));
        sb.append(cell(prfFormat.format(fMeasureCounts.precision() * 100), true));
        sb.append(cell(prfFormat.format(fMeasureCounts.recall() * 100), true));
        sb.append(cell(Float.toString(fMeasureCounts.truePositives()), false));
        sb.append(cell(Float.toString(fMeasureCounts.falsePositives()), false));
        sb.append(cell(Float.toString(fMeasureCounts.falseNegatives()), false));
        sb.append("</tr>");
        return sb.toString();
    }
    
    public static String tableHeader(String dataset) {
        final String[] tableHeader = {"System config", "F1", "P", "R", "TP", "FP", "FN"  }; 
        final StringBuilder sb = new StringBuilder();        
        // write "pilot-manual-subset (Standard scoring):" heading
        sb.append("<h0>");
        sb.append(dataset + " (Standard scoring):");
        sb.append("</h0>");
        sb.append("<br><br>");
        sb.append("<table cellpadding=\"4\" border=\"1\">");
        sb.append("<tr>");
        for (String cell : tableHeader) {
            sb.append("<th>");
            sb.append(cell);
            sb.append("</th>");
        }
        sb.append("</tr>");     
        return sb.toString();
    }
    
    public static String tableEnd() {
        final StringBuilder sb = new StringBuilder();
        sb.append("</table>");
        sb.append("<br><br>");
        return sb.toString();
    }
    
    public static String cell(String value, boolean isEmphasized) {
        final StringBuilder sb = new StringBuilder();
        sb.append(isEmphasized? "<td style=\"font-weight:bold\">" : "<td>");
        sb.append(value);
        sb.append("</td>");
        return sb.toString();
    }
    
    public static Map<String, String> getConfigurationDescriptions(File masterAnnotationRepo) throws IOException {
        Map<String, String> configDescriptions = new HashMap<String, String>();
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
        final File masterAnnotationRepo = new File(args[0]);
        
        /* See: /nfs/mercury-04/u10/kbp/scoreTracker/expts/scoreTracker-2014-05-12_14-09-49 as an example */
        final File scoreTrackerDirectory = new File(args[1]);
        
        File outputHTMLFile = new File(args[2]);

        final JacksonSerializer jacksonSerializer = JacksonSerializer.forNormalJSON();


        
        
        
        // one table per dataset
        // figure out what the datasets are
        final File datasetsDir = new File(masterAnnotationRepo, "datasets");
        
        try {
            final Map<String, String> systemDescriptions = getConfigurationDescriptions(masterAnnotationRepo);
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputHTMLFile),"UTF-8");
            
            writer.write("<html><body>");
            writer.write("<br><br>");
            
            List<String> datasets = Files.readLines(new File(datasetsDir, "datasets.list"), Charsets.UTF_8);
            for (String dataset : datasets) {

                File datasetScoreDir = new File(scoreTrackerDirectory, dataset);
                
                if (!datasetScoreDir.exists()) {
                    log.info("Dataset score directory does not exist: " + datasetScoreDir);
                    continue;
                }
                
                writer.write(tableHeader(dataset));
                
                // find the system configurations
                String[] configurationDirs = datasetScoreDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File current, String name) {
                      return new File(current, name).isDirectory();
                    }
                  });
                List<String> configurations = Arrays.asList(configurationDirs);
                Collections.sort(configurations);
                Map<FMeasureCounts, String> allFMeasureCountsMap = new HashMap<FMeasureCounts, String>();
                FMeasureCounts baselineFMeasureCounts = null;
                for (String configuration : configurations) {
                    File scoreDir = new File(datasetScoreDir, configuration);
                    scoreDir = new File(scoreDir, "score");
                    File standardDir = new File(scoreDir, "Standard");
                    File scoreJsonFile = new File(standardDir, "Aggregate.json");
                    log.info("Loading scores from {}", scoreJsonFile);
                    final Map<String, FMeasureCounts> fMeasureCountsMap =
                            (Map<String, FMeasureCounts>)jacksonSerializer.deserializeFrom(Files.asByteSource(scoreJsonFile));
                    
                    final FMeasureCounts fMeasureCounts = fMeasureCountsMap.get("Aggregate");
                    allFMeasureCountsMap.put(fMeasureCounts, configuration);
                    if (configuration.equals("baseline")) {
                        baselineFMeasureCounts = fMeasureCounts;
                    }
                }
                
                
                    
                for (FMeasureCounts f : FMeasureCounts.byF1Ordering().reverse().immutableSortedCopy(allFMeasureCountsMap.keySet())) {
                    writer.write(row(allFMeasureCountsMap.get(f), f, baselineFMeasureCounts));
                }

                writer.write(tableEnd());
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

    private static String buildDescriptionTable(Map<String, String> systemDescriptions) {
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
