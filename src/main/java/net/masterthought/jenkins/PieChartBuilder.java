package net.masterthought.jenkins;

import java.util.List;

import net.masterthought.jenkins.json.ChartItem;

import com.google.common.collect.Lists;
import com.google.gson.Gson;


public class PieChartBuilder {

   /**
    * Create the data for the Pie chart.
    * 
    * @see https://gist.github.com/1203641 for pie chart example with label
    * @see http://bl.ocks.org/1346395 for simple pie chart example
    */
   public static String build(int total_passed, int total_failed, int total_skipped, int total_pending) {
      List<ChartItem> chartItems = Lists.newArrayList();
      chartItems.add(new ChartItem("Passed", total_passed));
      chartItems.add(new ChartItem("Failed", total_failed));
      chartItems.add(new ChartItem("Skipped", total_skipped));
      chartItems.add(new ChartItem("Pending", total_pending));
      
      Gson gson = new Gson();
      return gson.toJson(chartItems);
   }

}
