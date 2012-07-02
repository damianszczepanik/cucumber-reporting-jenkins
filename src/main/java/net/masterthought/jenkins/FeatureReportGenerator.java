package net.masterthought.jenkins;

import com.google.gson.Gson;
import net.masterthought.jenkins.json.Element;
import net.masterthought.jenkins.json.Feature;
import net.masterthought.jenkins.json.Step;
import net.masterthought.jenkins.json.Util;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeatureReportGenerator {
    //todo: clean up this file, many methods can be removed or replaced with more efficient ones

    private static final Pattern p = Pattern.compile("\\\\u\\s*([0-9(A-F|a-f)]{4})", Pattern.MULTILINE);
    private File reportDirectory;
    private String buildNumber;
    private String buildProject;
    private String pluginUrlPath;
    private List<Project> allProjects;
    private static final String charEncoding = "UTF-8";
    private int totalScenarioPassed;
    private int totalScenarioFailed;
    private int totalFeatures = 0;
    private int totalSteps = 0;
    private static PrintStream log;

    public FeatureReportGenerator(List<String> jsonResultFiles, File reportDirectory, String pluginUrlPath,
                                  String buildNumber, String buildProject, boolean skippedFails, boolean undefinedFails,
                                  PrintStream logger) throws IOException {
        ConfigurationOptions.setSkippedFailsBuild(skippedFails);
        ConfigurationOptions.setUndefinedFailsBuild(undefinedFails);
        if (logger == null) {
            log = System.out;
        } else {
            FeatureReportGenerator.log = logger;
        }

        this.buildNumber = buildNumber;
        this.buildProject = buildProject;
        this.reportDirectory = reportDirectory;
        this.pluginUrlPath = getPluginUrlPath(pluginUrlPath);

        copyResources(reportDirectory);

        parseJsonResults(jsonResultFiles);

        this.allProjects = getAllProjects(jsonResultFiles);
        getTotalScenarioStatuses();
    }

    private static void copyResources(File reportDirectory) {
        try {
            log.println("Copying resources from \"" + FeatureReportGenerator.class.getResource("/themes/blue").toURI() + "\"to " + reportDirectory.getAbsolutePath());
            FileUtils.copyDirectory(new File(FeatureReportGenerator.class.getResource("/themes/blue").toURI()), reportDirectory);
        } catch (IOException e) {
            log.println("Exception in copyResource: ");
            e.printStackTrace(log);
        } catch (URISyntaxException e) {
            log.println("Exception in copyResource: ");
            e.printStackTrace(log);
        }
    }

    private List<Project> getAllProjects(List<String> jsonResultFiles) throws IOException {
        List<Project> projects = new ArrayList<Project>();
        Project project;
        //todo: make sure project name has not already been taken
        for (String jsonFile : jsonResultFiles) {
            log.println("Parsing json file: " + jsonFile + "\n");
            String fileContent = U2U(Util.readFileAsString(jsonFile));
            Feature[] features = new Gson().fromJson(fileContent, Feature[].class);
            project = new Project(jsonFile, Arrays.asList(features), reportDirectory.getAbsolutePath());
            projects.add(project);
        }
        return projects;
    }

    public static String U2U(String s) {
        String res = s;
        Matcher m = p.matcher(res);
        while (m.find()) {
            res = res.replaceAll("\\" + m.group(0),
                    Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        return res;
    }


    private void getTotalScenarioStatuses() {
        this.totalScenarioPassed = 0;
        this.totalScenarioFailed = 0;
        this.totalFeatures = 0;
        this.totalSteps = 0;
        List<Feature> allFeatures;
        if (allProjects == null) {
            return;
        }
        for (Project project : allProjects) {
            allFeatures = project.getFeatures();
            for (Feature feature : allFeatures) {
                if (Util.hasScenarios(feature)) {
                    totalFeatures++;
                    for (Element element : feature.getElements()) {
                        if (Util.hasSteps(element) && !element.isOutline()) {
                            for (Step step : element.getSteps()) {
                                totalSteps++;
                            }
                            if (!element.isBackground()) {
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
        }
        log.println("Total Scenarios Passed: " + totalScenarioPassed);
        log.println("Total Scenarios Passed: " + totalScenarioFailed);
    }

    public boolean getBuildStatus() {
        return !(totalScenarioFailed > 0);
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
            generateReport(project.getName() + "-" + feature.getFileName(), featureResult, context);
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
            generateReport(project.getName() + "-" + tagObject.getTagName().replace("@", "").trim() + ".html", featureResult, context);

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
        List<List<Object>> scenarioData = generateTagOverviewChartScenariosData(project);
        context.put("tagScenariosData", scenarioData);
        context.put("numberOfTags", scenarioData.size());
        context.put("time_stamp", timeStamp());
        context.put("jenkins_base", pluginUrlPath);
        generateReport(project.getProjectTagUri(), featureOverview, context);
    }

    private void generateReport(String fileName, Template featureResult, VelocityContext context) throws Exception {
        Writer writer = new FileWriter(new File(reportDirectory, fileName));
        featureResult.merge(context, writer);
        writer.flush();
        writer.close();
    }


    private List<List<Object>> generateTagOverviewChartScenariosData(Project project) {
        List<List<Object>> tagOverviewData = new ArrayList<List<Object>>();
        List<Object> row;
        int i = 0;
        for (TagObject tagObject : project.getTags()) {
            row = new ArrayList<Object>(4);
            row.add(i);
            row.add(tagObject.getTagName());
            row.add(tagObject.getNumberOfScenariosPassed());
            row.add(tagObject.getNumberOfScenariosFailed());
            tagOverviewData.add(row);
            i++;
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

    private int getTotalScenarioFails() {
        return totalScenarioFailed;
    }

    private int getTotalScenarioPasses() {
        return totalScenarioPassed;
    }

    private int getTotalSteps() {
        return totalSteps;
    }

    private String getTotalDuration() {
        Long duration = 0L;

        List<Feature> allFeatures;
        for (Project project : allProjects) {
            allFeatures = project.getFeatures();
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
        }
        return Util.formatDuration(duration);
    }

    private int getTotalFeatures() {
        return totalFeatures;
    }

    private int getTotalScenarios() {
        int scenarios = 0;
        List<Feature> allFeatures;
        for (Project project : allProjects) {
            allFeatures = project.getFeatures();
            for (Feature feature : allFeatures) {
                scenarios = scenarios + feature.getNumberOfScenarios();
            }
        }
        return scenarios;
    }

    private String getPluginUrlPath(String path) {
        return path.isEmpty() ? "/" : path;
    }

    private Properties getProperties() {
        Properties props = new Properties();
        props.setProperty("resource.loader", "class");
        props.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        props.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem");
        return props;
    }

    private String timeStamp() {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
    }

}

