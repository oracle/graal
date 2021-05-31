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

/* This benchmark is is derived from Stefan Marr's Are-We-Fast-Yet benchmark
 * suite available at https://github.com/smarr/are-we-fast-yet
 */
'use strict';

var NO_TASK = null,
  NO_WORK   = null,
  IDLER     = 0,
  WORKER    = 1,
  HANDLER_A = 2,
  HANDLER_B = 3,
  DEVICE_A  = 4,
  DEVICE_B  = 5,
  NUM_TYPES = 6,

  DEVICE_PACKET_KIND = 0,
  WORK_PACKET_KIND   = 1,

  DATA_SIZE = 4,

  TRACING = false;

function RBObject() {}

RBObject.prototype.append = function (packet, queueHead) {
  packet.link = NO_WORK;
  if (NO_WORK === queueHead) {
    return packet;
  }

  var mouse = queueHead,
    link;

  while (NO_WORK !== (link = mouse.link)) {
    mouse = link;
  }
  mouse.link = packet;
  return queueHead;
};

function Scheduler() {
  RBObject.call(this);

  // init tracing
  this.layout = 0;

  // init scheduler
  this.queuePacketCount = 0;
  this.holdCount = 0;
  this.taskTable = new Array(NUM_TYPES).fill(NO_TASK);
  this.taskList  = NO_TASK;

  this.currentTask = null;
  this.currentTaskIdentity = 0;
}
Scheduler.prototype = Object.create(RBObject.prototype);

Scheduler.prototype.createDevice = function (identity, priority, workPacket,
                                             state) {
  var data = new DeviceTaskDataRecord(),
    that = this;

  this.createTask(identity, priority, workPacket, state, data,
    function(workArg, wordArg) {
      var dataRecord = wordArg,
        functionWork = workArg;
      if (NO_WORK === functionWork) {
        if (NO_WORK === (functionWork = dataRecord.pending)) {
          return that.markWaiting();
        } else {
          dataRecord.pending = NO_WORK;
          return that.queuePacket(functionWork);
        }
      } else {
        dataRecord.pending = functionWork;
        if (TRACING) {
          that.trace(functionWork.datum);
        }
        return that.holdSelf();
      }
    });
};

Scheduler.prototype.createHandler = function (identity, priority, workPacket,
                                              state) {
  var data = new HandlerTaskDataRecord(),
    that = this;
  this.createTask(identity, priority, workPacket, state, data,
    function (work, word) {
      var dataRecord = word;
      if (NO_WORK !== work) {
        if (WORK_PACKET_KIND === work.kind) {
          dataRecord.workInAdd(work);
        } else {
          dataRecord.deviceInAdd(work);
        }
      }

      var workPacket;
      if (NO_WORK === (workPacket = dataRecord.workIn)) {
        return that.markWaiting();
      } else {
        var count = workPacket.datum;
        if (count >= DATA_SIZE) {
          dataRecord.workIn = workPacket.link;
          return that.queuePacket(workPacket);
        } else {
          var devicePacket;
          if (NO_WORK === (devicePacket = dataRecord.deviceIn)) {
            return that.markWaiting();
          } else {
            dataRecord.deviceIn = devicePacket.link;
            devicePacket.datum  = workPacket.data[count];
            workPacket.datum    = count + 1;
            return that.queuePacket(devicePacket);
          }
        }
      }
    });
};

Scheduler.prototype.createIdler = function (identity, priority, work, state) {
  var data = new IdleTaskDataRecord(),
    that = this;
  this.createTask(identity, priority, work, state, data,
    function (workArg, wordArg) {
      var dataRecord = wordArg;
      dataRecord.count -= 1;
      if (0 === dataRecord.count) {
        return that.holdSelf();
      } else {
        if (0 === (dataRecord.control & 1)) {
          dataRecord.control /= 2;
          return that.release(DEVICE_A);
        } else {
          dataRecord.control = (dataRecord.control / 2) ^ 53256;
          return that.release(DEVICE_B);
        }
      }
    });
};

Scheduler.prototype.createPacket = function (link, identity, kind) {
  return new Packet(link, identity, kind);
};

Scheduler.prototype.createTask = function (identity, priority, work, state,
                                           data, fn) {
  var t = new TaskControlBlock(this.taskList, identity, priority, work, state,
    data, fn);
  this.taskList = t;
  this.taskTable[identity] = t;
};

