package net.masterthought.jenkins;

import net.masterthought.jenkins.json.Element;
import net.masterthought.jenkins.json.Feature;
import net.masterthought.jenkins.json.Step;
import net.masterthought.jenkins.json.Tag;
import net.masterthought.jenkins.json.Util;
import org.apache.commons.collections.map.MultiValueMap;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: dpayne2
 * Date: 6/27/12
 * Time: 10:16 AM
 */
public class Project {

    private String name;
    private int numberOfFeatures = 0;
    private int numberOfScenarios = 0;
    private int numberOfStepsFailed = 0;
    private int numberOfStepsPassed = 0;
    private int numberOfStepsSkipped = 0;
    private int numberOfStepsPending = 0;
    private int numberOfScenariosFailed = 0;
    private int numberOfScenariosPassed = 0;
    private int numberOfScenariosSkipped = 0;
    private List<Feature> features;
    private List<TagObject> tags;
    private int numberOfSteps;
    private long durationOfSteps = 0L;
    private String fileName;
    private String parentUri;

    public Project(String jsonFile, List<Feature> features, String parentUri) {
        fileName = jsonFile;
        this.features = features;
        numberOfFeatures = features.size();
        getAllStatuses();
        this.parentUri = parentUri;
        name = getProjectName();
    }

