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

package io.temporal.internal.external;

import com.google.protobuf.ByteString;
import com.uber.m3.tally.Scope;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.ActivityCancelledException;
import io.temporal.client.ActivityCompletionFailureException;
import io.temporal.client.ActivityNotExistsException;
import io.temporal.converter.DataConverter;
import io.temporal.internal.common.OptionsUtils;
import io.temporal.internal.metrics.MetricsType;
import io.temporal.proto.common.WorkflowExecution;
import io.temporal.proto.workflowservice.RecordActivityTaskHeartbeatByIDRequest;
import io.temporal.proto.workflowservice.RecordActivityTaskHeartbeatByIDResponse;
import io.temporal.proto.workflowservice.RecordActivityTaskHeartbeatRequest;
import io.temporal.proto.workflowservice.RecordActivityTaskHeartbeatResponse;
import io.temporal.proto.workflowservice.RespondActivityTaskCanceledByIDRequest;
import io.temporal.proto.workflowservice.RespondActivityTaskCanceledRequest;
import io.temporal.proto.workflowservice.RespondActivityTaskCompletedByIDRequest;
import io.temporal.proto.workflowservice.RespondActivityTaskCompletedRequest;
import io.temporal.proto.workflowservice.RespondActivityTaskFailedByIDRequest;
import io.temporal.proto.workflowservice.RespondActivityTaskFailedRequest;
import io.temporal.serviceclient.GrpcRetryer;
import io.temporal.serviceclient.GrpcWorkflowServiceFactory;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: service call retries
class ManualActivityCompletionClientImpl extends ManualActivityCompletionClient {

  private static final Logger log =
      LoggerFactory.getLogger(ManualActivityCompletionClientImpl.class);

  private final GrpcWorkflowServiceFactory service;

  private final byte[] taskToken;

  private final DataConverter dataConverter;
  private final String domain;
  private final WorkflowExecution execution;
  private final String activityId;
  private final Scope metricsScope;

  ManualActivityCompletionClientImpl(
      GrpcWorkflowServiceFactory service,
      byte[] taskToken,
      DataConverter dataConverter,
      Scope metricsScope) {
    this.service = service;
    this.taskToken = taskToken;
    this.dataConverter = dataConverter;
    this.domain = null;
    this.execution = null;
    this.activityId = null;
    this.metricsScope = metricsScope;
  }

  ManualActivityCompletionClientImpl(
      GrpcWorkflowServiceFactory service,
      String domain,
      WorkflowExecution execution,
      String activityId,
      DataConverter dataConverter,
      Scope metricsScope) {
    this.service = service;
    this.taskToken = null;
    this.domain = domain;
    this.execution = execution;
    this.activityId = activityId;
    this.dataConverter = dataConverter;
    this.metricsScope = metricsScope;
  }

  @Override
  public void complete(Object result) {
    byte[] convertedResult = dataConverter.toData(result);
    if (taskToken != null) {
      RespondActivityTaskCompletedRequest.Builder request =
          RespondActivityTaskCompletedRequest.newBuilder()
              .setTaskToken(ByteString.copyFrom(taskToken));
      if (result != null) {
        request.setResult(ByteString.copyFrom(convertedResult));
      }
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskCompleted(request.build()));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_COMPLETED_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(e);
        }
        throw new ActivityCompletionFailureException(e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RespondActivityTaskCompletedByIDRequest.Builder request =
          RespondActivityTaskCompletedByIDRequest.newBuilder()
              .setActivityID(activityId)
              .setDomain(domain)
              .setWorkflowID(execution.getWorkflowId())
              .setRunID(execution.getRunId());
      if (result != null) {
        request.setResult(ByteString.copyFrom(convertedResult));
      }
      try {
        service.blockingStub().respondActivityTaskCompletedByID(request.build());
        metricsScope.counter(MetricsType.ACTIVITY_TASK_COMPLETED_BY_ID_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(activityId, e);
      }
    }
  }