Scheduler.prototype.createWorker = function (identity, priority, workPacket, state) {
  var dataRecord = new WorkerTaskDataRecord(),
    that = this;
  this.createTask(identity, priority, workPacket, state, dataRecord,
    function (work, word) {
      var data = word;
      if (NO_WORK === work) {
        return that.markWaiting();
      } else {
        data.destination = (HANDLER_A === data.destination) ? HANDLER_B : HANDLER_A;
        work.identity = data.destination;
        work.datum = 0;
        for (var i = 0; i < DATA_SIZE; i++) {
          data.count += 1;
          if (data.count > 26) { data.count = 1; }
          work.data[i] = 65 + data.count - 1;
        }
        return that.queuePacket(work);
      }
    });
};

Scheduler.prototype.start = function () {
  var workQ;

  this.createIdler(IDLER, 0, NO_WORK, TaskState.createRunning());
  workQ = this.createPacket(NO_WORK, WORKER, WORK_PACKET_KIND);
  workQ = this.createPacket(workQ,   WORKER, WORK_PACKET_KIND);

  this.createWorker(WORKER, 1000, workQ, TaskState.createWaitingWithPacket());
  workQ = this.createPacket(NO_WORK, DEVICE_A, DEVICE_PACKET_KIND);
  workQ = this.createPacket(workQ,   DEVICE_A, DEVICE_PACKET_KIND);
  workQ = this.createPacket(workQ,   DEVICE_A, DEVICE_PACKET_KIND);

  this.createHandler(HANDLER_A, 2000, workQ, TaskState.createWaitingWithPacket());
  workQ = this.createPacket(NO_WORK, DEVICE_B, DEVICE_PACKET_KIND);
  workQ = this.createPacket(workQ,   DEVICE_B, DEVICE_PACKET_KIND);
  workQ = this.createPacket(workQ,   DEVICE_B, DEVICE_PACKET_KIND);

  this.createHandler(HANDLER_B, 3000,   workQ, TaskState.createWaitingWithPacket());
  this.createDevice(DEVICE_A,   4000, NO_WORK, TaskState.createWaiting());
  this.createDevice(DEVICE_B,   5000, NO_WORK, TaskState.createWaiting());

  this.schedule();

  return this.queuePacketCount == 23246 && this.holdCount == 9297;
};

Scheduler.prototype.findTask = function (identity) {
  var t = this.taskTable[identity];
  if (NO_TASK == t) { throw "findTask failed"; }
  return t;
};

Scheduler.prototype.holdSelf = function () {
  this.holdCount += 1;
  this.currentTask.setTaskHolding(true);
  return this.currentTask.link;
};

Scheduler.prototype.queuePacket = function (packet) {
  var t = this.findTask(packet.identity);
  if (NO_TASK == t) { return NO_TASK; }

  this.queuePacketCount += 1;

  packet.link = NO_WORK;
  packet.identity = this.currentTaskIdentity;
  return t.addInputAndCheckPriority(packet, this.currentTask);
};

Scheduler.prototype.release = function (identity) {
  var t = this.findTask(identity);
  if (NO_TASK == t) { return NO_TASK; }
  t.setTaskHolding(false);
  if (t.priority > this.currentTask.priority) {
    return t;
  } else {
    return this.currentTask;
  }
};

Scheduler.prototype.trace = function (id) {
  this.layout -= 1;
  if (0 >= this.layout) {
    process.stdout.write("\n");
    this.layout = 50;
  }
  process.stdout.write(id);
};

Scheduler.prototype.markWaiting = function () {
  this.currentTask.setTaskWaiting(true);
  return this.currentTask;
};

Scheduler.prototype.schedule = function () {
  this.currentTask = this.taskList;
  while (NO_TASK != this.currentTask) {
    if (this.currentTask.isTaskHoldingOrWaiting()) {
      this.currentTask = this.currentTask.link;
    } else {
      this.currentTaskIdentity = this.currentTask.identity;
      if (TRACING) { this.trace(this.currentTaskIdentity); }
      this.currentTask = this.currentTask.runTask();
    }
  }
};

function DeviceTaskDataRecord() {
  RBObject.call(this);
  this.pending = NO_WORK;
}
DeviceTaskDataRecord.prototype = Object.create(RBObject.prototype);

function HandlerTaskDataRecord() {
  RBObject.call(this);
  this.workIn = this.deviceIn = NO_WORK;
}
HandlerTaskDataRecord.prototype = Object.create(RBObject.prototype);

HandlerTaskDataRecord.prototype.deviceInAdd = function (packet) {
  this.deviceIn = this.append(packet, this.deviceIn);
};

