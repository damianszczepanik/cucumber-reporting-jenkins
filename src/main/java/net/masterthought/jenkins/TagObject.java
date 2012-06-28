package net.masterthought.jenkins;

import net.masterthought.jenkins.json.Element;
import net.masterthought.jenkins.json.Step;
import net.masterthought.jenkins.json.Util;

import java.util.ArrayList;
import java.util.List;


public class TagObject {
    private String tagName;
    private List<ScenarioTag> scenarios = new ArrayList<ScenarioTag>();
    private List<Element> elements = new ArrayList<Element>();
    private int numberOfScenarios = 0;
    private int numberOfStepsFailed = 0;
    private int numberOfStepsPassed = 0;
    private int numberOfStepsSkipped = 0;
    private int numberOfStepsPending = 0;
    private int numberOfScenariosFailed = 0;
    private int numberOfScenariosPassed = 0;
    private int numberOfSteps = 0;
    private Long durationOfSteps = 0L;

    public TagObject(String tagName, List<ScenarioTag> scenarios) {
        this.tagName = tagName;
        this.scenarios = scenarios;
        processTags();
    }

    private void processTags() {
        for (ScenarioTag scenarioTag : scenarios) {
            Element scenario = scenarioTag.getScenario();
            if (Util.hasSteps(scenario) && !scenario.isOutline()) {
                if (!scenario.isBackground()) {
                    addScenarioStatus(scenario.getStatus());
                    numberOfScenarios++;
                }
                for (Step step : scenario.getSteps()) {
                    addStepStatus(step.getStatus());
                    numberOfSteps++;
                    durationOfSteps += step.getDuration();
                }
            }
        }
    }

    private void addScenarioStatus(Util.Status status) {
        if (status.equals(Util.Status.FAILED)) {
            numberOfScenariosFailed++;
        } else if (status.equals(Util.Status.PASSED)) {
            numberOfScenariosPassed++;
        }
    }

    private void addStepStatus(Util.Status status) {
        if (status == null) {
            return;
        }
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

    public String getTagName() {
        return tagName;
    }

    public String getFileName() {
        return tagName.replace("@", "").trim() + ".html";
    }

    public List<ScenarioTag> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<ScenarioTag> scenarios) {
        this.scenarios = scenarios;
    }

    public List<Element> getElements() {
        return elements;
    }

    public void setElements(List<Element> elements) {
        this.elements = elements;
    }

    public int getNumberOfScenarios() {
        return numberOfScenarios;
    }

    public void setNumberOfScenarios(int numberOfScenarios) {
        this.numberOfScenarios = numberOfScenarios;
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

    public Util.Status getStatus() {
        if (numberOfScenariosFailed != 0) {
            return Util.Status.FAILED;
        }
        return Util.Status.PASSED;
    }

    public int getNumberOfSteps() {
        return numberOfSteps;
    }

    public void setNumberOfSteps(int numberOfSteps) {
        this.numberOfSteps = numberOfSteps;
    }

    public Long getDurationOfSteps() {
        return durationOfSteps;
    }

    public void setDurationOfSteps(Long durationOfSteps) {
        this.durationOfSteps = durationOfSteps;
    }

    public String getFormattedDuration() {
        return Util.formatDuration(getDurationOfSteps());
    }
}
