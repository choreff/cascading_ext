package com.liveramp.cascading_ext.flow;

import cascading.flow.Flow;
import cascading.flow.FlowDef;
import cascading.flow.FlowStepStrategy;
import cascading.flow.hadoop.planner.HadoopPlanner;
import org.apache.hadoop.mapred.JobConf;

public class LoggingHadoopPlanner extends HadoopPlanner {
  private final FlowStepStrategy<JobConf> flowStepStrategy;

  public LoggingHadoopPlanner(FlowStepStrategy<JobConf> flowStepStrategy) {
    super();
    this.flowStepStrategy = flowStepStrategy;
  }

  @Override
  public Flow buildFlow(FlowDef flowDef) {
    Flow internalFlow = super.buildFlow(flowDef);
    internalFlow.setFlowStepStrategy(flowStepStrategy);
    return new LoggingFlow(internalFlow);
  }
}