    private List<TagObject> getAllTags(List<Feature> features) {
        List<TagObject> tagMap = new ArrayList<TagObject>();
        for (Feature feature : features) {
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

    public String getProjectUrl() {
        return parentUri + File.separator + getProjectFeatureUri();
    }

    public String getProjectFeatureUri() {
        return name + "-feature-overview.html";
    }

    public String getProjectTagUri() {
        return name + "-tag-overview.html";
    }

    public String getProjectName() {
        //remove directory path
        String retval = FileUtils.removePath(this.fileName);

        //remove extension
        return FileUtils.removeExtension(retval);
    }

    public String getFormattedDuration() {
        return Util.formatDuration(getDurationOfSteps());
    }

    private List<Util.Status> getAllStatuses() {
        List<Util.Status> steps = new ArrayList<Util.Status>();
        Map<Tag, List<ScenarioTag>> tagMap = new HashMap<Tag, List<ScenarioTag>>();
        for (Feature feature : features) {
            if (Util.hasScenarios(feature)) {
                for (Element scenario : feature.getElements()) {
                    if (Util.hasSteps(scenario) && !scenario.isOutline()) {
                        if (!scenario.isBackground()) {
                            addScenarioStatus(scenario.getStatus());
                            numberOfScenarios++;
                        }
                        for (Step step : scenario.getSteps()) {
                            steps.add(step.getStatus());
                            addStepStatus(step.getStatus());
                            numberOfSteps++;
                            durationOfSteps += step.getDuration();
                        }
                        List<Tag> tempTags = combineTagArrays(feature.getRawTags(), scenario.getRawTags());
                        if (tempTags.size() > 0) {
                            ScenarioTag scenarioTag = new ScenarioTag(scenario, feature.getFileName());
                            for (Tag tag : tempTags) {
                                if (tagMap.containsKey(tag)) {
                                    tagMap.get(tag).add(scenarioTag);
                                } else {
                                    List<ScenarioTag> values = new ArrayList<ScenarioTag>();
                                    values.add(scenarioTag);
                                    tagMap.put(tag, values);
                                }
                            }
                        }
                    }
                }
            }
        }
        tags = convertTagMapToTagObjectList(tagMap);
        return steps;
    }

    private List<Tag> combineTagArrays(Tag[] rawTags, Tag[] rawTags1) {
        if (rawTags == null) {
            rawTags = new Tag[0];
        }
        if (rawTags1 == null) {
            rawTags1 = new Tag[0];
        }
        HashSet<Tag> tagSet = new HashSet<Tag>();
        Collections.addAll(tagSet, rawTags);
        Collections.addAll(tagSet, rawTags1);
        List<Tag> retval = new ArrayList<Tag>();
        retval.addAll(tagSet);
        return retval;
    }

    private List<TagObject> convertTagMapToTagObjectList(Map<Tag, List<ScenarioTag>> tagMap) {
        List<TagObject> tagObjects = new ArrayList<TagObject>(tagMap.keySet().size());
        TagObject tagObject;
        for (Tag tag : tagMap.keySet()) {
            tagObject = new TagObject(tag.getName(), tagMap.get(tag));
            tagObjects.add(tagObject);
        }
        return tagObjects;
    }

    private void addScenarioStatus(Util.Status status) {
        if (status.equals(Util.Status.FAILED)) {
            numberOfScenariosFailed++;
        } else if (status.equals(Util.Status.PASSED)) {
            numberOfScenariosPassed++;
        } else if (status.equals(Util.Status.SKIPPED)) {
            numberOfScenariosSkipped++;
        }
    }

    private void addStepStatus(Util.Status status) {
        if (status.equals(Util.Status.FAILED)) {
            numberOfStepsFailed++;
        } else if (status.equals(Util.Status.PASSED)) {
            numberOfStepsPassed++;
        } else if (status.equals(Util.Status.SKIPPED)) {
            numberOfStepsSkipped++;
        } else if (status.equals(Util.Status.UNDEFINED)) {
            numberOfStepsPending++;
        }
    }

    public Util.Status getStatus() {
        if (numberOfScenariosFailed == 0) {
            return Util.Status.PASSED;
        }
        return Util.Status.FAILED;
    }

    public int getNumberOfStepsFailed() {
        return numberOfStepsFailed;
    }

    public void setNumberOfStepsFailed(int numberOfStepsFailed) {
        this.numberOfStepsFailed = numberOfStepsFailed;
    }

    public int getNumberOfStepsPassed() {
        return numberOfStepsPassed;
    }

    public void setNumberOfStepsPassed(int numberOfStepsPassed) {
        this.numberOfStepsPassed = numberOfStepsPassed;
    }

    public int getNumberOfStepsSkipped() {
        return numberOfStepsSkipped;
    }

    public void setNumberOfStepsSkipped(int numberOfStepsSkipped) {
        this.numberOfStepsSkipped = numberOfStepsSkipped;
    }

    public int getNumberOfStepsPending() {
        return numberOfStepsPending;
    }

    public void setNumberOfStepsPending(int numberOfStepsPending) {
        this.numberOfStepsPending = numberOfStepsPending;
    }

    public int getNumberOfScenariosFailed() {
        return numberOfScenariosFailed;
    }

    public void setNumberOfScenariosFailed(int numberOfScenariosFailed) {
        this.numberOfScenariosFailed = numberOfScenariosFailed;
    }

    public int getNumberOfScenariosPassed() {
        return numberOfScenariosPassed;
    }

    public void setNumberOfScenariosPassed(int numberOfScenariosPassed) {
        this.numberOfScenariosPassed = numberOfScenariosPassed;
    }

    public int getNumberOfScenariosSkipped() {
        return numberOfScenariosSkipped;
    }

    public void setNumberOfScenariosSkipped(int numberOfScenariosSkipped) {
        this.numberOfScenariosSkipped = numberOfScenariosSkipped;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    public int getNumberOfFeatures() {
        return numberOfFeatures;
    }

    public void setNumberOfFeatures(int numberOfFeatures) {
        this.numberOfFeatures = numberOfFeatures;
    }

    public int getNumberOfScenarios() {
        return numberOfScenarios;
    }

    public void setNumberOfScenarios(int numberOfScenarios) {
        this.numberOfScenarios = numberOfScenarios;
    }

    public int getNumberOfSteps() {
        return numberOfSteps;
    }

    public Long getDurationOfSteps() {
        return durationOfSteps;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setDurationOfSteps(Long durationOfSteps) {
        this.durationOfSteps = durationOfSteps;
    }

    public List<TagObject> getTags() {
        return tags;
    }
}
