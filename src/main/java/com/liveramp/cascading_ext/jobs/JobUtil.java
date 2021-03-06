package com.liveramp.cascading_ext.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.TIPStatus;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.mapred.TaskCompletionEvent;
import org.apache.hadoop.mapred.TaskReport;

import com.liveramp.commons.state.TaskFailure;
import com.liveramp.commons.state.TaskSummary;

public class JobUtil {

  private static int FAILURES_TO_QUERY = 3;
  private static int MILLIS_TO_SEARCH = 5000; //this will limit the length of searches when searchUntilFound is false

  public static TaskSummary getSummary(JobClient client, JobID id, boolean searchUntilFound) throws IOException {
    DescriptiveStatistics mapStats = getRuntimes(client.getMapTaskReports(id));
    DescriptiveStatistics reduceStats = getRuntimes(client.getReduceTaskReports(id));
    return new TaskSummary((long)mapStats.getMean(),
        (long)mapStats.getPercentile(50),
        (long)mapStats.getMax(),
        (long)mapStats.getMin(),
        (long)mapStats.getStandardDeviation(),
        (long)reduceStats.getMean(),
        (long)reduceStats.getPercentile(50),
        (long)reduceStats.getMax(),
        (long)reduceStats.getMin(),
        (long)reduceStats.getStandardDeviation(),
        getTaskFailures(client, id, searchUntilFound)
    );
  }


  private static List<TaskFailure> getTaskFailures(JobClient client, JobID id, boolean searchUntilFound) throws IOException {
    List<TaskFailure> jobFailures = new ArrayList<>();
    RunningJob job = client.getJob(id);

    long start = System.currentTimeMillis();

    int index = 0;
    TaskCompletionEvent[] events = job.getTaskCompletionEvents(index);
    ArrayList<TaskCompletionEvent> failures = new ArrayList<TaskCompletionEvent>();

    //this returns either nothing (if no task exceptions at all) or a subset of the exceptions from the first
    //index at which exceptions are found in the task completion events
    //we also limit the time spent on it, in case a completely successful job with too many tasks
    while (
        events.length > 0
            && failures.size() == 0
            && (searchUntilFound || System.currentTimeMillis() - start < MILLIS_TO_SEARCH)) {
      for (TaskCompletionEvent event : events) {
        if (event.getTaskStatus() == TaskCompletionEvent.Status.FAILED) {
          failures.add(event);
        }
      }
      index += 10; //hadoop is weird and caps the number of task events at 10 non-optionally
      events = job.getTaskCompletionEvents(index);
    }

    // We limit the number of potential logs being pulled since we don't want to spend forever on these queries
    if (failures.size() > 0) {
      Collections.shuffle(failures);
      for (int i = 0; i < FAILURES_TO_QUERY; i++) {
        TaskAttemptID taskAttemptID = failures.get(i).getTaskAttemptId();
        String[] fails = job.getTaskDiagnostics(taskAttemptID);
        for (String failure : fails) {
          jobFailures.add(new TaskFailure(taskAttemptID.toString(), failure));
        }
      }
    }
    return jobFailures;
  }


  private static DescriptiveStatistics getRuntimes(TaskReport[] reports) {
    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (TaskReport report : reports) {
      if (report != null) {
        if (report.getCurrentStatus() == TIPStatus.COMPLETE) {
          stats.addValue(report.getFinishTime() - report.getStartTime());
        }
      }
    }
    return stats;
  }


}
