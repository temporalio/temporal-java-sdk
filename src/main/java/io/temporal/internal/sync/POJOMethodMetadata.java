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

package io.temporal.internal.sync;

import io.temporal.activity.ActivityMethod;
import java.lang.reflect.Method;
import java.util.Objects;

public class POJOMethodMetadata {
  private final boolean hasActivityMethodAnnotation;
  private final String name;
  private final Method method;
  private final Class<?> interfaceType;

  POJOMethodMetadata(Method method, Class<?> interfaceType) {
    this.method = Objects.requireNonNull(method);
    this.interfaceType = Objects.requireNonNull(interfaceType);
    ActivityMethod activityMethod = method.getAnnotation(ActivityMethod.class);
    String name;
    if (activityMethod != null) {
      hasActivityMethodAnnotation = true;
      name = activityMethod.name();
    } else {
      hasActivityMethodAnnotation = false;
      name = interfaceType.getSimpleName() + "_" + method.getName();
    }
    this.name = name;
  }

  public boolean isHasActivityMethodAnnotation() {
    return hasActivityMethodAnnotation;
  }

  public String getName() {
    if (name == null) {
      throw new IllegalStateException("Not annotated");
    }
    return name;
  }

  public Method getMethod() {
    return method;
  }

  /** Compare and hash based on method and the interface type only. */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    POJOMethodMetadata that = (POJOMethodMetadata) o;
    return com.google.common.base.Objects.equal(method, that.method)
        && com.google.common.base.Objects.equal(interfaceType, that.interfaceType);
  }

  /** Compare and hash based on method and the interface type only. */
  @Override
  public int hashCode() {
    return com.google.common.base.Objects.hashCode(method, interfaceType);
  }
}