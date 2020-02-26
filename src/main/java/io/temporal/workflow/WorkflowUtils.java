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

package io.temporal.workflow;

import com.cronutils.utils.StringUtils;
import io.temporal.SearchAttributes;
import io.temporal.converter.DataConverter;
import io.temporal.converter.JsonDataConverter;

public class WorkflowUtils {
  private static final DataConverter jsonConverter = JsonDataConverter.getInstance();

  public static <T> T getValueFromSearchAttributes(
      SearchAttributes searchAttributes, String key, Class<T> classType) {
    if (searchAttributes == null || StringUtils.isEmpty(key)) {
      return null;
    }
    return jsonConverter.fromData(getValueBytes(searchAttributes, key), classType, classType);
  }

  private static byte[] getValueBytes(SearchAttributes searchAttributes, String key) {
    return searchAttributes.getIndexedFieldsMap().get(key).toByteArray();
  }
}
