package net.masterthought.jenkins;

import com.google.gson.Gson;
import net.masterthought.jenkins.json.Element;
import net.masterthought.jenkins.json.Feature;
import net.masterthought.jenkins.json.Step;
import net.masterthought.jenkins.json.Util;
import net.masterthought.jenkins.util.UnzipUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeatureReportGenerator {

    private Map<String, List<Feature>> jsonResultFiles;
    private File reportDirectory;
    private String buildNumber;
    private String buildProject;
    private List<Util.Status> totalSteps;
    private String pluginUrlPath;
    private List<Feature> allFeatures;
    private List<TagObject> allTags;
    private static final String charEncoding = "UTF-8";
    private int totalScenarioSkipped;
    private int totalScenarioPassed;
    private int totalScenarioFailed;

    public FeatureReportGenerator(List<String> jsonResultFiles, File reportDirectory, String pluginUrlPath, String buildNumber, String buildProject, boolean skippedFails, boolean undefinedFails) throws IOException {
        ConfigurationOptions.setSkippedFailsBuild(skippedFails);
        ConfigurationOptions.setUndefinedFailsBuild(undefinedFails);
        this.jsonResultFiles = parseJsonResults(jsonResultFiles);
        this.allFeatures = listAllFeatures();
        this.totalSteps = getAllStepStatuses();
        getTotalScenarioStatuses();
        this.reportDirectory = reportDirectory;
        this.buildNumber = buildNumber;
        this.buildProject = buildProject;
        this.pluginUrlPath = getPluginUrlPath(pluginUrlPath);
        this.allTags = findTagsInFeatures();
    }

    private void getTotalScenarioStatuses() {
        this.totalScenarioPassed = 0;
        this.totalScenarioFailed = 0;
        this.totalScenarioSkipped = 0;

        for (Feature feature : allFeatures) {
            if (Util.hasScenarios(feature)) {
                for (Element element : feature.getElements()) {
                    if (Util.hasSteps(element) && !element.isBackground() && !element.isOutline()) {
                        if (element.getStatus().equals(Util.Status.FAILED)) {
                            totalScenarioFailed++;
                        } else if (element.getStatus().equals(Util.Status.PASSED)) {
                            totalScenarioPassed++;
                        } else if (element.getStatus().equals(Util.Status.SKIPPED)) {
                            totalScenarioPassed++;
                        }
                    }
                }
            }
        }

    }

    public boolean getBuildStatus() {
        return !(getTotalFails() > 0);
    }

    private Map<String, List<Feature>> parseJsonResults(List<String> jsonResultFiles) throws IOException {
        Map<String, List<Feature>> featureResults = new HashMap<String, List<Feature>>();
        for (String jsonFile : jsonResultFiles) {
            String fileContent = U2U(Util.readFileAsString(jsonFile));
            Feature[] features = new Gson().fromJson(fileContent, Feature[].class);
            featureResults.put(jsonFile, Arrays.asList(features));
        }
        return featureResults;
    }

    public void generateReports() throws Exception {
        copyResources();
        generateFeatureReports();
        generateFeatureOverview();
        generateTagReports();
        generateTagOverview();
    }

    private void copyResources() throws IOException, URISyntaxException {
        final File tmpResourcesArchive = new File(FileUtils.getTempDirectory(), "theme.zip");

        InputStream resourceArchiveInputStream = FeatureReportGenerator.class.getResourceAsStream("themes/blue.zip");
        if (resourceArchiveInputStream == null) {
            resourceArchiveInputStream = FeatureReportGenerator.class.getResourceAsStream("/themes/blue.zip");
        }
        OutputStream resourceArchiveOutputStream = new FileOutputStream(tmpResourcesArchive);
        try {
            IOUtils.copy(resourceArchiveInputStream, resourceArchiveOutputStream);
        } finally {
            IOUtils.closeQuietly(resourceArchiveInputStream);
            IOUtils.closeQuietly(resourceArchiveOutputStream);
        }
        UnzipUtils.unzipToFile(tmpResourcesArchive, reportDirectory);
        FileUtils.deleteQuietly(tmpResourcesArchive);
    }

    public void generateFeatureOverview() throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.init(getProperties());
        Template featureOverview = ve.getTemplate("templates/featureOverview.vm");
        VelocityContext context = new VelocityContext();
        context.put("build_project", buildProject);
        context.put("build_number", buildNumber);
        context.put("features", allFeatures);
        context.put("total_features", getTotalFeatures());
        context.put("total_scenarios", getTotalScenarios());
        context.put("total_steps", getTotalSteps());
        context.put("total_passes", getTotalPasses());
        context.put("total_fails", getTotalFails());
        context.put("total_skipped", getTotalSkipped());
        context.put("total_pending", getTotalPending());
        context.put("total_scenario_passes", getTotalScenarioPasses());
        context.put("total_scenario_fails", getTotalScenarioFails());
        context.put("total_scenario_skipped", getTotalScenarioSkipped());
        context.put("time_stamp", timeStamp());
        context.put("total_duration", getTotalDuration());
        context.put("jenkins_base", pluginUrlPath);
        generateReport("feature-overview.html", featureOverview, context);
    }

    private int getTotalScenarioSkipped() {
        return totalScenarioSkipped;
    }

    private int getTotalScenarioFails() {
        return totalScenarioFailed;
    }

    private int getTotalScenarioPasses() {
        return totalScenarioPassed;
    }

    public void generateFeatureReports() throws Exception {
        Iterator it = jsonResultFiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            List<Feature> featureList = (List<Feature>) pairs.getValue();

            for (Feature feature : featureList) {
                VelocityEngine ve = new VelocityEngine();
                ve.init(getProperties());
                Template featureResult = ve.getTemplate("templates/featureReport.vm");
                VelocityContext context = new VelocityContext();
                context.put("feature", feature);
                context.put("report_status_colour", getReportStatusColour(feature));
                context.put("build_project", buildProject);
                context.put("build_number", buildNumber);
                context.put("scenarios", feature.getElements());
                context.put("time_stamp", timeStamp());
                context.put("jenkins_base", pluginUrlPath);
                generateReport(feature.getFileName(), featureResult, context);
            }
        }
    }

    public void generateTagReports() throws Exception {
        for (TagObject tagObject : allTags) {
            VelocityEngine ve = new VelocityEngine();
            ve.init(getProperties());
            Template featureResult = ve.getTemplate("templates/tagReport.vm");
            VelocityContext context = new VelocityContext();
            context.put("tag", tagObject);
            context.put("time_stamp", timeStamp());
            context.put("jenkins_base", pluginUrlPath);
            context.put("build_project", buildProject);
            context.put("build_number", buildNumber);
            context.put("report_status_colour", getTagReportStatusColour(tagObject));
            generateReport(tagObject.getTagName().replace("@", "").trim() + ".html", featureResult, context);

        }
    }

    public void generateTagOverview() throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.init(getProperties());
        Template featureOverview = ve.getTemplate("templates/tagOverview.vm");
        VelocityContext context = new VelocityContext();
        context.put("build_project", buildProject);
        context.put("build_number", buildNumber);
        context.put("tags", allTags);
        context.put("total_tags", getTotalTags());
        context.put("total_scenarios", getTotalTagScenarios());
        context.put("total_steps", getTotalTagSteps());
        context.put("total_passes", getTotalTagPasses());
        context.put("total_fails", getTotalTagFails());
        context.put("total_skipped", getTotalTagSkipped());
        context.put("total_pending", getTotalTagPending());
        context.put("total_scenario_passes", getTotalScenarioTagPasses());
        context.put("total_scenario_fails", getTotalScenarioTagFails());
        context.put("total_scenario_skipped", getTotalScenarioTagSkipped());
        context.put("total_duration", getTotalTagDuration());
        context.put("tagScenariosData", generateTagOverviewChartScenariosData());
        context.put("tagStepsData", generateTagOverviewChartStepsData());
        context.put("time_stamp", timeStamp());
        context.put("jenkins_base", pluginUrlPath);
        generateReport("tag-overview.html", featureOverview, context);
    }

    private List<TagObject> findTagsInFeatures() {
        List<TagObject> tagMap = new ArrayList<TagObject>();
        for (Feature feature : allFeatures) {
            List<ScenarioTag> scenarioList = new ArrayList<ScenarioTag>();

            if (feature.hasTags()) {
                for (Element scenario : feature.getElements()) {
                    scenarioList.add(new ScenarioTag(scenario, feature.getFileName()));
                    tagMap = createOrAppendToTagMap(tagMap, feature.getTagList(), scenarioList);
                }
            }
            if (Util.hasScenarios(feature)) {
                for (Element scenario : feature.getElements()) {
                    if (scenario.hasTags()) {
                        scenarioList = addScenarioUnlessExists(scenarioList, new ScenarioTag(scenario, feature.getFileName()));
                    }
                    tagMap = createOrAppendToTagMap(tagMap, scenario.getTagList(), scenarioList);
                }
            }
        }
        return tagMap;
    }

    private List<ScenarioTag> addScenarioUnlessExists(List<ScenarioTag> scenarioList, ScenarioTag scenarioTag) {
        boolean exists = false;
        for (ScenarioTag scenario : scenarioList) {
            if (scenario.getParentFeatureUri().equalsIgnoreCase(scenarioTag.getParentFeatureUri())
                    && scenario.getScenario().getName().equalsIgnoreCase(scenarioTag.getScenario().getName())) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            scenarioList.add(scenarioTag);
        }
        return scenarioList;
    }

    private List<TagObject> createOrAppendToTagMap(List<TagObject> tagMap, List<String> tagList, List<ScenarioTag> scenarioList) {
        for (String tag : tagList) {
            boolean exists = false;
            TagObject tagObj = null;
            for (TagObject tagObject : tagMap) {
                if (tagObject.getTagName().equalsIgnoreCase(tag)) {
                    exists = true;
                    tagObj = tagObject;
                    break;
                }
            }
            if (exists) {
                List<ScenarioTag> existingTagList = tagObj.getScenarios();
                for (ScenarioTag scenarioTag : scenarioList) {
                    existingTagList = addScenarioUnlessExists(existingTagList, scenarioTag);
                }
                tagMap.remove(tagObj);
                tagObj.setScenarios(existingTagList);
                tagMap.add(tagObj);
            } else {
                tagObj = new TagObject(tag, scenarioList);
                tagMap.add(tagObj);
            }
        }
        return tagMap;
    }

    private List<Feature> listAllFeatures() {
        List<Feature> allFeatures = new ArrayList<Feature>();
        Iterator it = jsonResultFiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            List<Feature> featureList = (List<Feature>) pairs.getValue();
            allFeatures.addAll(featureList);
        }
        return allFeatures;
    }

    private static final Pattern p = Pattern.compile("\\\\u\\s*([0-9(A-F|a-f)]{4})", Pattern.MULTILINE);

    public static String U2U(String s) {
        String res = s;
        Matcher m = p.matcher(res);
        while (m.find()) {
            res = res.replaceAll("\\" + m.group(0),
                    Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        return res;
    }

    private String getPluginUrlPath(String path) {
        return path.isEmpty() ? "/" : path;
    }

    private int getTotalSteps() {
        return totalSteps.size();
    }

    private int getTotalTagSteps() {
        int steps = 0;
        for (TagObject tag : allTags) {
            for (ScenarioTag scenarioTag : tag.getScenarios()) {
                Step[] stepList = scenarioTag.getScenario().getSteps();
                if (stepList != null && stepList.length != 0) {
                    steps += stepList.length;
                }
            }
        }
        return steps;
    }

    private String getTotalDuration() {
        Long duration = 0L;
        for (Feature feature : allFeatures) {
            if (Util.hasScenarios(feature)) {
                for (Element scenario : feature.getElements()) {
                    if (Util.hasSteps(scenario)) {
                        for (Step step : scenario.getSteps()) {
                            duration = duration + step.getDuration();
                        }
                    }
                }
            }
        }
        return Util.formatDuration(duration);
    }

    private String getTotalTagDuration() {
        Long duration = 0L;
        for (TagObject tagObject : allTags) {
            for (ScenarioTag scenario : tagObject.getScenarios()) {
                if (Util.hasSteps(scenario)) {
                    for (Step step : scenario.getScenario().getSteps()) {
                        duration = duration + step.getDuration();
                    }
                }
            }
        }
        return Util.formatDuration(duration);
    }

    private int getTotalPasses() {
        return Util.findStatusCount(totalSteps, Util.Status.PASSED);
    }

    private int getTotalFails() {
        return Util.findStatusCount(totalSteps, Util.Status.FAILED);
    }

    private int getTotalSkipped() {
        return Util.findStatusCount(totalSteps, Util.Status.SKIPPED);
    }

    private int getTotalPending() {
        return Util.findStatusCount(totalSteps, Util.Status.UNDEFINED);
    }

    private int getTotalTagPasses() {
        int passes = 0;
        for (TagObject tag : allTags) {
            passes += tag.getNumberOfPasses();
        }
        return passes;
    }

    private int getTotalTagFails() {
        int failed = 0;
        for (TagObject tag : allTags) {
            failed += tag.getNumberOfFailures();
        }
        return failed;
    }

    private int getTotalTagSkipped() {
        int skipped = 0;
        for (TagObject tag : allTags) {
            skipped += tag.getNumberOfSkipped();
        }
        return skipped;
    }

    private int getTotalTagPending() {
        int pending = 0;
        for (TagObject tag : allTags) {
            pending += tag.getNumberOfPending();
        }
        return pending;
    }

    private int getTotalScenarioTagPasses() {
        int passes = 0;
        for (TagObject tag : allTags) {
            passes += tag.getNumberOfPassesScenarios();
        }
        return passes;
    }

    private int getTotalScenarioTagFails() {
        int failed = 0;
        for (TagObject tag : allTags) {
            failed += tag.getNumberOfFailuresScenarios();
        }
        return failed;
    }

    private int getTotalScenarioTagSkipped() {
        int skipped = 0;
        for (TagObject tag : allTags) {
            skipped += tag.getNumberOfSkippedScenarios();
        }
        return skipped;
    }

    private List<Util.Status> getAllStepStatuses() {
        List<Util.Status> steps = new ArrayList<Util.Status>();
        for (Feature feature : allFeatures) {
            if (Util.hasScenarios(feature)) {
                for (Element scenario : feature.getElements()) {
                    if (Util.hasSteps(scenario) && !scenario.isOutline()) {
                        for (Step step : scenario.getSteps()) {
                            steps.add(step.getStatus());
                        }
                    }
                }
            }
        }
        return steps;
    }

    private int getTotalFeatures() {
        return allFeatures.size();
    }

    private int getTotalTags() {
        return allTags.size();
    }

    private int getTotalScenarios() {
        int scenarios = 0;
        for (Feature feature : allFeatures) {
            scenarios = scenarios + feature.getNumberOfScenarios();
        }
        return scenarios;
    }

    private int getTotalTagScenarios() {
        int scenarios = 0;
        for (TagObject tag : allTags) {
            scenarios = scenarios + tag.getScenarios().size();
        }
        return scenarios;
    }

    private void generateReport(String fileName, Template featureResult, VelocityContext context) throws Exception {
        Writer writer = new FileWriter(new File(reportDirectory, fileName));
        featureResult.merge(context, writer);
        writer.flush();
        writer.close();
    }

    private Properties getProperties() {
        Properties props = new Properties();
        props.setProperty("resource.loader", "class");
        props.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        return props;
    }

    private List<List<String>> generateTagOverviewChartScenariosData() {
        List<List<String>> tagOverviewData = new ArrayList<List<String>>();
        List<String> row;
        for (TagObject tagObject : allTags) {
            row = new ArrayList<String>(4);
            row.add(tagObject.getTagName());
            row.add(Integer.toString(tagObject.getNumberOfPassesScenarios()));
            row.add(Integer.toString(tagObject.getNumberOfFailuresScenarios()));
            row.add(Integer.toString(tagObject.getNumberOfSkippedScenarios()));
            tagOverviewData.add(row);
        }
        return tagOverviewData;
    }

    private List<List<String>> generateTagOverviewChartStepsData() {
        List<List<String>> tagOverviewData = new ArrayList<List<String>>();
        List<String> row;
        for (TagObject tagObject : allTags) {
            row = new ArrayList<String>(5);
            row.add(tagObject.getTagName());
            row.add(Integer.toString(tagObject.getNumberOfPasses()));
            row.add(Integer.toString(tagObject.getNumberOfFailures()));
            row.add(Integer.toString(tagObject.getNumberOfSkipped()));
            row.add(Integer.toString(tagObject.getNumberOfPending()));
            tagOverviewData.add(row);
        }
        return tagOverviewData;
    }

    private String getReportStatusColour(Feature feature) {
        return feature.getStatus() == Util.Status.PASSED ? "#C5D88A" : "#D88A8A";
    }

    private String getTagReportStatusColour(TagObject tag) {
        return tag.getStatus() == Util.Status.PASSED ? "#C5D88A" : "#D88A8A";
    }

    private String timeStamp() {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
    }

}

