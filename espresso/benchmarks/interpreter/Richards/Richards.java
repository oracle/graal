/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * This benchmark is derived from https://github.com/newspeaklanguage/benchmarks
 * Originally ported to Java by Mario Wolczko, see http://www.wolczko.com/java_benchmarking.html
 */

/**
 * Richards emulates the task dispatcher of an operating system.
 **/
public final class Richards {  

  public static void main(String[] args) {
    run();
  }

  public static void run() {
    Scheduler scheduler = new Scheduler();
    scheduler.addIdleTask(ID_IDLE, 0, null, COUNT);

    Packet queue = new Packet(null, ID_WORKER, KIND_WORK);
    queue = new Packet(queue, ID_WORKER, KIND_WORK);
    scheduler.addWorkerTask(ID_WORKER, 1000, queue);

    queue = new Packet(null, ID_DEVICE_A, KIND_DEVICE);
    queue = new Packet(queue, ID_DEVICE_A, KIND_DEVICE);
    queue = new Packet(queue, ID_DEVICE_A, KIND_DEVICE);
    scheduler.addHandlerTask(ID_HANDLER_A, 2000, queue);

    queue = new Packet(null, ID_DEVICE_B, KIND_DEVICE);
    queue = new Packet(queue, ID_DEVICE_B, KIND_DEVICE);
    queue = new Packet(queue, ID_DEVICE_B, KIND_DEVICE);
    scheduler.addHandlerTask(ID_HANDLER_B, 3000, queue);

    scheduler.addDeviceTask(ID_DEVICE_A, 4000, null);

    scheduler.addDeviceTask(ID_DEVICE_B, 5000, null);

    scheduler.schedule();

    if (scheduler.queueCount != EXPECTED_QUEUE_COUNT ||
        scheduler.holdCount != EXPECTED_HOLD_COUNT) {
      System.err.println("Error during execution: queueCount = ${scheduler.queueCount},"
                         + "holdCount = ${scheduler.holdCount}.");
    }
    if (EXPECTED_QUEUE_COUNT != scheduler.queueCount) {
      throw new RuntimeException("bad scheduler queue-count");
    }
    if (EXPECTED_HOLD_COUNT != scheduler.holdCount) {
      throw new RuntimeException("bad scheduler hold-count");
    }
  }

  static final int DATA_SIZE = 4;
  static final int COUNT = 10000;

  /**
   * These two constants specify how many times a packet is queued and
   * how many times a task is put on hold in a correct run of richards.
   * They don't have any meaning a such but are characteristic of a
   * correct run so if the actual queue or hold count is different from
   * the expected there must be a bug in the implementation.
   **/
  static final int EXPECTED_QUEUE_COUNT = 23246;
  static final int EXPECTED_HOLD_COUNT = 9297;

  static final int ID_IDLE = 0;
  static final int ID_WORKER = 1;
  static final int ID_HANDLER_A = 2;
  static final int ID_HANDLER_B = 3;
  static final int ID_DEVICE_A = 4;
  static final int ID_DEVICE_B = 5;
  static final int NUMBER_OF_IDS = 6;

  static final int KIND_DEVICE = 0;
  static final int KIND_WORK = 1;
}


/**
 * A scheduler can be used to schedule a set of tasks based on their relative
 * priorities.  Scheduling is done by maintaining a list of task control blocks
 * which holds tasks and the data queue they are processing.
 */
class Scheduler {

  int queueCount = 0;
  int holdCount = 0;
  TaskControlBlock currentTcb;
  int currentId;
  TaskControlBlock list;
  TaskControlBlock[] blocks = new TaskControlBlock[Richards.NUMBER_OF_IDS];

  /// Add an idle task to this scheduler.
  void addIdleTask(int id, int priority, Packet queue, int count) {
    addRunningTask(id, priority, queue, new IdleTask(this, 1, count));
  }

  /// Add a work task to this scheduler.
  void addWorkerTask(int id, int priority, Packet queue) {
    addTask(id,
            priority,
            queue,
            new WorkerTask(this, Richards.ID_HANDLER_A, 0));
  }

  /// Add a handler task to this scheduler.
  void addHandlerTask(int id, int priority, Packet queue) {
    addTask(id, priority, queue, new HandlerTask(this));
  }

  /// Add a handler task to this scheduler.
  void addDeviceTask(int id, int priority, Packet queue) {
    addTask(id, priority, queue, new DeviceTask(this));
  }

  /// Add the specified task and mark it as running.
  void addRunningTask(int id, int priority, Packet queue, Task task) {
    addTask(id, priority, queue, task);
    currentTcb.setRunning();
  }

