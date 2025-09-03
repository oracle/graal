/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.jdwp.impl.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.impl.ClassPrepareRequest;
import com.oracle.truffle.espresso.jdwp.impl.DebuggerController;
import com.oracle.truffle.espresso.jdwp.impl.EventInfo;
import com.oracle.truffle.espresso.jdwp.impl.FieldBreakpointEvent;
import com.oracle.truffle.espresso.jdwp.impl.FieldBreakpointInfo;
import com.oracle.truffle.espresso.jdwp.impl.JDWP;
import com.oracle.truffle.espresso.jdwp.impl.MethodBreakpointEvent;
import com.oracle.truffle.espresso.jdwp.impl.MethodBreakpointInfo;
import com.oracle.truffle.espresso.jdwp.impl.MonitorEvent;
import com.oracle.truffle.espresso.jdwp.impl.PacketStream;
import com.oracle.truffle.espresso.jdwp.impl.RequestFilter;
import com.oracle.truffle.espresso.jdwp.impl.RequestedJDWPEvents;
import com.oracle.truffle.espresso.jdwp.impl.SocketConnection;
import com.oracle.truffle.espresso.jdwp.impl.SteppingInfo;
import com.oracle.truffle.espresso.jdwp.impl.SuspendStrategy;
import com.oracle.truffle.espresso.jdwp.impl.TypeTag;

public final class VMEventListenerImpl implements VMEventListener {

    public static final InteropLibrary UNCACHED = InteropLibrary.getUncached();

    private final Map<Integer, ClassPrepareRequest> classPrepareRequests = new ConcurrentHashMap<>();
    private final Map<Integer, BreakpointInfo> breakpointRequests = new ConcurrentHashMap<>();
    private final Map<Integer, RequestFilter> monitorContendedRequests = new ConcurrentHashMap<>();
    private final Map<Integer, RequestFilter> monitorContendedEnteredRequests = new ConcurrentHashMap<>();
    private final Map<Integer, RequestFilter> monitorWaitRequests = new ConcurrentHashMap<>();
    private final Map<Integer, RequestFilter> monitorWaitedRequests = new ConcurrentHashMap<>();
    private final Map<Integer, FieldRef> fieldRequests = new ConcurrentHashMap<>();
    private final Collection<Consumer<KlassRef>> classConsumers = new CopyOnWriteArrayList<>();

    // The connection field is null only until the connection is established. Thus, we need
    // to guard any attempted usage prior to that, e.g. vm dies event.
    private SocketConnection connection;
    private JDWPContext context;
    private Ids<Object> ids;
    private DebuggerController debuggerController;
    private volatile boolean holdEvents;

    private int threadStartedRequestId;
    private int threadDeathRequestId;
    private byte threadStartSuspendPolicy;
    private byte threadDeathSuspendPolicy;
    private int vmDeathRequestId;
    private byte vmDeathSuspendPolicy = SuspendStrategy.NONE;
    private int vmStartRequestId;
    private final List<PacketStream> heldEvents = new ArrayList<>();
    private final Map<Object, Object> currentContendedMonitor = new ConcurrentHashMap<>();
    private Object initialThread;

    public void activate(Object mainThread, DebuggerController control, JDWPContext jdwpContext) {
        this.initialThread = mainThread;
        this.debuggerController = control;
        this.context = jdwpContext;
        this.ids = context.getIds();
    }

    public void replaceController(DebuggerController newController) {
        this.debuggerController = newController;
    }

    @Override
    public void onDetach() {
        // free up request, to avoid attempting to send anything further
        removeThreadStartedRequestId();
        removeThreadDiedRequestId();
        this.vmDeathRequestId = 0;
        this.vmDeathSuspendPolicy = SuspendStrategy.NONE;
        classPrepareRequests.clear();
        breakpointRequests.clear();
        monitorContendedRequests.clear();
        monitorContendedEnteredRequests.clear();
        monitorWaitedRequests.clear();
        monitorWaitRequests.clear();
        removeFieldRequests();

        /*
         * We don't null the connection field here, since there's a race condition between preparing
         * a packet based on the just cleared event requests and actually passing those packets to
         * the connection. It is the responsibility of the underlying connection object to implement
         * the required synchronization. Hence, with the current design we accept that some
         * intermediate packets might be prepared and passed into a void connection, which is OK
         * from a functional perspective, but we do leak the useless connection object.
         */
    }

