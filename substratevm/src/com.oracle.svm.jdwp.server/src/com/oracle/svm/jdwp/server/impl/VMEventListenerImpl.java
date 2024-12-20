/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.jdwp.bridge.EventKind;
import com.oracle.svm.jdwp.bridge.JDWPBridge;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.TagConstants;
import com.oracle.svm.jdwp.bridge.WritablePacket;
import com.oracle.svm.jdwp.server.api.BreakpointInfo;
import com.oracle.svm.jdwp.server.api.VMEventListener;

import java.util.Objects;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class VMEventListenerImpl implements VMEventListener {

    private final Map<Integer, ClassPrepareRequest> classPrepareRequests = new ConcurrentHashMap<>();
    private final Map<Integer, Collection<BreakpointInfo>> breakpointRequests = new ConcurrentHashMap<>();
    private final Map<Integer, SteppingInfo> stepRequests = new ConcurrentHashMap<>();
    private final Map<Long, Integer> stepRequestPerThread = new ConcurrentHashMap<>();
    private SocketConnection connection;
    private JDWPContext context;
    private volatile boolean holdEvents;
    private final Object holdEventsLock = new Object();
    private volatile boolean vmDied;

    private int threadStartedRequestId;
    private int threadDeathRequestId;
    private byte threadStartSuspendPolicy;
    private byte threadDeathSuspendPolicy;
    private int vmDeathRequestId;
    private byte vmDeathSuspendPolicy = SuspendStrategy.NONE;
    private int vmStartRequestId;
    private long initialThreadId;

    public VMEventListenerImpl() {
    }

    public void activate(long itid, JDWPContext jdwpContext) {
        this.initialThreadId = itid;
        this.context = jdwpContext;
    }

    @Override
    public void setConnection(SocketConnection connection) {
        this.connection = connection;
    }

    @Override
    public void addClassPrepareRequest(ClassPrepareRequest request) {
        classPrepareRequests.put(request.getRequestId(), request);
    }

    @Override
    public void removeClassPrepareRequest(int requestId) {
        classPrepareRequests.remove(requestId);
    }

    @Override
    public void addBreakpointRequest(int requestId, Collection<BreakpointInfo> infos) {
        breakpointRequests.put(requestId, infos);
    }

    @Override
    public void removeBreakpointRequest(int requestId) {
        Collection<BreakpointInfo> remove = breakpointRequests.remove(requestId);
        if (remove != null) {
            context.getBreakpoints().removeAll(remove);
        }
    }

    @Override
    public void clearAllBreakpointRequests() {
        Iterator<Map.Entry<Integer, Collection<BreakpointInfo>>> entries = breakpointRequests.entrySet().iterator();
        while (entries.hasNext()) {
            Collection<BreakpointInfo> infos = entries.next().getValue();
            context.getBreakpoints().removeAll(infos);
            entries.remove();
        }
    }

    @Override
    public void addStepRequest(int requestId, long threadId, SteppingInfo info) {
        stepRequests.put(requestId, info);
        stepRequestPerThread.put(threadId, requestId);
    }

    @Override
    public void removeStepRequest(int requestId) {
        SteppingInfo info = stepRequests.remove(requestId);
        if (info != null) {
            long threadId = info.getThreadId();
            stepRequestPerThread.remove(threadId);
            ServerJDWP.BRIDGE.setEventEnabled(threadId, EventKind.SINGLE_STEP.ordinal(), false);
        }
    }

    private void clearAllStepRequests() {
        for (SteppingInfo info : stepRequests.values()) {
            long threadId = info.getThreadId();
            ServerJDWP.BRIDGE.setEventEnabled(threadId, EventKind.SINGLE_STEP.ordinal(), false);
        }
        stepRequests.clear();
        stepRequestPerThread.clear();
    }

    @Override
    public void onEventAt(long threadId, long classId, byte typeTag, long methodId, int bci, byte resultTag, long resultPrimitiveOrId, int eventKindFlags) {
        List<Object> infoList = null;
        byte suspendPolicy = 0;
        ServerJDWP.LOGGER.log(() -> "onEventAt(" + threadId + ", " + methodId + ", " + bci + ", flags=" + eventKindFlags + ")");
        if (EventKind.SINGLE_STEP.matchesFlag(eventKindFlags)) {
            Integer requestId = stepRequestPerThread.get(threadId);
            assert requestId != null;
            SteppingInfo info = stepRequests.get(requestId);

            RequestFilter filter = info.stepInfo().filter();
            String className = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(classId).toClassName();
            // A next step, if any, goes from here:
            ServerJDWP.BRIDGE.setSteppingFromLocation(threadId, info.stepInfo().getInterpreterDepth(), info.stepInfo().getInterpreterSize(), methodId, bci, JDWPBridge.UNINITIALIZED_LINE_NUMBER);
            if (filter.isHit(new EventInfo(threadId, classId, className, methodId, bci, info.stepRemoval()))) {
                // Test if we didn't ended up at the same location. This can happen if some steps
                // were filtered out
                if (info.stepInfo().startLocation().differsAndUpdate(methodId, bci)) {
                    ServerJDWP.LOGGER.log(() -> "onStep(" + threadId + ", " + methodId + ", " + bci + ")");
                    infoList = Collections.singletonList(info);
                    suspendPolicy = (byte) Math.max(suspendPolicy, info.suspendPolicy());
                }
            }
        }
        for (Collection<BreakpointInfo> infos : breakpointRequests.values()) {
            for (BreakpointInfo info : infos) {
                EventKind eventKind = info.getEventKind();
                ServerJDWP.LOGGER.log(() -> "  info: " + info + " of " + eventKind + " matches " + eventKindFlags + " = " + eventKind.matchesFlag(eventKindFlags));
                if (eventKind.matchesFlag(eventKindFlags) && info.matches(classId, methodId, bci)) {
                    ResolvedJavaMethod method = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaMethod(methodId);
                    String className = method.getDeclaringClass().toClassName();
                    if (info.getFilter().isHit(new EventInfo(threadId, classId, className, methodId, bci, info.getBreakRemoval()))) {
                        ServerJDWP.LOGGER.log(() -> "onBreakpoint(" + threadId + ", " + methodId + " (" + method + "), " + bci + ")");
                        infoList = addInfo(infoList, info);
                        suspendPolicy = (byte) Math.max(suspendPolicy, info.getSuspendPolicy());
                    }
                }
            }
        }
        if (infoList != null) {
            WritablePacket packet = WritablePacket.commandPacket();
            Packet.Writer data = packet.dataWriter();

            data.writeByte(suspendPolicy);
            data.writeInt(infoList.size()); // # events in reply

            for (Object info : infoList) {
                boolean provideResult = false;
                if (info instanceof SteppingInfo si) {
                    data.writeByte(EventKind.SINGLE_STEP.getEventId());
                    data.writeInt(si.requestId());
                } else if (info instanceof BreakpointInfo bi) {
                    data.writeByte(bi.getEventKind().getEventId());
                    data.writeInt(bi.getRequestId());
                    provideResult = EventKind.METHOD_EXIT_WITH_RETURN_VALUE == bi.getEventKind();
                }
                data.writeLong(threadId);
                // location
                data.writeByte(typeTag);
                data.writeLong(classId);
                data.writeLong(methodId);
                data.writeLong(bci);
                if (provideResult) {
                    writeValue(resultTag, resultPrimitiveOrId, data);
                }
            }
            ThreadRef threadRef = context.getThreadRef(threadId);
            Runnable eventSender = eventSender(packet);
            SuspendedInfo suspendedInfo = new SuspendedInfo(context, threadRef, classId, methodId, bci, -1);
            suspend(suspendPolicy, suspendedInfo, eventSender);
        }
    }

    private static void writeValue(byte resultTag, long resultPrimitiveOrId, Packet.Writer data) {
        data.writeByte(resultTag);
        switch (resultTag) {
            case TagConstants.BOOLEAN:
            case TagConstants.BYTE:
                data.writeByte((byte) resultPrimitiveOrId);
                break;
            case TagConstants.CHAR:
            case TagConstants.SHORT:
                data.writeShort((short) resultPrimitiveOrId);
                break;
            case TagConstants.INT:
            case TagConstants.FLOAT:
                data.writeInt((int) resultPrimitiveOrId);
                break;
            case TagConstants.VOID:
                // No value written, only the tag
                break;
            default:
                data.writeLong(resultPrimitiveOrId);
                break;
        }
    }

    private static List<Object> addInfo(List<Object> infoListArgument, Object info) {
        List<Object> infoList = infoListArgument;
        if (infoList == null) {
            return Collections.singletonList(info);
        } else if (infoList.size() == 1) {
            // We have singleton list
            infoList = new ArrayList<>(infoList);
        }
        infoList.add(info);
        return infoList;
    }

    @Override
    public void onThreadStart(long threadId) {
        if (connection == null || threadStartedRequestId == 0) {
            return;
        }
        WritablePacket packet = WritablePacket.commandPacket();
        Packet.Writer data = packet.dataWriter();
        data.writeByte(threadStartSuspendPolicy);
        data.writeInt(1); // # events in reply
        data.writeByte(EventKind.THREAD_START.getEventId());
        data.writeInt(threadStartedRequestId);
        data.writeLong(threadId);
        context.getThreadsCollector().blockIfVMSuspended();
        ServerJDWP.LOGGER.log(() -> "sending thread started event for thread: " + threadId);
        ThreadRef threadRef = context.getThreadRef(threadId);
        SuspendedInfo suspendedInfo = new SuspendedInfo(context, threadRef, -1, -1, -1, 0);
        suspend(threadStartSuspendPolicy, suspendedInfo, eventSender(packet));
    }

    @Override
    public void onThreadDeath(long threadId) {
        if (connection == null || threadDeathRequestId == 0) {
            return;
        }

        WritablePacket packet = WritablePacket.commandPacket();
        Packet.Writer data = packet.dataWriter();

        byte suspendPolicy = threadDeathSuspendPolicy;
        data.writeByte(suspendPolicy);
        data.writeInt(1); // # events in reply
        data.writeByte(EventKind.THREAD_DEATH.getEventId());
        data.writeInt(threadDeathRequestId);
        data.writeLong(threadId);
        context.getThreadsCollector().blockIfVMSuspended();
        ServerJDWP.LOGGER.log(() -> "sending thread death event for thread: " + threadId);
        if (vmDied && suspendPolicy == SuspendStrategy.ALL) {
            suspendPolicy = SuspendStrategy.EVENT_THREAD;
        }
        ThreadRef threadRef = context.getThreadRef(threadId);
        SuspendedInfo suspendedInfo = new SuspendedInfo(context, threadRef, -1, -1, -1, 0);
        suspend(suspendPolicy, suspendedInfo, eventSender(packet));
    }

    @Override
    public void vmStarted(boolean suspend) {
        WritablePacket packet = WritablePacket.commandPacket();
        Packet.Writer data = packet.dataWriter();
        data.writeByte(suspend ? SuspendStrategy.ALL : SuspendStrategy.NONE);
        data.writeInt(1);
        data.writeByte(EventKind.VM_START.getEventId());
        data.writeInt(vmStartRequestId != -1 ? vmStartRequestId : 0);
        data.writeLong(initialThreadId);
        eventSender(packet).run();
    }

    @Override
    public void onVMDeath() {
        vmDied = true;
        if (connection == null) {
            return;
        }
        WritablePacket packet = WritablePacket.commandPacket();
        Packet.Writer data = packet.dataWriter();

        data.writeByte(vmDeathSuspendPolicy);
        if (vmDeathRequestId != 0) {
            data.writeInt(2);
            // requested event
            data.writeByte(EventKind.VM_DEATH.getEventId());
            data.writeInt(vmDeathRequestId);
            // automatic event
        } else {
            // only automatic event to send
            data.writeInt(1);
        }
        data.writeByte(EventKind.VM_DEATH.getEventId());
        data.writeInt(0);
        // don't queue this packet, send immediately
        connection.sendVMDied(packet);
        // Resume all threads, there can be suspended threads on thread exit.
        // https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jdi/com/sun/jdi/event/VMDeathEvent.html
        if (vmDeathSuspendPolicy != SuspendStrategy.NONE) {
            context.getThreadsCollector().releaseAllThreadsAndDispose();
        }
    }

    @Override
    public void addClassUnloadRequestId(int id) {
        // not implemented yet
        ServerJDWP.LOGGER.log(() -> "class unload events not yet implemented!");
    }

    @Override
    public void addThreadStartedRequestId(int id, byte suspendPolicy) {
        ServerJDWP.LOGGER.log(() -> "Adding thread start listener");
        this.threadStartedRequestId = id;
        this.threadStartSuspendPolicy = suspendPolicy;
    }

    @Override
    public void addThreadDiedRequestId(int id, byte suspendPolicy) {
        ServerJDWP.LOGGER.log(() -> "Adding thread death listener");
        this.threadDeathRequestId = id;
        this.threadDeathSuspendPolicy = suspendPolicy;
    }

    @Override
    public void removeThreadStartedRequestId() {
        this.threadStartSuspendPolicy = 0;
        this.threadStartedRequestId = 0;
    }

    @Override
    public void removeThreadDiedRequestId() {
        this.threadDeathSuspendPolicy = 0;
        this.threadDeathRequestId = 0;
    }

    @Override
    public void addVMDeathRequest(int id, byte suspendPolicy) {
        this.vmDeathRequestId = id;
        this.vmDeathSuspendPolicy = suspendPolicy;
    }

    @Override
    public void addVMStartRequest(int id) {
        this.vmStartRequestId = id;
    }

    @Override
    public void holdEvents() {
        holdEvents = true;
    }

    @Override
    public void releaseEvents() {
        holdEvents = false;
        synchronized (holdEventsLock) {
            holdEventsLock.notifyAll();
        }
    }

    @Override
    public void sendEvent(Packet packet) {
        Objects.requireNonNull(packet);
        connection.queuePacket(packet);
    }

    private Runnable eventSender(Packet packet) {
        return () -> {
            if (connection == null) {
                return;
            }
            if (holdEvents) {
                synchronized (holdEventsLock) {
                    while (holdEvents) {
                        try {
                            holdEventsLock.wait();
                        } catch (InterruptedException ex) {
                            break; // Interrupted
                        }
                    }
                }
            }
            connection.queuePacket(packet);
        };
    }

    private void suspend(byte suspendPolicy, SuspendedInfo suspendedInfo, Runnable eventSender) {
        ServerJDWP.LOGGER.log(() -> "VMEventListenerImpl.suspend(" + suspendPolicy + ", " + suspendedInfo + ") thread = " + suspendedInfo.thread().getThreadId());
        if (SuspendStrategy.NONE == suspendPolicy) {
            eventSender.run();
        } else {
            ThreadRef threadRef = suspendedInfo.thread();
            if (SuspendStrategy.EVENT_THREAD == suspendPolicy) {
                threadRef.suspendedAt(suspendedInfo, eventSender);
            } else {
                assert SuspendStrategy.ALL == suspendPolicy;
                context.getThreadsCollector().suspendAllAt(threadRef, suspendedInfo, eventSender);
            }
        }
    }

    @Override
    public void disposeAllRequests() {
        classPrepareRequests.clear();
        clearAllBreakpointRequests();
        clearAllStepRequests();
        ServerJDWP.BRIDGE.setThreadRequest(false, false);
    }
}
