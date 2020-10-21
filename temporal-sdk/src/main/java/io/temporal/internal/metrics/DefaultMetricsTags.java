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

package io.temporal.internal.metrics;

import static io.temporal.internal.metrics.MetricsTag.NAMESPACE;

import com.uber.m3.util.ImmutableMap;
import java.util.Map;

public class DefaultMetricsTags {

  public static final String DEFAULT_VALUE = "none";

  public static Map<String, String> defaultTags(String namespace) {
    return new ImmutableMap.Builder<String, String>(9)
        .put(NAMESPACE, namespace)
        .put(MetricsTag.ACTIVITY_TYPE, DEFAULT_VALUE)
        .put(MetricsTag.OPERATION_NAME, DEFAULT_VALUE)
        .put(MetricsTag.SIGNAL_NAME, DEFAULT_VALUE)
        .put(MetricsTag.QUERY_TYPE, DEFAULT_VALUE)
        .put(MetricsTag.TASK_QUEUE, DEFAULT_VALUE)
        .put(MetricsTag.STATUS_CODE, DEFAULT_VALUE)
        .put(MetricsTag.EXCEPTION, DEFAULT_VALUE)
        .put(MetricsTag.WORKFLOW_TYPE, DEFAULT_VALUE)
        .build();
  }
}
