/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
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

package io.temporal.workflow.versionTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.client.WorkflowStub;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.shared.SDKTestWorkflowRule;
import io.temporal.workflow.shared.TestWorkflows.TestWorkflow4;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public class GetVersionSameIdOnReplayTest {

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestGetVersionSameIdOnReplay.class)
          .setWorkerFactoryOptions(
              WorkerFactoryOptions.newBuilder()
                  .setWorkflowHostLocalTaskQueueScheduleToStartTimeout(Duration.ZERO)
                  .build())
          .build();

  @Test
  public void testGetVersionSameIdOnReplay() {
    assumeFalse("skipping for docker tests", SDKTestWorkflowRule.useExternalService);

    TestWorkflow4 workflowStub =
        testWorkflowRule.newWorkflowStubTimeoutOptions(TestWorkflow4.class);
    assertTrue(workflowStub.execute());
    WorkflowExecution execution = WorkflowStub.fromTyped(workflowStub).getExecution();
    testWorkflowRule.assertNoHistoryEvent(execution, EventType.EVENT_TYPE_MARKER_RECORDED);
  }

  public static class TestGetVersionSameIdOnReplay implements TestWorkflow4 {

    private boolean hasReplayed;

    @Override
    public boolean execute() {
      if (Workflow.isReplaying()) hasReplayed = true;
      // Test adding a version check in replay code.
      if (!Workflow.isReplaying()) {
        Workflow.sleep(Duration.ofMinutes(1));
      } else {
        int version2 = Workflow.getVersion("test_change_2", Workflow.DEFAULT_VERSION, 11);
        Workflow.sleep(Duration.ofMinutes(1));
        int version3 = Workflow.getVersion("test_change_2", Workflow.DEFAULT_VERSION, 11);

        assertEquals(Workflow.DEFAULT_VERSION, version3);
        assertEquals(version2, version3);
      }

      return hasReplayed;
    }
  }
}
