# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.


# This benchmark is is derived from Stefan Marr's Are-We-Fast-Yet benchmark
# suite available at https://github.com/smarr/are-we-fast-yet
NO_TASK = nil
NO_WORK = nil

IDLER     = 0
WORKER    = 1
HANDLER_A = 2
HANDLER_B = 3
DEVICE_A  = 4
DEVICE_B  = 5

NUM_TYPES = 6

DEVICE_PACKET_KIND = 0
WORK_PACKET_KIND   = 1

DATA_SIZE = 4

TRACING = false

class RBObject
  def append(packet, queue_head)
    packet.link = NO_WORK
    return packet if NO_WORK == queue_head

    mouse = queue_head

    while NO_WORK != (link = mouse.link)
      mouse = link
    end
    mouse.link = packet
    queue_head
  end
end

class Scheduler < RBObject
  def initialize
    # init tracing
    @layout = 0

    # init scheduler
    @task_list    = NO_TASK
    @current_task = NO_TASK
    @current_task_identity = 0

    @task_table = Array.new(NUM_TYPES, NO_TASK)

    @queue_count = 0
    @hold_count  = 0
  end

  def create_device(identity, priority, work, state)
    data = DeviceTaskDataRecord.new
    create_task(identity, priority, work, state, data) do |work, word|
      data = word
      function_work = work
      if NO_WORK == function_work
        function_work = data.pending
        if NO_WORK == function_work
          wait
        else
          data.pending = NO_WORK
          queue_packet(function_work)
        end
      else
        data.pending = function_work
        trace(function_work.datum) if TRACING
        hold_self
      end
    end
  end

  def create_handler(identity, priority, work, state)
    data = HandlerTaskDataRecord.new
    create_task(identity, priority, work, state, data) do |work, word|
      data = word
      unless NO_WORK == work
        if WORK_PACKET_KIND == work.kind
          data.work_in_add(work)
        else
          data.device_in_add(work)
        end
      end

      work_packet = data.work_in
      if NO_WORK == work_packet
        wait
      else
        count = work_packet.datum
        if count >= DATA_SIZE
          data.work_in = work_packet.link
          queue_packet(work_packet)
        else
          device_packet = data.device_in
          if NO_WORK == device_packet
            wait
          else
            data.device_in = device_packet.link
            device_packet.datum = work_packet.data[count]
            work_packet.datum = count + 1
            queue_packet(device_packet)
          end
        end
      end
    end
  end

  def create_idler(identity, priority, work, state)
    data = IdleTaskDataRecord.new
    create_task(identity, priority, work, state, data) do |_work, word|
      data = word
      data.count -= 1
      if 0 == data.count
        hold_self
      else
        if 0 == (data.control & 1)
          data.control /= 2
          release(DEVICE_A)
        else
          data.control = (data.control / 2) ^ 53_256
          release(DEVICE_B)
        end
      end
    end
  end

  def create_packet(link, identity, kind)
    Packet.new(link, identity, kind)
  end

  def create_task(identity, priority, work, state, data, &block)
    t = TaskControlBlock.new(@task_list, identity, priority,
                             work, state, data, &block)
    @task_list = t
    @task_table[identity] = t
  end

  def create_worker(identity, priority, work, state)
    data = WorkerTaskDataRecord.new
    create_task(identity, priority, work, state, data) do |work, word|
      data = word
      if NO_WORK == work
        wait
      else
        data.destination = HANDLER_A == data.destination ? HANDLER_B : HANDLER_A
        work.identity = data.destination
        work.datum = 0
        DATA_SIZE.times do |i|
          data.count += 1
          data.count = 1 if data.count > 26
          work.data[i] = 65 + data.count - 1
        end
        queue_packet(work)
      end
    end
  end

  def start
    create_idler(IDLER, 0, NO_WORK, TaskState.running)
    wkq = create_packet(NO_WORK, WORKER, WORK_PACKET_KIND)
    wkq = create_packet(wkq,     WORKER, WORK_PACKET_KIND)

    create_worker(WORKER, 1000, wkq, TaskState.waiting_with_packet)
    wkq = create_packet(NO_WORK, DEVICE_A, DEVICE_PACKET_KIND)
    wkq = create_packet(wkq,     DEVICE_A, DEVICE_PACKET_KIND)
    wkq = create_packet(wkq,     DEVICE_A, DEVICE_PACKET_KIND)

    create_handler(HANDLER_A, 2000, wkq, TaskState.waiting_with_packet)
    wkq = create_packet(NO_WORK, DEVICE_B, DEVICE_PACKET_KIND)
    wkq = create_packet(wkq,     DEVICE_B, DEVICE_PACKET_KIND)
    wkq = create_packet(wkq,     DEVICE_B, DEVICE_PACKET_KIND)

    create_handler(HANDLER_B, 3000, wkq, TaskState.waiting_with_packet)
    create_device(DEVICE_A, 4000, NO_WORK, TaskState.waiting)
    create_device(DEVICE_B, 5000, NO_WORK, TaskState.waiting)

    schedule

    @queue_count == 23_246 && @hold_count == 9297
  end

  def find_task(identity)
    t = @task_table[identity]
    raise 'find_task failed' if NO_TASK == t
    t
  end

  def hold_self
    @hold_count += 1
    @current_task.task_holding = true
    @current_task.link
  end

  def queue_packet(packet)
    task = find_task(packet.identity)
    return NO_TASK if NO_TASK == task

    @queue_count += 1

    packet.link     = NO_WORK
    packet.identity = @current_task_identity
    task.add_input_and_check_priority(packet, @current_task)
  end

  def release(identity)
    task = find_task(identity)
    return NO_TASK if NO_TASK == task

    task.task_holding = false

    if task.priority > @current_task.priority
      task
    else
      @current_task
    end
  end

  def trace(id)
    @layout -= 1
    if 0 >= @layout
      puts ''
      @layout = 50
    end
    print id
  end

  def wait
    @current_task.task_waiting = true
    @current_task
  end

  def schedule
    @current_task = @task_list
    while NO_TASK != @current_task
      if @current_task.is_task_holding_or_waiting
        @current_task = @current_task.link
      else
        @current_task_identity = @current_task.identity
        trace(@current_task_identity) if TRACING
        @current_task = @current_task.run_task
      end
    end
  end
