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

package io.temporal.internal.common.kotlin;

import java.lang.reflect.Field;
import kotlin.jvm.internal.CallableReference;

/**
 * This class uses kotlin-specific classes and before it's used - {@link KotlinDetector} should be
 * used that gives a gentle check for kotlin presence without requiring presence of Kotlin classes
 */
public abstract class KotlinUtils {
  public static Object getLambdaTarget(Object lambda) {
    try {
      CallableReference callableReference = null;
      if (lambda instanceof CallableReference) {
        callableReference = (CallableReference) lambda;
      } else {
        /* Here we unwrap our io.temporal.workflow.Functions */
        try {
          Field functionField = lambda.getClass().getDeclaredField("function");
          functionField.setAccessible(true);
          Object function = functionField.get(lambda);
          if (function instanceof CallableReference) {
            callableReference = (CallableReference) function;
          }
        } catch (Exception e) {
          // should we report a problem here somehow at least with a logging,
          // that it's Kotlin and both ways of resolution failed?
        }
      }
      return callableReference != null ? callableReference.getBoundReceiver() : null;
    } catch (Exception e) {
      return null;
    }
  }
}
