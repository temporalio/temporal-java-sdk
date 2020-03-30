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

import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Functions;
import io.temporal.workflow.QueueConsumer;
import io.temporal.workflow.WorkflowQueue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

final class WorkflowQueueImpl<E> implements WorkflowQueue<E> {

  private final Deque<E> queue = new ArrayDeque<>();
  private final int capacity;

  public WorkflowQueueImpl(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("Capacity less than 1: " + capacity);
    }
    this.capacity = capacity;
  }

  @Override
  public E take() {
    WorkflowThread.await(
        "WorkflowQueue.take",
        () -> {
          CancellationScope.throwCancelled();
          return !queue.isEmpty();
        });
    return queue.pollLast();
  }

  @Override
  public E poll(long timeout, TimeUnit unit) {
    WorkflowThread.await(
        unit.toMillis(timeout),
        "WorkflowQueue.poll",
        () -> {
          CancellationScope.throwCancelled();
          return !queue.isEmpty();
        });

    if (queue.isEmpty()) {
      return null;
    }
    return queue.remove();
  }

  @Override
  public boolean offer(E e) {
    if (queue.size() == capacity) {
      return false;
    }
    queue.addLast(e);
    return true;
  }

  @Override
  public void put(E e) {
    // This condition is excessive as await already checks it.
    // But await can be called only from the sync owned thread.
    // This condition allows puts outside the sync thread which
    // is used by signal handling logic.
    if (queue.size() >= capacity) {
      WorkflowThread.await(
          "WorkflowQueue.put",
          () -> {
            CancellationScope.throwCancelled();
            return queue.size() < capacity;
          });
    }
    queue.addLast(e);
  }

  @Override
  public boolean offer(E e, long timeout, TimeUnit unit) {
    boolean timedOut =
        WorkflowThread.await(
            unit.toMillis(timeout), "WorkflowQueue.offer", () -> queue.size() < capacity);
    if (timedOut) {
      return false;
    }
    queue.addLast(e);
    return true;
  }

  @Override
  public <R> QueueConsumer<R> map(Functions.Func1<? super E, ? extends R> mapper) {
    return new MappedQueueConsumer<R, E>(this, mapper);
  }

  private static class MappedQueueConsumer<R, E> implements QueueConsumer<R> {

    private QueueConsumer<E> source;
    private final Functions.Func1<? super E, ? extends R> mapper;

    public MappedQueueConsumer(
        QueueConsumer<E> source, Functions.Func1<? super E, ? extends R> mapper) {
      this.source = source;
      this.mapper = mapper;
    }

    @Override
    public R take() {
      E element = source.take();
      return mapper.apply(element);
    }

    @Override
    public R poll(long timeout, TimeUnit unit) {
      E element = source.poll(timeout, unit);
      if (element == null) {
        return null;
      }
      return mapper.apply(element);
    }

    @Override
    public <R1> QueueConsumer<R1> map(Functions.Func1<? super R, ? extends R1> mapper) {
      return new MappedQueueConsumer<>(this, mapper);
    }
  }
}
