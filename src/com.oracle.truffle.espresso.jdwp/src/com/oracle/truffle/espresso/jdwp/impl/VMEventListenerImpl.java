/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.FieldBreakpoint;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodBreakpoint;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.TagConstants;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VMEventListenerImpl implements VMEventListener {

    private final Ids<Object> ids;
    private final JDWPContext context;
    private final DebuggerController debuggerController;
    private final HashMap<Integer, ClassPrepareRequest> classPrepareRequests = new HashMap<>();
    private final HashMap<Integer, BreakpointInfo> breakpointRequests = new HashMap<>();
    private final HashMap<Integer, RequestFilter> monitorContendedRequests = new HashMap<>();
    private final HashMap<Integer, RequestFilter> monitorContendedEnteredRequests = new HashMap<>();
    private final HashMap<Integer, RequestFilter> monitorWaitRequests = new HashMap<>();
    private final HashMap<Integer, RequestFilter> monitorWaitedRequests = new HashMap<>();
    private final StableBoolean fieldBreakpointsActive = new StableBoolean(false);
    private static volatile int fieldBreakpointCount;
    private final StableBoolean methodBreakpointsActive = new StableBoolean(false);
    private static volatile int methodBreakpointCount;
    private SocketConnection connection;
    private volatile boolean holdEvents;

    private int threadStartedRequestId;
    private int threadDeathRequestId;
    private byte threadStartSuspendPolicy;
    private byte threadDeathSuspendPolicy;
    private int vmDeathRequestId;
    private int vmStartRequestId;
    private final List<PacketStream> heldEvents = new ArrayList<>();
    private final Map<Object, Set<Object>> ownedMonitors = new HashMap<>();
    private final Map<Object, Object> currentContendedMonitor = new HashMap<>();

    public VMEventListenerImpl(DebuggerController controller) {
        this.debuggerController = controller;
        this.context = controller.getContext();
        this.ids = context.getIds();
    }

    public void setConnection(SocketConnection connection) {
        this.connection = connection;
    }

    @Override
    public Callable<Void> addClassPrepareRequest(ClassPrepareRequest request) {
        classPrepareRequests.put(request.getRequestId(), request);
        // check if the class has been already prepared and send the event
        if (request.getPatterns() == null) {
            return null;
        }

        // optimize for fully qualified class name pattern
        Pattern[] patterns = request.getPatterns();

        for (Pattern patt : patterns) {
            String pattern = patt.pattern();
            KlassRef[] klasses = context.findLoadedClass(pattern.replace('.', '/'));
            if (klasses.length > 0) {
                for (KlassRef klass : klasses) {
                    // great, we can simply send a class prepare event for the class
                    return getPreparedCallable(request, klass);
                }
            } else {
                KlassRef[] allLoadedClasses = context.getAllLoadedClasses();
                for (KlassRef klass : allLoadedClasses) {
                    String dotName = klass.getNameAsString().replace('/', '.');
                    Matcher matcher = patt.matcher(dotName);

                    if (matcher.matches()) {
                        return getPreparedCallable(request, klass);
                    }
                }
            }
        }
        return null;
    }

    private Callable<Void> getPreparedCallable(ClassPrepareRequest request, KlassRef klass) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Object thread = klass.getPrepareThread();
                if (request.getThread() == null || request.getThread() == thread) {
                    classPrepared(klass, thread, true);
                }
                return null;
            }
        };
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void removeClassPrepareRequest(int requestId) {
        classPrepareRequests.remove(requestId);
    }

    @Override
    public void addBreakpointRequest(int requestId, BreakpointInfo info) {
        breakpointRequests.put(requestId, info);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void removeBreakpointRequest(int requestId) {
        BreakpointInfo remove = breakpointRequests.remove(requestId);
        Breakpoint[] breakpoints = remove.getBreakpoints();
        for (Breakpoint breakpoint : breakpoints) {
            breakpoint.dispose();
        }
    }

    @Override
    public void clearAllBreakpointRequests() {
        breakpointRequests.clear();
    }

    @Override
    public void increaseFieldBreakpointCount() {
        fieldBreakpointCount++;
        fieldBreakpointsActive.set(true);
    }

    @Override
    public void decreaseFieldBreakpointCount() {
        fieldBreakpointCount--;
        if (fieldBreakpointCount <= 0) {
            fieldBreakpointCount = 0;
            fieldBreakpointsActive.set(false);
        }
    }

    @Override
    public void increaseMethodBreakpointCount() {
        methodBreakpointCount++;
        methodBreakpointsActive.set(true);
    }

    @Override
    public void decreaseMethodBreakpointCount() {
        methodBreakpointCount--;
        if (methodBreakpointCount <= 0) {
            methodBreakpointCount = 0;
            methodBreakpointsActive.set(false);
        }
    }

    @Override
    public boolean hasFieldModificationBreakpoint(FieldRef field, Object receiver, Object value) {
        if (!fieldBreakpointsActive.get()) {
            return false;
        } else {
            return checkFieldModificationBreakpoint(field, receiver, value);
        }
    }

    private boolean checkFieldModificationBreakpoint(FieldRef field, Object receiver, Object value) {
        if (!field.hasActiveBreakpoint()) {
            return false;
        } else {
            return checkFieldModificationSlowPath(field, receiver, value);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private boolean checkFieldModificationSlowPath(FieldRef field, Object receiver, Object value) {
        for (FieldBreakpoint info : field.getFieldBreakpointInfos()) {
            if (info.isModificationBreakpoint()) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareFieldBreakpoint(new FieldBreakpointEvent((FieldBreakpointInfo) info, receiver, value));
                debuggerController.suspend(context.asGuestThread(Thread.currentThread()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFieldAccessBreakpoint(FieldRef field, Object receiver) {
        if (!fieldBreakpointsActive.get()) {
            return false;
        } else {
            return checkFieldAccessBreakpoint(field, receiver);
        }
    }

    private boolean checkFieldAccessBreakpoint(FieldRef field, Object receiver) {
        if (!field.hasActiveBreakpoint()) {
            return false;
        } else {
            return checkFieldAccessSlowPath(field, receiver);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private boolean checkFieldAccessSlowPath(FieldRef field, Object receiver) {
        for (FieldBreakpoint info : field.getFieldBreakpointInfos()) {
            if (info.isAccessBreakpoint()) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareFieldBreakpoint(new FieldBreakpointEvent((FieldBreakpointInfo) info, receiver));
                debuggerController.suspend(context.asGuestThread(Thread.currentThread()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasMethodBreakpoint(MethodRef method, Object returnValue) {
        if (!methodBreakpointsActive.get()) {
            return false;
        } else {
            return checkMethodBreakpoint(method, returnValue);
        }
    }

    private boolean checkMethodBreakpoint(MethodRef method, Object returnValue) {
        if (!method.hasActiveBreakpoint()) {
            return false;
        } else {
            return checkMethodSlowPath(method, returnValue);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private boolean checkMethodSlowPath(MethodRef method, Object returnValue) {
        for (MethodBreakpoint info : method.getMethodBreakpointInfos()) {
            // OK, tell the Debug API to suspend the thread now
            debuggerController.prepareMethodBreakpoint(new MethodBreakpointEvent((MethodBreakpointInfo) info, returnValue));
            debuggerController.suspend(context.asGuestThread(Thread.currentThread()));
            return true;
        }
        return false;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void classPrepared(KlassRef klass, Object prepareThread, boolean preparedEarlier) {
        if (connection == null) {
            return;
        }
        // prepare the event and ship
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        // check if event should be reported based on the current patterns
        String dotName = klass.getNameAsString().replace('/', '.');
        ClassPrepareRequest[] allClassPrepareRequests = getAllClassPrepareRequests();
        ArrayList<ClassPrepareRequest> toSend = new ArrayList<>();
        byte suspendPolicy = SuspendStrategy.NONE;

        for (ClassPrepareRequest cpr : allClassPrepareRequests) {
            Pattern[] patterns = cpr.getPatterns();
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(dotName);

                if (matcher.matches()) {
                    toSend.add(cpr);
                    byte cprPolicy = cpr.getSuspendPolicy();
                    if (cprPolicy == SuspendStrategy.ALL) {
                        suspendPolicy = SuspendStrategy.ALL;
                    } else if (cprPolicy == SuspendStrategy.EVENT_THREAD && suspendPolicy != SuspendStrategy.ALL) {
                        suspendPolicy = SuspendStrategy.EVENT_THREAD;
                    }
                }
            }
        }

        if (!toSend.isEmpty()) {
            if (preparedEarlier) {
                stream.writeByte(SuspendStrategy.NONE);
            } else {
                stream.writeByte(suspendPolicy);
            }
            stream.writeInt(toSend.size());

            for (ClassPrepareRequest cpr : toSend) {
                stream.writeByte(RequestedJDWPEvents.CLASS_PREPARE);
                stream.writeInt(cpr.getRequestId());
                stream.writeLong(ids.getIdAsLong(prepareThread));
                stream.writeByte(TypeTag.CLASS);
                stream.writeLong(ids.getIdAsLong(klass));
                stream.writeString(klass.getTypeAsString());
                // only send PREPARED status for class prepare events.
                // if using ClassStatusConstants.INITIALIZED the debugger doesn't submit a
                // breakpoint!
                stream.writeInt(ClassStatusConstants.PREPARED);
                classPrepareRequests.remove(cpr.getRequestId());
            }
            if (!preparedEarlier && suspendPolicy != SuspendStrategy.NONE) {
                // the current thread has just prepared the class
                // so we must suspend according to suspend policy
                debuggerController.immediateSuspend(prepareThread, suspendPolicy, new Callable<Void>() {
                    @Override
                    public Void call() {
                        if (holdEvents) {
                            heldEvents.add(stream);
                        } else {
                            connection.queuePacket(stream);
                        }
                        return null;
                    }
                });
            } else {
                if (holdEvents) {
                    heldEvents.add(stream);
                } else {
                    connection.queuePacket(stream);
                }
            }
        }
    }

    private ClassPrepareRequest[] getAllClassPrepareRequests() {
        Collection<ClassPrepareRequest> values = classPrepareRequests.values();
        return new ArrayList<>(values).toArray(new ClassPrepareRequest[values.size()]);
    }

    @Override
    public void breakpointHit(BreakpointInfo info, CallFrame frame, Object currentThread) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(info.getEventKind());
        stream.writeInt(info.getRequestId());
        long threadId = ids.getIdAsLong(currentThread);
        stream.writeLong(threadId);

        // location
        stream.writeByte(frame.getTypeTag());
        stream.writeLong(frame.getClassId());
        stream.writeLong(frame.getMethodId());
        stream.writeLong(frame.getCodeIndex());
        JDWPLogger.log("Sending breakpoint hit event in thread: %s with suspension policy: %d", JDWPLogger.LogLevel.STEPPING, context.getThreadName(currentThread), info.getSuspendPolicy());
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void methodBreakpointHit(MethodBreakpointEvent methodEvent, Object currentThread, CallFrame frame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        MethodBreakpointInfo info = methodEvent.getInfo();

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(info.getEventKind());
        stream.writeInt(info.getRequestId());
        long threadId = ids.getIdAsLong(currentThread);
        stream.writeLong(threadId);

        // location
        stream.writeByte(frame.getTypeTag());
        stream.writeLong(frame.getClassId());
        stream.writeLong(frame.getMethodId());
        stream.writeLong(frame.getCodeIndex());

        // return value if requested
        if (info.getEventKind() == RequestedJDWPEvents.METHOD_EXIT_WITH_RETURN_VALUE) {
            Object returnValue = methodEvent.getReturnValue();
            byte tag = context.getTag(returnValue);
            JDWP.writeValue(tag, returnValue, stream, true, context);
        }

        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void fieldAccessBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame) {
        PacketStream stream = writeSharedFieldInformation(event, currentThread, callFrame, RequestedJDWPEvents.FIELD_ACCESS);
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void fieldModificationBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame) {
        PacketStream stream = writeSharedFieldInformation(event, currentThread, callFrame, RequestedJDWPEvents.FIELD_MODIFICATION);

        // value about to be set
        Object value = event.getValue();
        byte tag = context.getTag(value);
        JDWP.writeValue(tag, value, stream, true, context);
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    private PacketStream writeSharedFieldInformation(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame, byte eventType) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        FieldBreakpointInfo info = event.getInfo();

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(eventType);
        stream.writeInt(info.getRequestId());
        long threadId = ids.getIdAsLong(currentThread);
        stream.writeLong(threadId);

        // location
        stream.writeByte(callFrame.getTypeTag());
        stream.writeLong(callFrame.getClassId());
        stream.writeLong(callFrame.getMethodId());
        stream.writeLong(callFrame.getCodeIndex());

        // tagged refType
        KlassRef klass = info.getKlass();
        stream.writeByte(TypeTag.getKind(klass));
        stream.writeLong(context.getIds().getIdAsLong(klass));

        // fieldID
        stream.writeLong(context.getIds().getIdAsLong(info.getField()));

        // tagged object ID for field being accessed
        stream.writeByte(TagConstants.OBJECT);
        if (Modifier.isStatic(info.getField().getModifiers())) {
            stream.writeLong(0);
        } else {
            stream.writeLong(context.getIds().getIdAsLong(event.getReceiver()));
        }
        return stream;
    }

    @Override
    public void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, CallFrame[] callFrames) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        CallFrame top = callFrames[0];
        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.EXCEPTION);
        stream.writeInt(info.getRequestId());
        stream.writeLong(ids.getIdAsLong(currentThread));

        // location
        stream.writeByte(top.getTypeTag());
        stream.writeLong(top.getClassId());
        stream.writeLong(top.getMethodId());
        stream.writeLong(top.getCodeIndex());

        // exception
        stream.writeByte(TagConstants.OBJECT);
        stream.writeLong(context.getIds().getIdAsLong(exception));

        // catch-location
        boolean caught = false;
        for (CallFrame callFrame : callFrames) {
            MethodRef method = (MethodRef) context.getIds().fromId((int) callFrame.getMethodId());
            int catchLocation = context.getCatchLocation(method, exception, (int) callFrame.getCodeIndex());
            if (catchLocation != -1) {
                stream.writeByte(callFrame.getTypeTag());
                stream.writeLong(callFrame.getClassId());
                stream.writeLong(callFrame.getMethodId());
                stream.writeLong(catchLocation);
                caught = true;
                break;
            }
        }
        if (!caught) {
            stream.writeByte((byte) 1);
            stream.writeLong(0);
            stream.writeLong(0);
            stream.writeLong(0);
        }
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void stepCompleted(SteppingInfo info, CallFrame currentFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.SINGLE_STEP);
        stream.writeInt(info.getRequestId());
        stream.writeLong(currentFrame.getThreadId());

        // location
        stream.writeByte(currentFrame.getTypeTag());
        stream.writeLong(currentFrame.getClassId());
        stream.writeLong(currentFrame.getMethodId());
        long codeIndex = info.getStepOutBCI() != -1 ? info.getStepOutBCI() : currentFrame.getCodeIndex();
        stream.writeLong(codeIndex);
        JDWPLogger.log("Sending step completed event", JDWPLogger.LogLevel.STEPPING);

        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void sendMonitorContendedEnterEvent(MonitorEvent monitorEvent, Object currentThread, CallFrame currentFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(monitorEvent.getFilter().getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.MONITOR_CONTENDED_ENTER);
        stream.writeInt(monitorEvent.getFilter().getRequestId());
        stream.writeLong(currentFrame.getThreadId());
        // tagged object ID
        Object monitor = monitorEvent.getMonitor();
        stream.writeByte(context.getTag(monitor));
        stream.writeLong(context.getIds().getIdAsLong(monitor));

        // location
        stream.writeByte(currentFrame.getTypeTag());
        stream.writeLong(currentFrame.getClassId());
        stream.writeLong(currentFrame.getMethodId());
        long codeIndex = currentFrame.getCodeIndex();
        stream.writeLong(codeIndex);
        JDWPLogger.log("Sending monitor contended event", JDWPLogger.LogLevel.STEPPING);

        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void addMonitorContendedEnterRequest(int requestId, RequestFilter filter) {
        monitorContendedRequests.put(requestId, filter);
    }

    @Override
    public void removeMonitorContendedEnterRequest(int requestId) {
        monitorContendedRequests.remove(requestId);
    }

    @Override
    public boolean prepareMonitorContended(Object monitor) {
        if (monitorContendedRequests.isEmpty()) {
            return false;
        }
        Object currentThread = context.asGuestThread(Thread.currentThread());
        for (Map.Entry<Integer, RequestFilter> entry : monitorContendedRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (currentThread == filter.getThread()) {
                // monitor is contended on a requested thread
                MonitorEvent event = new MonitorEvent(monitor, filter);
                debuggerController.prepareMonitorContendedEvent(event);
                debuggerController.suspend(context.asGuestThread(Thread.currentThread()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void sendMonitorContendedEnteredEvent(MonitorEvent monitorEvent, Object currentThread, CallFrame currentFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(monitorEvent.getFilter().getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.MONITOR_CONTENDED_ENTERED);
        stream.writeInt(monitorEvent.getFilter().getRequestId());
        stream.writeLong(currentFrame.getThreadId());
        // tagged object ID
        Object monitor = monitorEvent.getMonitor();
        stream.writeByte(context.getTag(monitor));
        stream.writeLong(context.getIds().getIdAsLong(monitor));

        // location
        stream.writeByte(currentFrame.getTypeTag());
        stream.writeLong(currentFrame.getClassId());
        stream.writeLong(currentFrame.getMethodId());
        long codeIndex = currentFrame.getCodeIndex();
        stream.writeLong(codeIndex);
        JDWPLogger.log("Sending monitor contended entered event", JDWPLogger.LogLevel.STEPPING);

        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void addMonitorContendedEnteredRequest(int requestId, RequestFilter filter) {
        monitorContendedEnteredRequests.put(requestId, filter);
    }

    @Override
    public void removeMonitorContendedEnteredRequest(int requestId) {
        monitorContendedEnteredRequests.remove(requestId);
    }

    @Override
    public boolean prepareMonitorContendedEntered(Object monitor) {
        if (monitorContendedEnteredRequests.isEmpty()) {
            return false;
        }
        Object currentThread = context.asGuestThread(Thread.currentThread());
        for (Map.Entry<Integer, RequestFilter> entry : monitorContendedEnteredRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (currentThread == filter.getThread()) {
                // monitor is contended on a requested thread
                MonitorEvent event = new MonitorEvent(monitor, filter);
                debuggerController.prepareMonitorContendedEnteredEvent(event);
                debuggerController.suspend(context.asGuestThread(Thread.currentThread()));
                return true;
            }
        }
        return false;
    }

    public void sendMonitorWaitEvent(Object monitor, long timeout, RequestFilter filter, CallFrame currentFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(filter.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.MONITOR_WAIT);
        stream.writeInt(filter.getRequestId());
        stream.writeLong(currentFrame.getThreadId());
        // tagged object ID
        stream.writeByte(context.getTag(monitor));
        stream.writeLong(context.getIds().getIdAsLong(monitor));

        // location
        stream.writeByte(currentFrame.getTypeTag());
        stream.writeLong(currentFrame.getClassId());
        stream.writeLong(currentFrame.getMethodId());
        long codeIndex = currentFrame.getCodeIndex();
        stream.writeLong(codeIndex);

        // timeout
        stream.writeLong(timeout);
        JDWPLogger.log("Sending monitor wait event", JDWPLogger.LogLevel.STEPPING);

        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void addMonitorWaitRequest(int requestId, RequestFilter filter) {
        monitorWaitRequests.put(requestId, filter);
    }

    @Override
    public void removeMonitorWaitRequest(int requestId) {
        monitorWaitRequests.remove(requestId);
    }

    @Override
    public void monitorWait(Object monitor, long timeout) {
        Object currentThread = context.asGuestThread(Thread.currentThread());
        // always add to current contended monitor on the thread before a wait
        currentContendedMonitor.put(currentThread, monitor);

        if (monitorWaitRequests.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, RequestFilter> entry : monitorWaitRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (currentThread == filter.getThread()) {
                // monitor wait(timeout) is called on a requested thread
                // create the call frame for the caller location of Object.wait(timeout)
                CallFrame frame = context.locateObjectWaitFrame();

                debuggerController.immediateSuspend(currentThread, filter.getSuspendPolicy(), new Callable<Void>() {
                    @Override
                    public Void call() {
                        sendMonitorWaitEvent(monitor, timeout, filter, frame);
                        return null;
                    }
                });
            }
        }
    }

    public void sendMonitorWaitedEvent(Object monitor, boolean timedOut, RequestFilter filter, CallFrame currentFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(filter.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.MONITOR_WAITED);
        stream.writeInt(filter.getRequestId());
        stream.writeLong(currentFrame.getThreadId());
        // tagged object ID
        stream.writeByte(context.getTag(monitor));
        stream.writeLong(context.getIds().getIdAsLong(monitor));

        // location
        stream.writeByte(currentFrame.getTypeTag());
        stream.writeLong(currentFrame.getClassId());
        stream.writeLong(currentFrame.getMethodId());
        long codeIndex = currentFrame.getCodeIndex();
        stream.writeLong(codeIndex);

        // timeout
        stream.writeBoolean(timedOut);
        JDWPLogger.log("Sending monitor wait event", JDWPLogger.LogLevel.STEPPING);

        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void addMonitorWaitedRequest(int requestId, RequestFilter filter) {
        monitorWaitedRequests.put(requestId, filter);
    }

    @Override
    public void removeMonitorWaitedRequest(int requestId) {
        monitorWaitedRequests.remove(requestId);
    }

    @Override
    public void monitorWaited(Object monitor, boolean timedOut) {
        Object currentThread = context.asGuestThread(Thread.currentThread());
        // remove contended monitor from the thread
        currentContendedMonitor.remove(currentThread);

        if (monitorWaitedRequests.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, RequestFilter> entry : monitorWaitedRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (currentThread == filter.getThread()) {
                // monitor wait(timeout) is called on a requested thread
                // create the call frame for the caller location of Object.wait(timeout)
                CallFrame frame = context.locateObjectWaitFrame();

                debuggerController.immediateSuspend(currentThread, filter.getSuspendPolicy(), new Callable<Void>() {
                    @Override
                    public Void call() {
                        sendMonitorWaitedEvent(monitor, timedOut, filter, frame);
                        return null;
                    }
                });
            }
        }
    }

    @Override
    public void addOwnedMonitor(Object monitor) {
        Object guestThread = context.asGuestThread(Thread.currentThread());
        Set<Object> monitors = ownedMonitors.get(guestThread);
        if (monitors == null) {
            monitors = new HashSet<>();
            ownedMonitors.put(guestThread, monitors);
        }
        monitors.add(monitor);
        // also clear contended monitor for the thread
        currentContendedMonitor.remove(guestThread);
    }

    @Override
    public void removeOwnedMonitor(Object monitor) {
        Object guestThread = context.asGuestThread(Thread.currentThread());
        Set<Object> monitors = ownedMonitors.get(guestThread);
        if (monitors != null) {
            monitors.remove(monitor);
        }
    }

    @Override
    public Object[] getOwnedMonitors(Object thread) {
        Set<Object> monitors = ownedMonitors.get(thread);
        if (monitors != null) {
            System.out.println("owned monitors: " + monitors.size());
            for (Object monitor : monitors) {
                System.out.println("monitor: " + monitor);
            }
            return monitors.toArray();
        }
        return new Object[0];
    }

    @Override
    public void addCurrentContendedMonitor(Object monitor) {
        Object guestThread = context.asGuestThread(Thread.currentThread());
        currentContendedMonitor.put(guestThread, monitor);
    }

    @Override
    public Object getCurrentContendedMonitor(Object guestThread) {
        return currentContendedMonitor.get(guestThread);
    }

    @Override
    public void classUnloaded(KlassRef klass) {
        throw new NotImplementedException();
    }

    @Override
    public void threadStarted(Object thread) {
        if (connection == null || threadStartedRequestId == 0) {
            return;
        }
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(threadStartSuspendPolicy);
        suspend(threadStartSuspendPolicy, thread);
        stream.writeInt(1); // # events in reply
        stream.writeByte(RequestedJDWPEvents.THREAD_START);
        stream.writeInt(threadStartedRequestId);
        stream.writeLong(ids.getIdAsLong(thread));
        JDWPLogger.log("sending thread started event for thread: %s", JDWPLogger.LogLevel.THREAD, context.getThreadName(thread));
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void threadDied(Object thread) {
        if (connection == null || threadDeathRequestId == 0) {
            return;
        }
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(threadDeathSuspendPolicy);
        suspend(threadDeathSuspendPolicy, thread);
        stream.writeInt(1); // # events in reply
        stream.writeByte(RequestedJDWPEvents.THREAD_DEATH);
        stream.writeInt(threadDeathRequestId);
        stream.writeLong(ids.getIdAsLong(thread));
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void vmStarted(Object mainThread) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(SuspendStrategy.NONE);
        stream.writeInt(1);
        stream.writeByte(RequestedJDWPEvents.VM_START);
        stream.writeInt(vmStartRequestId != -1 ? vmStartRequestId : 0);
        stream.writeLong(context.getIds().getIdAsLong(mainThread));
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void vmDied() {
        if (connection == null) {
            return;
        }
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(SuspendStrategy.NONE);
        stream.writeInt(1);
        stream.writeByte(RequestedJDWPEvents.VM_DEATH);
        stream.writeInt(vmDeathRequestId != -1 ? vmDeathRequestId : 0);
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void addClassUnloadRequestId(int id) {
        // not implemented yet
        JDWPLogger.log("class unload events not yet implemented!", JDWPLogger.LogLevel.ALL);
    }

    @Override
    public void addThreadStartedRequestId(int id, byte suspendPolicy) {
        JDWPLogger.log("Adding thread start listener", JDWPLogger.LogLevel.THREAD);
        this.threadStartedRequestId = id;
        this.threadStartSuspendPolicy = suspendPolicy;
    }

    @Override
    public void addThreadDiedRequestId(int id, byte suspendPolicy) {
        JDWPLogger.log("Adding thread death listener", JDWPLogger.LogLevel.THREAD);
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
    public void addVMDeathRequest(int id) {
        this.vmStartRequestId = id;
    }

    @Override
    public void addVMStartRequest(int id) {
        this.vmDeathRequestId = id;
    }

    @Override
    public void holdEvents() {
        holdEvents = true;
    }

    @Override
    public void releaseEvents() {
        holdEvents = false;
        // queue all held events for sending
        for (PacketStream heldEvent : heldEvents) {
            connection.queuePacket(heldEvent);
        }
    }

    private void suspend(byte suspendPolicy, Object thread) {
        switch (suspendPolicy) {
            case SuspendStrategy.NONE:
                return;
            case SuspendStrategy.EVENT_THREAD:
                debuggerController.suspend(thread);
                return;
            case SuspendStrategy.ALL:
                debuggerController.suspendAll();
                return;
        }
    }
}
