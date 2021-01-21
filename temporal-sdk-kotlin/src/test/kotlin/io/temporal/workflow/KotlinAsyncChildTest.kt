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

package io.temporal.workflow

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.client.WorkflowClientOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.internal.sync.AsyncInternal
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinAsyncChildTest {
    private var testEnvironment: TestWorkflowEnvironment? = null
    @Before
    fun setUp() {
        val mapper = ObjectMapper()
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        mapper.registerModule(JavaTimeModule())
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        mapper.registerModule(KotlinModule())

        val converter = JacksonJsonPayloadConverter(mapper)
        val workflowClientOptions = WorkflowClientOptions.newBuilder().setDataConverter(DefaultDataConverter(converter)).build()
        val options = TestEnvironmentOptions.newBuilder().setWorkflowClientOptions(workflowClientOptions).build()
        testEnvironment = TestWorkflowEnvironment.newInstance(options)
    }

    @After
    fun tearDown() {
        testEnvironment!!.close()
    }

    @WorkflowInterface
    interface ChildWorkflow {
        @WorkflowMethod
        fun execute(): Int
    }

    class ChildWorkflowImpl : ChildWorkflow {
        override fun execute(): Int {
            return 0
        }
    }

    @WorkflowInterface
    interface NaiveParentWorkflow {
        @WorkflowMethod
        fun execute()
    }

    class NaiveParentWorkflowImpl : NaiveParentWorkflow {
        override fun execute() {
            val childWorkflow = Workflow.newChildWorkflowStub(ChildWorkflow::class.java)
            assertTrue("This has to be true to make " +
                    "Async.function(dangerousChildWorkflow::execute) work correctly",
                AsyncInternal.isAsync(childWorkflow::execute))
            Async.function(childWorkflow::execute)
        }
    }

    @Test
    fun asyncChildWorkflowTest() {
        //Async works correctly and even while child workflows have problems with parameters deserialization
        // (because kotlin jackson module is not registered) - the main workflows finish just fine
        val worker = testEnvironment!!.newWorker(TASK_QUEUE)
        worker.registerWorkflowImplementationTypes(
                NaiveParentWorkflowImpl::class.java, ChildWorkflowImpl::class.java)
        testEnvironment!!.start()
        val client = testEnvironment!!.workflowClient
        for (i in 0..10) {
            val options = WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build()
            val workflowStub = client.newWorkflowStub(NaiveParentWorkflow::class.java, options)
            workflowStub.execute()
        }
    }

    companion object {
        private const val TASK_QUEUE = "test-workflow"
    }
}