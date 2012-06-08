package net.masterthought.jenkins;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Runner {

    public static void main(String[] args) throws Exception {
        File rd = new File("/Users/stamer/Documents/development/projects/CPM-AdminConsole/admin-console/target/cucumber-html-reports");
        List<String> list = new ArrayList<String>();
//        list.add("/Users/kings/.jenkins/jobs/aaaaa/builds/15/cucumber-html-reports/french.json");
//        list.add("/Users/kings/.jenkins/jobs/aaaaa/builds/15/cucumber-html-reports/co_cucumber.json");
//        list.add("/Users/kings/.jenkins/jobs/aaaaa/builds/15/cucumber-html-reports/ccp_cucumber.json");
//        list.add("/Users/kings/.jenkins/jobs/aaaaa/builds/15/cucumber-html-reports/ss_cucumber.json");
//        list.add("/Users/kings/.jenkins/jobs/cucumber-jvm/builds/7/cucumber-html-reports/cukes.json");
        list.add("/Users/stamer/Documents/development/projects/CPM-AdminConsole/admin-console/target/cucumber.json");

       FeatureReportGenerator featureReportGenerator = new FeatureReportGenerator(list,rd,"", null,"cucumber-jvm",false,true);
       featureReportGenerator.generateReports();
//       boolean result = featureReportGenerator.getBuildStatus();
//       System.out.println("status: " + result);

    }
}