  @Override
  public void fail(Throwable failure) {
    if (failure == null) {
      throw new IllegalArgumentException("null failure");
    }
    // When converting failures reason is class name, details are serialized exception.
    if (taskToken != null) {
      RespondActivityTaskFailedRequest request =
          RespondActivityTaskFailedRequest.newBuilder()
              .setReason(failure.getClass().getName())
              .setDetails(OptionsUtils.toByteString(dataConverter.toData(failure)))
              .setTaskToken(ByteString.copyFrom(taskToken))
              .build();
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskFailed(request));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_FAILED_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(e);
        }
        throw new ActivityCompletionFailureException(e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RespondActivityTaskFailedByIDRequest request =
          RespondActivityTaskFailedByIDRequest.newBuilder()
              .setReason(failure.getClass().getName())
              .setDetails(OptionsUtils.toByteString(dataConverter.toData(failure)))
              .setDomain(domain)
              .setWorkflowID(execution.getWorkflowId())
              .setRunID(execution.getRunId())
              .setActivityID(activityId)
              .build();
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskFailedByID(request));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_FAILED_BY_ID_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(activityId, e);
      }
    }
  }

  @Override
  public void recordHeartbeat(Object details) throws CancellationException {
    byte[] convertedDetails = dataConverter.toData(details);
    if (taskToken != null) {
      RecordActivityTaskHeartbeatRequest.Builder request =
          RecordActivityTaskHeartbeatRequest.newBuilder()
              .setTaskToken(ByteString.copyFrom(taskToken));
      if (convertedDetails != null) {
        request.setDetails(ByteString.copyFrom(convertedDetails));
      }
      RecordActivityTaskHeartbeatResponse status;
      try {
        status = service.blockingStub().recordActivityTaskHeartbeat(request.build());
        if (status.getCancelRequested()) {
          throw new ActivityCancelledException();
        }
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RecordActivityTaskHeartbeatByIDRequest.Builder request =
          RecordActivityTaskHeartbeatByIDRequest.newBuilder()
              .setWorkflowID(execution.getWorkflowId())
              .setRunID(execution.getRunId())
              .setActivityID(activityId);
      if (convertedDetails != null) {
        request.setDetails(ByteString.copyFrom(convertedDetails));
      }
      RecordActivityTaskHeartbeatByIDResponse status = null;
      try {
        status = service.blockingStub().recordActivityTaskHeartbeatByID(request.build());
        if (status.getCancelRequested()) {
          throw new ActivityCancelledException();
        }
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(activityId, e);
      }
    }
  }

  @Override
  public void reportCancellation(Object details) {
    byte[] convertedDetails = dataConverter.toData(details);
    if (taskToken != null) {
      RespondActivityTaskCanceledRequest.Builder request =
          RespondActivityTaskCanceledRequest.newBuilder()
              .setTaskToken(ByteString.copyFrom(taskToken));
      if (convertedDetails != null) {
        request.setDetails(ByteString.copyFrom(convertedDetails));
      }
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskCanceled(request.build()));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_CANCELED_COUNTER).inc(1);
      } catch (Exception e) {
        // There is nothing that can be done at this point.
        // so let's just ignore.
        log.info("reportCancellation", e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RespondActivityTaskCanceledByIDRequest.Builder request =
          RespondActivityTaskCanceledByIDRequest.newBuilder()
              .setDomain(domain)
              .setWorkflowID(execution.getWorkflowId())
              .setRunID(OptionsUtils.safeGet(execution.getRunId()))
              .setActivityID(activityId);
      if (convertedDetails != null) {
        request.setDetails(ByteString.copyFrom(convertedDetails));
      }
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskCanceledByID(request.build()));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_CANCELED_BY_ID_COUNTER).inc(1);
      } catch (Exception e) {
        // There is nothing that can be done at this point.
        // so let's just ignore.
        log.warn("reportCancellation", e);
      }
    }
  }
}