    public void setConnection(SocketConnection connection) {
        this.connection = connection;
    }

    @Override
    public void addClassConsumer(Consumer<KlassRef> classConsumer) {
        classConsumers.add(classConsumer);
    }

    @Override
    public void removeClassConsumer(Consumer<KlassRef> classConsumer) {
        classConsumers.remove(classConsumer);
    }

    @Override
    public void addClassPrepareRequest(ClassPrepareRequest request) {
        classPrepareRequests.put(request.getRequestId(), request);
    }

    @Override
    @TruffleBoundary
    public void removeClassPrepareRequest(int requestId) {
        classPrepareRequests.remove(requestId);
    }

    @Override
    public void addBreakpointRequest(int requestId, BreakpointInfo info) {
        breakpointRequests.put(requestId, info);
    }

    @Override
    @TruffleBoundary
    public void removeBreakpointRequest(int requestId) {
        BreakpointInfo remove = breakpointRequests.remove(requestId);
        if (remove != null) {
            Breakpoint[] breakpoints = remove.getBreakpoints();
            for (Breakpoint breakpoint : breakpoints) {
                breakpoint.dispose();
            }
        }
    }

    @Override
    public void clearAllBreakpointRequests() {
        classConsumers.clear();
        breakpointRequests.clear();
    }

    @Override
    public void addFieldRequest(FieldBreakpointInfo info) {
        FieldRef field = info.getField();
        field.addFieldBreakpointInfo(info);
        fieldRequests.put(info.getRequestId(), field);
    }

    @Override
    public void removeFieldRequest(int requestId, FieldRef field) {
        field.removeFieldBreakpointInfo(requestId);
        fieldRequests.remove(requestId, field);
    }

    private void removeFieldRequests() {
        for (Map.Entry<Integer, FieldRef> entry : fieldRequests.entrySet()) {
            entry.getValue().removeFieldBreakpointInfo(entry.getKey());
        }
        fieldRequests.clear();
    }