end

class DeviceTaskDataRecord < RBObject
  attr_accessor :pending

  def initialize
    @pending = NO_WORK
  end
end

class HandlerTaskDataRecord < RBObject
  attr_accessor :work_in, :device_in

  def initialize
    @work_in   = NO_WORK
    @device_in = NO_WORK
  end

  def device_in_add(packet)
    @device_in = append(packet, @device_in)
  end

  def work_in_add(packet)
    @work_in = append(packet, @work_in)
  end
end

class IdleTaskDataRecord < RBObject
  attr_accessor :control, :count

  def initialize
    @control = 1
    @count   = 10_000
  end
end

class Packet < RBObject
  attr_accessor :link, :kind, :identity, :datum, :data

  def initialize(link, identity, kind)
    @link     = link
    @kind     = kind
    @identity = identity
    @datum    = 0
    @data     = Array.new(4, 0)
  end
end

class TaskState < RBObject
  attr_writer :task_holding, :task_waiting, :packet_pending

  def initialize
    @task_holding = false
    @task_waiting = false
    @packet_pending = false
  end

  def is_packet_pending
    @packet_pending
  end

  def is_task_waiting
    @task_waiting
  end

  def is_task_holding
    @task_holding
  end

  def packet_pending
    @packet_pending = true
    @task_waiting   = false
    @task_holding   = false
    self
  end

  def running
    @packet_pending = @task_waiting = @task_holding = false
    self
  end

  def waiting
    @packet_pending = @task_holding = false
    @task_waiting = true
    self
  end

  def waiting_with_packet
    @task_holding = false
    @task_waiting = @packet_pending = true
    self
  end

  def is_running
    !@packet_pending && !@task_waiting && !@task_holding
  end

  def is_task_holding_or_waiting
    @task_holding || (!@packet_pending && @task_waiting)
  end

  def is_waiting
    !@packet_pending && @task_waiting && !@task_holding
  end

  def is_waiting_with_packet
    @packet_pending && @task_waiting && !@task_holding
  end

  def self.packet_pending
    new.packet_pending
  end

  def self.running
    new.running
  end

  def self.waiting
    new.waiting
  end

  def self.waiting_with_packet
    new.waiting_with_packet
  end
end

class TaskControlBlock < TaskState
  attr_reader :link, :identity, :function, :priority

  def initialize(link, identity, priority, initial_work_queue,
                 initial_state, private_data, &block)
    super()
    @link = link
    @identity = identity
    @function = block
    @priority = priority
    @input = initial_work_queue
    @handle = private_data

    self.packet_pending = initial_state.is_packet_pending
    self.task_waiting   = initial_state.is_task_waiting
    self.task_holding   = initial_state.is_task_holding
  end

  def add_input_and_check_priority(packet, old_task)
    if NO_WORK == @input
      @input = packet
      self.packet_pending = true
      return self if @priority > old_task.priority
    else
      @input = append(packet, @input)
    end
    old_task
  end

  def run_task
    if is_waiting_with_packet
      message = @input
      @input = message.link
      if NO_WORK == @input
        running
      else
        packet_pending
      end
    else
      message = NO_WORK
    end

    function.call(message, @handle)
  end
end

class WorkerTaskDataRecord < RBObject
  attr_accessor :destination, :count

  def initialize
    @destination = HANDLER_A
    @count = 0
  end
end

def run
  Scheduler.new.start
end