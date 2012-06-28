package net.masterthought.jenkins;

import com.google.gson.Gson;
import net.masterthought.jenkins.json.Element;
import net.masterthought.jenkins.json.Feature;
import net.masterthought.jenkins.json.Step;
import net.masterthought.jenkins.json.Util;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
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
    //todo: clean up this file, many methods can be removed or replaced with more efficient ones

    private Map<String, List<Feature>> jsonResultFiles;
    private File reportDirectory;
    private String buildNumber;
    private String buildProject;
    private List<Util.Status> totalSteps;
    private String pluginUrlPath;
    private List<Feature> allFeatures;
    private List<TagObject> allTags;
    private List<Project> allProjects;
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
        this.allProjects = getAllProjects(jsonResultFiles);
    }

    private List<Project> getAllProjects(List<String> jsonResultFiles) throws IOException {
        List<Project> projects = new ArrayList<Project>();
        Project project;
        //todo: make sure project name has not already been taken
        for (String jsonFile : jsonResultFiles) {
            String fileContent = U2U(Util.readFileAsString(jsonFile));
            Feature[] features = new Gson().fromJson(fileContent, Feature[].class);
            project = new Project(jsonFile, Arrays.asList(features), reportDirectory.getAbsolutePath());
            projects.add(project);
        }
        return projects;
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
        generateProjectOverview();
        for (Project project : allProjects) {
            generateFeatureOverview(project);
            generateTagOverview(project);
            generateFeatureReports(project);
            generateTagReports(project);
        }
    }

    private void generateProjectOverview() throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.init(getProperties());
        Template featureOverview = ve.getTemplate("templates/projectOverview.vm");
        VelocityContext context = new VelocityContext();
        context.put("build_project", buildProject);
        context.put("build_number", buildNumber);
        context.put("projects", allProjects);
        context.put("total_projects", allProjects.size());
        context.put("total_features", getTotalFeatures());
        context.put("total_scenarios", getTotalScenarios());
        context.put("total_steps", getTotalSteps());
        context.put("total_passes", getTotalPasses());
        context.put("total_fails", getTotalFails());
        context.put("total_skipped", getTotalSkipped());
        context.put("total_pending", getTotalPending());
        context.put("total_scenario_passes", getTotalScenarioPasses());
        context.put("total_scenario_fails", getTotalScenarioFails());
        context.put("time_stamp", timeStamp());
        context.put("total_duration", getTotalDuration());
        context.put("jenkins_base", pluginUrlPath);
        generateReport("project-overview.html", featureOverview, context);
    }

    public void generateFeatureOverview(Project project) throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.init(getProperties());
        Template featureOverview = ve.getTemplate("templates/featureOverview.vm");
        VelocityContext context = new VelocityContext();
        context.put("build_project", buildProject);
        context.put("build_number", buildNumber);
        context.put("project", project);
        context.put("features", project.getFeatures());
        context.put("total_features", project.getNumberOfFeatures());
        context.put("total_scenarios", project.getNumberOfScenarios());
        context.put("total_steps", project.getNumberOfSteps());
        context.put("total_passes", project.getNumberOfStepsPassed());
        context.put("total_fails", project.getNumberOfStepsFailed());
        context.put("total_skipped", project.getNumberOfStepsSkipped());
        context.put("total_pending", project.getNumberOfStepsPending());
        context.put("total_scenario_passes", project.getNumberOfScenariosPassed());
        context.put("total_scenario_fails", project.getNumberOfScenariosFailed());
        context.put("time_stamp", timeStamp());
        context.put("total_duration", project.getFormattedDuration());
        context.put("jenkins_base", pluginUrlPath);
        generateReport(project.getProjectFeatureUri(), featureOverview, context);
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

    public void generateFeatureReports(Project project) throws Exception {
        for (Feature feature : project.getFeatures()) {
            VelocityEngine ve = new VelocityEngine();
            ve.init(getProperties());
            Template featureResult = ve.getTemplate("templates/featureReport.vm");
            VelocityContext context = new VelocityContext();
            context.put("feature", feature);
            context.put("project", project);
            context.put("report_status_colour", getReportStatusColour(feature));
            context.put("build_project", buildProject);
            context.put("build_number", buildNumber);
            context.put("scenarios", feature.getElements());
            context.put("time_stamp", timeStamp());
            context.put("jenkins_base", pluginUrlPath);
            generateReport(project.getName()+"-"+feature.getFileName(), featureResult, context);
        }
    }

    public void generateTagReports(Project project) throws Exception {
        for (TagObject tagObject : project.getTags()) {
            VelocityEngine ve = new VelocityEngine();
            ve.init(getProperties());
            Template featureResult = ve.getTemplate("templates/tagReport.vm");
            VelocityContext context = new VelocityContext();
            context.put("tag", tagObject);
            context.put("project", project);
            context.put("time_stamp", timeStamp());
            context.put("jenkins_base", pluginUrlPath);
            context.put("build_project", buildProject);
            context.put("build_number", buildNumber);
            context.put("report_status_colour", getTagReportStatusColour(tagObject));
            generateReport(project.getName()+"-"+tagObject.getTagName().replace("@", "").trim() + ".html", featureResult, context);

        }
    }

    public void generateTagOverview(Project project) throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.init(getProperties());
        Template featureOverview = ve.getTemplate("templates/tagOverview.vm");
        VelocityContext context = new VelocityContext();
        context.put("project", project);
        context.put("build_project", buildProject);
        context.put("build_number", buildNumber);
        context.put("tags", project.getTags());
        context.put("total_tags", project.getTags().size());
        context.put("total_scenarios", project.getNumberOfScenarios());
        context.put("total_steps", project.getNumberOfSteps());
        context.put("total_passes", project.getNumberOfStepsPassed());
        context.put("total_fails", project.getNumberOfStepsFailed());
        context.put("total_skipped", project.getNumberOfStepsSkipped());
        context.put("total_pending", project.getNumberOfStepsPending());
        context.put("total_scenario_passes", project.getNumberOfScenariosPassed());
        context.put("total_scenario_fails", project.getNumberOfScenariosFailed());
        context.put("total_duration", project.getFormattedDuration());
        context.put("tagScenariosData", generateTagOverviewChartScenariosData(project));
        context.put("tagStepsData", generateTagOverviewChartStepsData(project));
        context.put("time_stamp", timeStamp());
        context.put("jenkins_base", pluginUrlPath);
        generateReport(project.getProjectTagUri(), featureOverview, context);
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
            passes += tag.getNumberOfStepsPassed();
        }
        return passes;
    }

    private int getTotalTagFails() {
        int failed = 0;
        for (TagObject tag : allTags) {
            failed += tag.getNumberOfStepsFailed();
        }
        return failed;
    }

    private int getTotalTagSkipped() {
        int skipped = 0;
        for (TagObject tag : allTags) {
            skipped += tag.getNumberOfStepsSkipped();
        }
        return skipped;
    }

    private int getTotalTagPending() {
        int pending = 0;
        for (TagObject tag : allTags) {
            pending += tag.getNumberOfStepsPending();
        }
        return pending;
    }

    private int getTotalScenarioTagPasses() {
        int passes = 0;
        for (TagObject tag : allTags) {
            passes += tag.getNumberOfScenariosPassed();
        }
        return passes;
    }

    private int getTotalScenarioTagFails() {
        int failed = 0;
        for (TagObject tag : allTags) {
            failed += tag.getNumberOfScenariosFailed();
        }
        return failed;
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

    private List<List<String>> generateTagOverviewChartScenariosData(Project project) {
        List<List<String>> tagOverviewData = new ArrayList<List<String>>();
        List<String> row;
        for (TagObject tagObject : project.getTags()) {
            row = new ArrayList<String>(3);
            row.add(tagObject.getTagName());
            row.add(Integer.toString(tagObject.getNumberOfScenariosPassed()));
            row.add(Integer.toString(tagObject.getNumberOfScenariosFailed()));
            tagOverviewData.add(row);
        }
        return tagOverviewData;
    }

    private List<List<String>> generateTagOverviewChartStepsData(Project project) {
        List<List<String>> tagOverviewData = new ArrayList<List<String>>();
        List<String> row;
        for (TagObject tagObject : project.getTags()) {
            row = new ArrayList<String>(5);
            row.add(tagObject.getTagName());
            row.add(Integer.toString(tagObject.getNumberOfStepsPassed()));
            row.add(Integer.toString(tagObject.getNumberOfStepsFailed()));
            row.add(Integer.toString(tagObject.getNumberOfStepsSkipped()));
            row.add(Integer.toString(tagObject.getNumberOfStepsPending()));
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