HandlerTaskDataRecord.prototype.workInAdd = function (packet) {
  this.workIn = this.append(packet, this.workIn);
};

function IdleTaskDataRecord() {
  RBObject.call(this);
  this.control = 1;
  this.count   = 10000;
}

function Packet(link, identity, kind) {
  RBObject.call(this);
  this.link     = link;
  this.identity = identity;
  this.kind     = kind;
  this.datum    = 0;
  this.data     = new Array(DATA_SIZE).fill(0);
}
Packet.prototype = Object.create(RBObject.prototype);

function TaskState() {
  RBObject.call(this);

  this.packetPending_ = false;
  this.taskWaiting_   = false;
  this.taskHolding_   = false;
}
TaskState.prototype = Object.create(RBObject.prototype);

TaskState.prototype.isPacketPending = function () { return this.packetPending_; };
TaskState.prototype.isTaskHolding   = function () { return this.taskHolding_;   };
TaskState.prototype.isTaskWaiting   = function () { return this.taskWaiting_;   };

TaskState.prototype.setTaskHolding   = function (b) { this.taskHolding_ = b; };
TaskState.prototype.setTaskWaiting   = function (b) { this.taskWaiting_ = b; };
TaskState.prototype.setPacketPending = function (b) { this.packetPending_ = b; };

TaskState.prototype.packetPending = function () {
  this.packetPending_ = true;
  this.taskWaiting_   = false;
  this.taskHolding_   = false;
};

TaskState.prototype.running = function () {
  this.packetPending_ = this.taskWaiting_ = this.taskHolding_ = false;
};

TaskState.prototype.waiting = function () {
  this.packetPending_ = this.taskHolding_ = false;
  this.taskWaiting_ = true;
};

TaskState.prototype.waitingWithPacket = function () {
  this.taskHolding_ = false;
  this.taskWaiting_ = this.packetPending_ = true;
};

TaskState.prototype.isRunning = function () {
  return !this.packetPending_ && !this.taskWaiting_ && !this.taskHolding_;
};

TaskState.prototype.isTaskHoldingOrWaiting = function () {
  return this.taskHolding_ || (!this.packetPending_ && this.taskWaiting_);
};

TaskState.prototype.isWaiting = function () {
  return !this.packetPending_ && this.taskWaiting_ && !this.taskHolding_;
};

TaskState.prototype.isWaitingWithPacket = function () {
  return this.packetPending_ && this.taskWaiting_ && !this.taskHolding_;
};

TaskState.createPacketPending = function () {
  var t = new TaskState();
  t.packetPending();
  return t;
};

TaskState.createRunning = function () {
  var t = new TaskState();
  t.running();
  return t;
};

TaskState.createWaiting = function () {
  var t = new TaskState();
  t.waiting();
  return t;
};

TaskState.createWaitingWithPacket = function () {
  var t = new TaskState();
  t.waitingWithPacket();
  return t;
};

function TaskControlBlock(link, identity, priority, initialWorkQueue,
                          initialState, privateData, fn) {
  TaskState.call(this);
  this.link     = link;
  this.identity = identity;
  this.priority = priority;
  this.input    = initialWorkQueue;
  this.setPacketPending(initialState.isPacketPending());
  this.setTaskWaiting(initialState.isTaskWaiting());
  this.setTaskHolding(initialState.isTaskHolding());
  this.handle = privateData;
  this.function = fn;
}
TaskControlBlock.prototype = Object.create(TaskState.prototype);

TaskControlBlock.prototype.addInputAndCheckPriority = function (packet, oldTask) {
  if (NO_WORK == this.input) {
    this.input = packet;
    this.setPacketPending(true);
    if (this.priority > oldTask.priority) { return this; }
  } else {
    if (this.append === null) {
      var i = 0;
    }
    this.input = this.append(packet, this.input);
  }
  return oldTask;
};

TaskControlBlock.prototype.runTask = function () {
  var message;
  if (this.isWaitingWithPacket()) {
    message = this.input;
    this.input = message.link;
    if (NO_WORK == this.input) {
      this.running();
    } else {
      this.packetPending();
    }
  } else {
    message = NO_WORK;
  }
  return this.function(message, this.handle);
};

function WorkerTaskDataRecord() {
  RBObject.call(this);
  this.destination = HANDLER_A;
  this.count       = 0;
}
WorkerTaskDataRecord.prototype = Object.create(RBObject.prototype);

function run() {
  return (new Scheduler()).start();
}
