/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.internal.worker;

import com.uber.m3.tally.Stopwatch;
import com.uber.m3.util.Duration;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.PollForActivityTaskRequest;
import io.temporal.PollForActivityTaskResponse;
import io.temporal.TaskList;
import io.temporal.TaskListMetadata;
import io.temporal.internal.metrics.MetricsType;
import io.temporal.serviceclient.GrpcWorkflowServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ActivityPollTask implements Poller.PollTask<ActivityWorker.MeasurableActivityTask> {

  private final GrpcWorkflowServiceFactory service;
  private final String domain;
  private final String taskList;
  private final SingleWorkerOptions options;
  private static final Logger log = LoggerFactory.getLogger(ActivityPollTask.class);

  public ActivityPollTask(
      GrpcWorkflowServiceFactory service,
      String domain,
      String taskList,
      SingleWorkerOptions options) {

    this.service = service;
    this.domain = domain;
    this.taskList = taskList;
    this.options = options;
  }

  @Override
  public ActivityWorker.MeasurableActivityTask poll() {
    options.getMetricsScope().counter(MetricsType.ACTIVITY_POLL_COUNTER).inc(1);
    Stopwatch sw = options.getMetricsScope().timer(MetricsType.ACTIVITY_POLL_LATENCY).start();
    Stopwatch e2eSW = options.getMetricsScope().timer(MetricsType.ACTIVITY_E2E_LATENCY).start();

    PollForActivityTaskRequest.Builder pollRequest =
        PollForActivityTaskRequest.newBuilder()
            .setDomain(domain)
            .setIdentity(options.getIdentity())
            .setTaskList(TaskList.newBuilder().setName(taskList).build());

    if (options.getTaskListActivitiesPerSecond() > 0) {
      TaskListMetadata metadata =
          TaskListMetadata.newBuilder()
              .setMaxTasksPerSecond(options.getTaskListActivitiesPerSecond())
              .build();
      pollRequest.setTaskListMetadata(metadata);
    }

    if (log.isDebugEnabled()) {
      log.debug("poll request begin: " + pollRequest);
    }
    PollForActivityTaskRequest request = pollRequest.build();
    PollForActivityTaskResponse result;
    try {
      result = service.blockingStub().pollForActivityTask(request);
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode().equals(Status.Code.INTERNAL)
          || e.getStatus().getCode().equals(Status.Code.RESOURCE_EXHAUSTED)) {
        options
            .getMetricsScope()
            .counter(MetricsType.ACTIVITY_POLL_TRANSIENT_FAILED_COUNTER)
            .inc(1);
        throw e;
      } else {
        options.getMetricsScope().counter(MetricsType.ACTIVITY_POLL_FAILED_COUNTER).inc(1);
        throw e;
      }
    }

    if (result == null || result.getTaskToken() == null) {
      if (log.isDebugEnabled()) {
        log.debug("poll request returned no task");
      }
      options.getMetricsScope().counter(MetricsType.ACTIVITY_POLL_NO_TASK_COUNTER).inc(1);
      return null;
    }

    if (log.isTraceEnabled()) {
      log.trace("poll request returned " + result);
    }

    options.getMetricsScope().counter(MetricsType.ACTIVITY_POLL_SUCCEED_COUNTER).inc(1);
    options
        .getMetricsScope()
        .timer(MetricsType.ACTIVITY_SCHEDULED_TO_START_LATENCY)
        .record(
            Duration.ofNanos(
                result.getStartedTimestamp() - result.getScheduledTimestampOfThisAttempt()));
    sw.stop();
    return new ActivityWorker.MeasurableActivityTask(result, e2eSW);
  }
}
