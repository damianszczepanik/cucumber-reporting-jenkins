package net.masterthought.jenkins;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Runner {

    public static void main(String[] args) throws Exception {
        File rd = new File("/Users/Shared/Jenkins/Home/jobs/Api\\ Transfer/builds/13/cucumber-html-reports/");
        List<String> list = new ArrayList<String>();
//        list.add("/Users/kings/.jenkins/jobs/cucumber-jvm/builds/7/cucumber-html-reports/cukes.json");
//        list.add("/Users/dpayne2/Workspace/api/api-transfer/target/cucumber.json");
//        list.add("/Users/dpayne2/Desktop/cumber.json");
        list.add("/Users/dpayne2/api-transfer-activity.json");
        list.add("/Users/dpayne2/api-preview-transfer.json");

       FeatureReportGenerator featureReportGenerator = new FeatureReportGenerator(list,rd,"", null,"cucumber-jvm",false,true);
       featureReportGenerator.generateReports();
       boolean result = featureReportGenerator.getBuildStatus();
       System.out.println("status: " + result);
    }
}
