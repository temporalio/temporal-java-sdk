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

package io.temporal.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.temporal.api.common.v1.SearchAttributes;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.internal.common.SearchAttributesUtil;
import io.temporal.testing.TracingWorkerInterceptor;
import io.temporal.workflow.shared.SDKTestWorkflowRule;
import io.temporal.workflow.shared.TestActivities;
import io.temporal.workflow.shared.TestOptions;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class UpsertSearchAttributesTest {

  private final TestActivities.TestActivitiesImpl activitiesImpl =
      new TestActivities.TestActivitiesImpl();

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestUpsertSearchAttributesImpl.class)
          .setActivityImplementations(activitiesImpl)
          .setWorkerInterceptors(
              new TracingWorkerInterceptor(new TracingWorkerInterceptor.FilteredTrace()))
          .build();

  @Test
  public void testUpsertSearchAttributes() {
    TestUpsertSearchAttributes testWorkflow =
        testWorkflowRule.newWorkflowStubTimeoutOptions(TestUpsertSearchAttributes.class);
    WorkflowExecution execution =
        WorkflowClient.start(testWorkflow::execute, testWorkflowRule.getTaskQueue(), "testKey");
    String result = testWorkflow.execute(testWorkflowRule.getTaskQueue(), "testKey");
    Assert.assertEquals("done", result);
    testWorkflowRule
        .getInterceptor(TracingWorkerInterceptor.class)
        .setExpected(
            "interceptExecuteWorkflow " + SDKTestWorkflowRule.UUID_REGEXP,
            "newThread workflow-method",
            "upsertSearchAttributes",
            "executeActivity Activity",
            "activity Activity");
    GetWorkflowExecutionHistoryRequest request =
        GetWorkflowExecutionHistoryRequest.newBuilder()
            .setNamespace(SDKTestWorkflowRule.NAMESPACE)
            .setExecution(execution)
            .build();
    GetWorkflowExecutionHistoryResponse response =
        testWorkflowRule
            .getTestEnvironment()
            .getWorkflowService()
            .blockingStub()
            .getWorkflowExecutionHistory(request);

    boolean found = false;
    for (HistoryEvent event : response.getHistory().getEventsList()) {
      if (EventType.EVENT_TYPE_UPSERT_WORKFLOW_SEARCH_ATTRIBUTES == event.getEventType()) {
        found = true;
        break;
      }
    }
    Assert.assertTrue("EVENT_TYPE_UPSERT_WORKFLOW_SEARCH_ATTRIBUTES found in the history", found);
  }

  @WorkflowInterface
  public interface TestUpsertSearchAttributes {
    @WorkflowMethod
    String execute(String taskQueue, String keyword);
  }

  public static class TestUpsertSearchAttributesImpl implements TestUpsertSearchAttributes {

    @Override
    public String execute(String taskQueue, String keyword) {
      SearchAttributes searchAttributes = Workflow.getInfo().getSearchAttributes();
      assertNull(searchAttributes);

      Map<String, Object> searchAttrMap = new HashMap<>();
      searchAttrMap.put("CustomKeywordField", keyword);
      Workflow.upsertSearchAttributes(searchAttrMap);

      searchAttributes = Workflow.getInfo().getSearchAttributes();
      assertEquals(
          "testKey",
          SearchAttributesUtil.getValueFromSearchAttributes(
              searchAttributes, "CustomKeywordField", String.class));

      // Running the activity below ensures that we have one more workflow task to be executed after
      // adding the search attributes. This helps with replaying the history one more time to check
      // against a possible NonDeterminisicWorkflowError which could be caused by missing
      // UpsertWorkflowSearchAttributes event in history.
      TestActivities activities =
          Workflow.newActivityStub(
              TestActivities.class, TestOptions.newActivityOptionsForTaskQueue(taskQueue));
      activities.activity();

      return "done";
    }
  }
}