  /// Add the specified task to this scheduler.
  void addTask(int id, int priority, Packet queue, Task task) {
    currentTcb = new TaskControlBlock(list, id, priority, queue, task);
    list = currentTcb;
    blocks[id] = currentTcb;
  }

  /// Execute the tasks managed by this scheduler.
  void schedule() {
    currentTcb = list;
    while (currentTcb != null) {
      if (currentTcb.isHeldOrSuspended()) {
        currentTcb = currentTcb.link;
      } else {
        currentId = currentTcb.id;
        currentTcb = currentTcb.run();
      }
    }
  }

  /// Release a task that is currently blocked and return the next block to run.
  TaskControlBlock release(int id) {
    TaskControlBlock tcb = blocks[id];
    if (tcb == null) return tcb;
    tcb.markAsNotHeld();
    if (tcb.priority > currentTcb.priority) return tcb;
    return currentTcb;
  }

  /**
   * Block the currently executing task and return the next task control block
   * to run.  The blocked task will not be made runnable until it is explicitly
   * released, even if new work is added to it.
   */
  TaskControlBlock holdCurrent() {
    holdCount++;
    currentTcb.markAsHeld();
    return currentTcb.link;
  }

  /**
   * Suspend the currently executing task and return the next task
   * control block to run.
   * If new work is added to the suspended task it will be made runnable.
   */
  TaskControlBlock suspendCurrent() {
    currentTcb.markAsSuspended();
    return currentTcb;
  }

  /**
   * Add the specified packet to the end of the worklist used by the task
   * associated with the packet and make the task runnable if it is currently
   * suspended.
   */
  TaskControlBlock queue(Packet packet) {
    TaskControlBlock t = blocks[packet.id];
    if (t == null) return t;
    queueCount++;
    packet.link = null;
    packet.id = currentId;
    return t.checkPriorityAdd(currentTcb, packet);
  }
}


/**
 * A task control block manages a task and the queue of work packages associated
 * with it.
 */
class TaskControlBlock {

  TaskControlBlock link;
  int id;       // The id of this block.
  int priority; // The priority of this block.
  Packet queue; // The queue of packages to be processed by the task.
  Task task;
  int state;

  TaskControlBlock(TaskControlBlock link, int id, int priority, Packet queue, Task task) {
    this.link = link;
    this.id = id;
    this.priority = priority;
    this.queue = queue;
    this.task = task;
    state = queue == null ? STATE_SUSPENDED : STATE_SUSPENDED_RUNNABLE;
  }

  /// The task is running and is currently scheduled.
  static final int STATE_RUNNING = 0;

  /// The task has packets left to process.
  static final int STATE_RUNNABLE = 1;

  /**
   * The task is not currently running. The task is not blocked as such and may
   * be started by the scheduler.
   */
  static final int STATE_SUSPENDED = 2;

  /// The task is blocked and cannot be run until it is explicitly released.
  static final int STATE_HELD = 4;

  static final int STATE_SUSPENDED_RUNNABLE = STATE_SUSPENDED | STATE_RUNNABLE;
  static final int STATE_NOT_HELD = ~STATE_HELD;

  void setRunning() {
    state = STATE_RUNNING;
  }

  void markAsNotHeld() {
    state = state & STATE_NOT_HELD;
  }

  void markAsHeld() {
    state = state | STATE_HELD;
  }

  boolean isHeldOrSuspended() {
    return (state & STATE_HELD) != 0 ||
           (state == STATE_SUSPENDED);
  }

  void markAsSuspended() {
    state = state | STATE_SUSPENDED;
  }

  void markAsRunnable() {
    state = state | STATE_RUNNABLE;
  }

  /// Runs this task, if it is ready to be run, and returns the next task to run.
  TaskControlBlock run() {
    Packet packet;
    if (state == STATE_SUSPENDED_RUNNABLE) {
      packet = queue;
      queue = packet.link;
      state = queue == null ? STATE_RUNNING : STATE_RUNNABLE;
    } else {
      packet = null;
    }
    return task.run(packet);
  }

  /**
   * Adds a packet to the worklist of this block's task, marks this as
   * runnable if necessary, and returns the next runnable object to run
   * (the one with the highest priority).
   */
  TaskControlBlock checkPriorityAdd(TaskControlBlock otherTask, Packet packet) {
    if (queue == null) {
      queue = packet;
      markAsRunnable();
      if (priority > otherTask.priority) return this;
    } else {
      queue = packet.addTo(queue);
    }
    return otherTask;
  }

    @Override
    public String toString() { return "tcb { ${task}@${state} }"; }
}

/**
 *  Abstract task that manipulates work packets.
 */
