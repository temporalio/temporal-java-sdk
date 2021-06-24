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
import static org.junit.Assume.assumeFalse;

import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.shared.SDKTestWorkflowRule;
import io.temporal.workflow.shared.TestWorkflows.TestWorkflow4;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public class GetVersionSameIdTest {

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestGetVersionSameId.class)
          .setWorkerFactoryOptions(
              WorkerFactoryOptions.newBuilder()
                  .setWorkflowHostLocalTaskQueueScheduleToStartTimeout(Duration.ZERO)
                  .build())
          .build();

  @Test
  public void testGetVersionSameId() {
    assumeFalse("skipping for docker tests", SDKTestWorkflowRule.useExternalService);

    TestWorkflow4 workflowStub =
        testWorkflowRule.newWorkflowStubTimeoutOptions(TestWorkflow4.class);
    workflowStub.execute();
  }

  public static class TestGetVersionSameId implements TestWorkflow4 {

    private boolean hasReplayed;

    @Override
    public boolean execute() {
      System.out.println("REPLAYING: " + Workflow.isReplaying());
      if (Workflow.isReplaying()) hasReplayed = true;
      // Test adding a version check in replay code.
      if (!Workflow.isReplaying()) {
        Workflow.getVersion("test_change", Workflow.DEFAULT_VERSION, 11);
        Workflow.sleep(Duration.ofMinutes(1));
      } else {
        int version1 = Workflow.getVersion("test_change", Workflow.DEFAULT_VERSION, 11);
        Workflow.sleep(Duration.ofMinutes(1));
        int version2 = Workflow.getVersion("test_change", Workflow.DEFAULT_VERSION, 11);

        assertEquals(11, version2);
        assertEquals(version1, version2);
      }
      return hasReplayed;
    }
  }
}