    @Override
    @TruffleBoundary
    public boolean onFieldModification(FieldRef field, Node node, Object receiver, Object value) {
        boolean active = false;
        for (FieldBreakpoint info : field.getFieldBreakpointInfos()) {
            if (info.isModificationBreakpoint()) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareFieldBreakpoint(new FieldBreakpointEvent((FieldBreakpointInfo) info, receiver, value));
                debuggerController.suspendHere(node);
                active = true;
            }
        }
        return active;
    }

    @Override
    @TruffleBoundary
    public boolean onFieldAccess(FieldRef field, Node node, Object receiver) {
        boolean active = false;
        for (FieldBreakpoint info : field.getFieldBreakpointInfos()) {
            if (info.isAccessBreakpoint()) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareFieldBreakpoint(new FieldBreakpointEvent((FieldBreakpointInfo) info, receiver));
                debuggerController.suspendHere(node);
                active = true;
            }
        }
        return active;
    }

    @Override
    @TruffleBoundary
    public boolean onMethodEntry(MethodRef method, Node node, Object scope) {
        boolean active = false;
        // collect variable information from scope
        List<MethodVariable> variables = new ArrayList<>(1);
        try {
            if (UNCACHED.hasMembers(scope)) {
                Object identifiers = UNCACHED.getMembers(scope);
                if (UNCACHED.hasArrayElements(identifiers)) {
                    long size = UNCACHED.getArraySize(identifiers);
                    for (long i = 0; i < size; i++) {
                        String identifier = (String) UNCACHED.readArrayElement(identifiers, i);
                        Object value = UNCACHED.readMember(scope, identifier);
                        variables.add(new MethodVariable(identifier, value));
                    }
                }
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException | UnknownIdentifierException e) {
            // not able to fetch locals, so leave variables list empty
        }

        for (MethodHook hook : method.getMethodHooks()) {
            // pass on the variables to the method entry hook
            if (hook.onMethodEnter(method, variables.toArray(new MethodVariable[variables.size()]))) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareMethodBreakpoint(new MethodBreakpointEvent((MethodBreakpointInfo) hook, null));
                debuggerController.suspendHere(node);
                active = true;

                switch (hook.getKind()) {
                    case ONE_TIME:
                        if (hook.hasFired()) {
                            method.removeMethodHook(hook);
                        }
                        break;
                    case INDEFINITE:
                        // leave the hook active
                        break;
                }
            }
        }
        return active;
    }

    @Override
    @TruffleBoundary
    public boolean onMethodReturn(MethodRef method, Node node, Object returnValue) {
        boolean active = false;
        for (MethodHook hook : method.getMethodHooks()) {
            if (hook.onMethodExit(method, returnValue)) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareMethodBreakpoint(new MethodBreakpointEvent((MethodBreakpointInfo) hook, returnValue));
                debuggerController.suspendHere(node);
                active = true;

                switch (hook.getKind()) {
                    case ONE_TIME:
                        if (hook.hasFired()) {
                            method.removeMethodHook(hook);
                        }
                        break;
                    case INDEFINITE:
                        // leave the hook active
                        break;
                }
            }
        }
        return active;
    }

    @Override
    @TruffleBoundary
    public void classPrepared(KlassRef klass, Object prepareThread) {
        for (Consumer<KlassRef> c : classConsumers) {
            c.accept(klass);
        }
        // check if event should be reported based on the current patterns, otherwise return early
        if (classPrepareRequests.isEmpty()) {
            return;
        }

        ArrayList<ClassPrepareRequest> toSend = new ArrayList<>();
        byte suspendPolicy = SuspendStrategy.NONE;

        Collection<ClassPrepareRequest> prepareRequests = classPrepareRequests.values();
        if (!prepareRequests.isEmpty()) {
            EventInfo event = new EventInfo.Klass(klass, prepareThread);
            for (ClassPrepareRequest cpr : prepareRequests) {
                if (cpr.isHit(event)) {
                    toSend.add(cpr);
                    byte cprPolicy = cpr.getSuspendPolicy();
                    if (cprPolicy == SuspendStrategy.ALL) {
                        suspendPolicy = SuspendStrategy.ALL;
                    } else if (cprPolicy == SuspendStrategy.EVENT_THREAD && suspendPolicy != SuspendStrategy.ALL) {
                        suspendPolicy = SuspendStrategy.EVENT_THREAD;
                    }
                }
                if (!cpr.isActive()) {
                    classPrepareRequests.remove(cpr.getRequestId());
                }
            }
        }
        if (!toSend.isEmpty()) {
            PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
            assert connection != null;
            stream.writeByte(suspendPolicy);
            stream.writeInt(toSend.size());

            for (ClassPrepareRequest cpr : toSend) {
                stream.writeByte(RequestedJDWPEvents.CLASS_PREPARE);
                stream.writeInt(cpr.getRequestId());
                stream.writeLong(ids.getIdAsLong(prepareThread));
                stream.writeByte(TypeTag.getKind(klass));
                stream.writeLong(ids.getIdAsLong(klass));
                stream.writeString(klass.getTypeAsString());
                stream.writeInt(klass.getStatus());
            }
            if (suspendPolicy != SuspendStrategy.NONE) {
                // the current thread has just prepared the class
                // so we must suspend according to suspend policy
                debuggerController.immediateSuspend(prepareThread, suspendPolicy, () -> {
                    if (holdEvents) {
                        heldEvents.add(stream);
                    } else {
                        debuggerController.fine(() -> "SENDING CLASS PREPARE EVENT FOR KLASS: " + klass.getNameAsString() + " WITH THREAD " + context.getThreadName(prepareThread));
                        connection.queuePacket(stream);
                    }
                    return null;
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

    @Override
    public void breakpointHit(BreakpointInfo info, CallFrame frame, Object currentThread) {
        assert connection != null;
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
        debuggerController.fine(() -> "Sending breakpoint hit event in thread: " + context.getThreadName(currentThread) + " with suspension policy: " + info.getSuspendPolicy());
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void methodBreakpointHit(MethodBreakpointEvent methodEvent, Object currentThread, CallFrame frame) {
        assert connection != null;
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
        assert connection != null;
        PacketStream stream = writeSharedFieldInformation(event, currentThread, callFrame, RequestedJDWPEvents.FIELD_ACCESS);
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void fieldModificationBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame) {
        assert connection != null;
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
        assert connection != null;
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
        stream.writeLong(ids.getIdAsLong(top.getMethod()));
        stream.writeLong(top.getCodeIndex());

        // exception
        stream.writeByte(TagConstants.OBJECT);
        stream.writeLong(context.getIds().getIdAsLong(exception));

        // catch-location
        boolean caught = false;
        for (CallFrame callFrame : callFrames) {
            MethodRef method = callFrame.getMethod();
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
        assert connection != null;
        if (info.isPopFrames()) {
            // send reply packet when "step" is completed
            PacketStream reply = new PacketStream().replyPacket().id(info.getRequestId());
            debuggerController.fine(() -> "Sending pop frames reply packet");
            if (holdEvents) {
                heldEvents.add(reply);
            } else {
                connection.queuePacket(reply);
            }
        } else {
            // single step completed events
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
            long codeIndex = currentFrame.getCodeIndex();
            stream.writeLong(codeIndex);
            debuggerController.fine(() -> "Sending step completed event");

            if (holdEvents) {
                heldEvents.add(stream);
            } else {
                connection.queuePacket(stream);
            }
        }
    }

    private void sendMonitorContendedEnterEvent(MonitorEvent monitorEvent, CallFrame currentFrame) {
        assert connection != null;
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
        debuggerController.fine(() -> "Sending monitor contended event");

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

    private void sendMonitorContendedEnteredEvent(MonitorEvent monitorEvent, CallFrame currentFrame) {
        assert connection != null;
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
        debuggerController.fine(() -> "Sending monitor contended entered event");

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

    public void sendMonitorWaitEvent(Object monitor, long timeout, RequestFilter filter, CallFrame currentFrame) {
        assert connection != null;
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
        debuggerController.fine(() -> "Sending monitor wait event");

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
    @TruffleBoundary
    public void monitorWait(Object monitor, long timeout) {
        if (context == null) {
            return;
        }
        Object guestThread = context.asGuestThread(Thread.currentThread());
        // a call to wait marks the monitor as contended
        currentContendedMonitor.put(guestThread, monitor);

        if (monitorWaitRequests.isEmpty()) {
            return;
        }
        CallFrame frame = context.locateObjectWaitFrame();
        EventInfo event = new EventInfo.Frame(context, frame, guestThread);
        for (Map.Entry<Integer, RequestFilter> entry : monitorWaitRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (filter.isHit(event)) {
                // monitor wait(timeout) is called on a requested thread
                // create the call frame for the caller location of Object.wait(timeout)
                debuggerController.immediateSuspend(guestThread, filter.getSuspendPolicy(), () -> {
                    sendMonitorWaitEvent(monitor, timeout, filter, frame);
                    return null;
                });
            }
            if (!filter.isActive()) {
                monitorWaitRequests.remove(filter.getRequestId());
            }
        }
    }

    private void sendMonitorWaitedEvent(Object monitor, boolean timedOut, RequestFilter filter, CallFrame currentFrame) {
        assert connection != null;
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
        debuggerController.fine(() -> "Sending monitor wait event");

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
    @TruffleBoundary
    public void monitorWaited(Object monitor, boolean timedOut) {
        if (context == null) {
            return;
        }
        Object currentThread = context.asGuestThread(Thread.currentThread());
        // remove contended monitor from the thread
        currentContendedMonitor.remove(currentThread);

        if (monitorWaitedRequests.isEmpty()) {
            return;
        }
        CallFrame frame = context.locateObjectWaitFrame();
        EventInfo event = new EventInfo.Frame(context, frame, currentThread);
        for (Map.Entry<Integer, RequestFilter> entry : monitorWaitedRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (filter.isHit(event)) {
                // monitor wait(timeout) is called on a requested thread
                // create the call frame for the caller location of Object.wait(timeout)

                debuggerController.immediateSuspend(currentThread, filter.getSuspendPolicy(), new Callable<Void>() {
                    @Override
                    public Void call() {
                        sendMonitorWaitedEvent(monitor, timedOut, filter, frame);
                        return null;
                    }
                });
            }
            if (!filter.isActive()) {
                monitorWaitedRequests.remove(filter.getRequestId());
            }
        }
    }

    @Override
    @TruffleBoundary
    public void onContendedMonitorEnter(Object monitor) {
        if (context == null) {
            return;
        }
        Object guestThread = context.asGuestThread(Thread.currentThread());
        currentContendedMonitor.put(guestThread, monitor);

        if (monitorContendedRequests.isEmpty()) {
            return;
        }

        final CallFrame topFrame = context.getStackTrace(guestThread)[0];
        EventInfo event = new EventInfo.Frame(context, topFrame, guestThread);
        for (Map.Entry<Integer, RequestFilter> entry : monitorContendedRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (filter.isHit(event)) {
                // monitor is contended on a requested thread
                MonitorEvent mevent = new MonitorEvent(monitor, filter);

                debuggerController.immediateSuspend(guestThread, filter.getSuspendPolicy(), () -> {
                    sendMonitorContendedEnterEvent(mevent, topFrame);
                    return null;
                });
            }
            if (!filter.isActive()) {
                monitorContendedRequests.remove(filter.getRequestId());
            }
        }
    }

    @Override
    @TruffleBoundary
    public void onContendedMonitorEntered(Object monitor) {
        if (context == null) {
            return;
        }
        Object guestThread = context.asGuestThread(Thread.currentThread());
        currentContendedMonitor.remove(guestThread);

        if (monitorContendedEnteredRequests.isEmpty()) {
            return;
        }

        final CallFrame topFrame = context.getStackTrace(guestThread)[0];
        EventInfo event = new EventInfo.Frame(context, topFrame, guestThread);
        for (Map.Entry<Integer, RequestFilter> entry : monitorContendedEnteredRequests.entrySet()) {
            RequestFilter filter = entry.getValue();
            if (filter.isHit(event)) {
                // monitor is contended on a requested thread
                MonitorEvent mevent = new MonitorEvent(monitor, filter);

                debuggerController.immediateSuspend(guestThread, filter.getSuspendPolicy(), () -> {
                    sendMonitorContendedEnteredEvent(mevent, topFrame);
                    return null;
                });
            }
            if (!filter.isActive()) {
                monitorContendedEnteredRequests.remove(filter.getRequestId());
            }
        }
    }

    @Override
    public Object getCurrentContendedMonitor(Object guestThread) {
        return currentContendedMonitor.get(guestThread);
    }

    @Override
    public void classUnloaded(KlassRef klass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void threadStarted(Object thread) {
        if (threadStartedRequestId == 0) {
            return;
        }
        // if we have a thread start request ID, we know the connection was established
        assert connection != null;

        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(threadStartSuspendPolicy);
        suspend(threadStartSuspendPolicy, thread);
        stream.writeInt(1); // # events in reply
        stream.writeByte(RequestedJDWPEvents.THREAD_START);
        stream.writeInt(threadStartedRequestId);
        stream.writeLong(ids.getIdAsLong(thread));
        debuggerController.fine(() -> "sending thread started event for thread: " + context.getThreadName(thread));
        if (holdEvents) {
            heldEvents.add(stream);
        } else {
            connection.queuePacket(stream);
        }
    }

    @Override
    public void threadDied(Object thread) {
        if (threadDeathRequestId == 0) {
            return;
        }
        // if we have a thread death request ID, we know the connection was established
        assert connection != null;

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

    public void vmStarted(boolean suspend) {
        // This event can only be sent when we know the connection was established
        assert connection != null;
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(suspend ? SuspendStrategy.ALL : SuspendStrategy.NONE);
        stream.writeInt(1);
        stream.writeByte(RequestedJDWPEvents.VM_START);
        stream.writeInt(vmStartRequestId != -1 ? vmStartRequestId : 0);
        stream.writeLong(context.getIds().getIdAsLong(initialThread));
        connection.queuePacket(stream);
    }

    @Override
    public boolean vmDied() {
        // In case the VM dies for any reason before we have the connection, just bail out here.
        if (connection == null) {
            return false;
        }
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(vmDeathSuspendPolicy);
        if (vmDeathRequestId != 0) {
            stream.writeInt(2);
            // requested event
            stream.writeByte(RequestedJDWPEvents.VM_DEATH);
            stream.writeInt(vmDeathRequestId);
            // automatic event
        } else {
            // only automatic event to send
            stream.writeInt(1);
        }
        stream.writeByte(RequestedJDWPEvents.VM_DEATH);
        stream.writeInt(0);
        connection.queuePacket(stream);
        return vmDeathSuspendPolicy != SuspendStrategy.NONE;
    }

    @Override
    public void addClassUnloadRequestId(int id) {
        // not implemented yet
        debuggerController.fine(() -> "class unload events not yet implemented!");
    }

    @Override
    public void addThreadStartedRequestId(int id, byte suspendPolicy) {
        debuggerController.fine(() -> "Adding thread start listener");
        this.threadStartedRequestId = id;
        this.threadStartSuspendPolicy = suspendPolicy;
    }

    @Override
    public void addThreadDiedRequestId(int id, byte suspendPolicy) {
        debuggerController.fine(() -> "Adding thread death listener");
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
        }
    }
}