abstract class Task {

  Scheduler scheduler; // The scheduler that manages this task.

  Task(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  abstract TaskControlBlock run(Packet packet);
}

/**
 * An idle task doesn't do any work itself but cycles control between the two
 * device tasks.
 */
class IdleTask extends Task {

  int v1;    // A seed value that controls how the device tasks are scheduled.
  int count; // The number of times this task should be scheduled.

  IdleTask(Scheduler scheduler, int v1, int count) {
    super(scheduler);
    this.v1 = v1;
    this.count = count;
  }

  @Override
  TaskControlBlock run(Packet packet) {
    count--;
    if (count == 0) return scheduler.holdCurrent();
    if ((v1 & 1) == 0) {
      v1 = v1 >> 1;
      return scheduler.release(Richards.ID_DEVICE_A);
    }
    v1 = (v1 >> 1) ^ 0xD008;
    return scheduler.release(Richards.ID_DEVICE_B);
  }

  @Override
  public String toString() { return "IdleTask"; }
}


/**
 * A task that suspends itself after each time it has been run to simulate
 * waiting for data from an external device.
 */
class DeviceTask extends Task {

  Packet v1;

  DeviceTask(Scheduler scheduler) {
    super(scheduler);
  }

  @Override
  TaskControlBlock run(Packet packet) {
    if (packet == null) {
      if (v1 == null) return scheduler.suspendCurrent();
      Packet v = v1;
      v1 = null;
      return scheduler.queue(v);
    }
    v1 = packet;
    return scheduler.holdCurrent();
  }

  @Override
  public String toString() { return "DeviceTask"; }
}


/**
 * A task that manipulates work packets.
 */
class WorkerTask extends Task {

  int v1; // A seed used to specify how work packets are manipulated.
  int v2; // Another seed used to specify how work packets are manipulated.

  WorkerTask(Scheduler scheduler, int v1, int v2) {
    super(scheduler);
    this.v1 = v1;
    this.v2 = v2;
  }

  @Override
  TaskControlBlock run(Packet packet) {
    if (packet == null) {
      return scheduler.suspendCurrent();
    }
    if (v1 == Richards.ID_HANDLER_A) {
      v1 = Richards.ID_HANDLER_B;
    } else {
      v1 = Richards.ID_HANDLER_A;
    }
    packet.id = v1;
    packet.a1 = 0;
    for (int i = 0; i < Richards.DATA_SIZE; i++) {
      v2++;
      if (v2 > 26) v2 = 1;
      packet.a2[i] = v2;
    }
    return scheduler.queue(packet);
  }

  @Override
  public String toString() { return "WorkerTask"; }
}


/**
 * A task that manipulates work packets and then suspends itself.
 */
class HandlerTask extends Task {

  Packet v1;
  Packet v2;

  HandlerTask(Scheduler scheduler) {
    super(scheduler);
  }

  @Override
  TaskControlBlock run(Packet packet) {
    if (packet != null) {
      if (packet.kind == Richards.KIND_WORK) {
        v1 = packet.addTo(v1);
      } else {
        v2 = packet.addTo(v2);
      }
    }
    if (v1 != null) {
      int count = v1.a1;
      Packet v;
      if (count < Richards.DATA_SIZE) {
        if (v2 != null) {
          v = v2;
          v2 = v2.link;
          v.a1 = v1.a2[count];
          v1.a1 = count + 1;
          return scheduler.queue(v);
        }
      } else {
        v = v1;
        v1 = v1.link;
        return scheduler.queue(v);
      }
    }
    return scheduler.suspendCurrent();
  }

  @Override
  public String toString() { return "HandlerTask"; }
}


/**
 * A simple package of data that is manipulated by the tasks.  The exact layout
 * of the payload data carried by a packet is not importaint, and neither is the
 * nature of the work performed on packets by the tasks.
 * Besides carrying data, packets form linked lists and are hence used both as
 * data and worklists.
 */
class Packet {

  Packet link; // The tail of the linked list of packets.
  int id;      // An ID for this packet.
  int kind;    // The type of this packet.
  int a1 = 0;

  int[] a2 = new int[Richards.DATA_SIZE];

  Packet(Packet link, int id, int kind) {
    this.link = link;
    this.id = id;
    this.kind = kind;
  }

  /// Add this packet to the end of a worklist, and return the worklist.
  Packet addTo(Packet queue) {
    link = null;
    if (queue == null) return this;
    Packet peek, next = queue;
    while ((peek = next.link) != null) next = peek;
    next.link = this;
    return queue;
  }

  @Override
  public String toString() { return "Packet"; }
}
