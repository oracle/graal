/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VMEventListenerImpl implements VMEventListener {

    private final SocketConnection connection;
    private final Ids<Object> ids;
    private final JDWPContext context;
    private final JDWPDebuggerController debuggerController;
    private HashMap<Integer, ClassPrepareRequest> classPrepareRequests = new HashMap<>();
    private HashMap<Integer, BreakpointInfo> breakpointRequests = new HashMap<>();

    private int threadStartedRequestId;
    private int threadDeathRequestId;
    private int vmDeathRequestId;
    private int vmStartRequestId;

    private final StableBoolean fieldBreakpointsActive = new StableBoolean(false);

    public VMEventListenerImpl(SocketConnection connection, JDWPContext context, JDWPDebuggerController controller) {
        this.connection = connection;
        this.ids = context.getIds();
        this.context = context;
        this.debuggerController = controller;
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
                    classPrepared(klass, thread);
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
        Breakpoint breakpoint = remove.getBreakpoint();
        breakpoint.dispose();
    }

    private static volatile int fieldBreakpointCount;

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
        for (FieldBreakpointInfo info : field.getFieldBreakpointInfos()) {
            if (info.isModificationBreakpoint()) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareFieldBreakpoint(new FieldBreakpointEvent(info, receiver, value));
                debuggerController.suspend(context.getHost2GuestThread(Thread.currentThread()));
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
        for (FieldBreakpointInfo info : field.getFieldBreakpointInfos()) {
            if (info.isAccessBreakpoint()) {
                // OK, tell the Debug API to suspend the thread now
                debuggerController.prepareFieldBreakpoint(new FieldBreakpointEvent(info, receiver));
                debuggerController.suspend(context.getHost2GuestThread(Thread.currentThread()));
                return true;
            }
        }
        return false;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void classPrepared(KlassRef klass, Object guestThread) {
        // prepare the event and ship
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        // check if event should be reported based on the current patterns
        String dotName = klass.getNameAsString().replace('/', '.');
        ClassPrepareRequest[] allClassPrepareRequests = getAllClassPrepareRequests();
        ArrayList<ClassPrepareRequest> toSend = new ArrayList<>();

        for (ClassPrepareRequest cpr : allClassPrepareRequests) {
            Pattern[] patterns = cpr.getPatterns();
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(dotName.replace('$', '_'));

                if (matcher.matches()) {
                    toSend.add(cpr);
                }
            }
        }

        if (!toSend.isEmpty()) {
            // TODO(Gregersen) - we should suspend the event thread to be correct
            stream.writeByte(SuspendStrategy.NONE);
            stream.writeInt(toSend.size());

            for (ClassPrepareRequest cpr : toSend) {
                stream.writeByte(RequestedJDWPEvents.CLASS_PREPARE);
                stream.writeInt(cpr.getRequestId());
                stream.writeLong(ids.getIdAsLong(guestThread));
                stream.writeByte(TypeTag.CLASS);
                stream.writeLong(ids.getIdAsLong(klass));
                stream.writeString(klass.getTypeAsString());
                // only send PREPARED status for class prepare events.
                // TODO(Gregersen) - when sending when class is initialized already
                // TODO(Gregersen) using ClassStatusConstants.INITIALIZED the debugger doesn't submit a breakpoint!
                stream.writeInt(ClassStatusConstants.PREPARED);
                classPrepareRequests.remove(cpr.getRequestId());
            }
            connection.queuePacket(stream);
            // give the debugger a little time to send breakpoint requests
            try {
                Thread.sleep(50);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private ClassPrepareRequest[] getAllClassPrepareRequests() {
        Collection<ClassPrepareRequest> values = classPrepareRequests.values();
        return new ArrayList<>(values).toArray(new ClassPrepareRequest[values.size()]);
    }

    @Override
    public void breakpointHIt(BreakpointInfo info, Object currentThread) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.BREAKPOINT);
        stream.writeInt(info.getRequestId());
        long threadId = ids.getIdAsLong(currentThread);
        stream.writeLong(threadId);

        // location
        stream.writeByte(info.getTypeTag());
        stream.writeLong(info.getClassId());
        stream.writeLong(info.getMethodId());
        stream.writeLong(info.getBci());
        JDWPLogger.log("Sending breakpoint hit event in thread: " + context.getThreadName(currentThread) + " with suspension policy: " + info.getSuspendPolicy(), JDWPLogger.LogLevel.STEPPING);
        connection.queuePacket(stream);
    }

    @Override
    public void fieldAccessBreakpointHit(FieldBreakpointEvent event, Object currentThread, JDWPCallFrame callFrame) {
        PacketStream stream = writeSharedFieldInformation(event, currentThread, callFrame, RequestedJDWPEvents.FIELD_ACCESS);
        connection.queuePacket(stream);
    }

    @Override
    public void fieldModificationBreakpointHit(FieldBreakpointEvent event, Object currentThread, JDWPCallFrame callFrame) {
        PacketStream stream = writeSharedFieldInformation(event, currentThread, callFrame, RequestedJDWPEvents.FIELD_MODIFICATION);

        // value about to be set
        Object value = event.getValue();
        byte tag = event.getInfo().getField().getTagConstant();
        if (tag == TagConstants.OBJECT) {
            tag = context.getTag(value);
        }
        JDWP.writeValue(tag, value, stream, true, context);

        connection.queuePacket(stream);
    }

    private PacketStream writeSharedFieldInformation(FieldBreakpointEvent event, Object currentThread, JDWPCallFrame callFrame, byte fieldModification) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        FieldBreakpointInfo info = event.getInfo();

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(fieldModification);
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
        stream.writeByte(klass.getTagConstant());
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
    public void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, JDWPCallFrame callFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(info.getSuspendPolicy());
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.EXCEPTION);
        stream.writeInt(info.getRequestId());
        stream.writeLong(ids.getIdAsLong(currentThread));

        // location
        stream.writeByte(callFrame.getTypeTag());
        stream.writeLong(callFrame.getClassId());
        stream.writeLong(callFrame.getMethodId());
        stream.writeLong(callFrame.getCodeIndex());

        // exception
        stream.writeByte(TagConstants.OBJECT);
        stream.writeLong(context.getIds().getIdAsLong(exception));

        // catch-location. TODO(Gregersen) - figure out how to implement this
        stream.writeByte((byte) 1);
        stream.writeLong(0);
        stream.writeLong(0);
        stream.writeLong(0);
        connection.queuePacket(stream);
    }

    @Override
    public void stepCompleted(int commandRequestId, JDWPCallFrame currentFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(SuspendStrategy.EVENT_THREAD); // TODO(Gregersen) - implemented suspend policies
        stream.writeInt(1); // # events in reply

        stream.writeByte(RequestedJDWPEvents.SINGLE_STEP);
        stream.writeInt(commandRequestId);
        stream.writeLong(currentFrame.getThreadId());

        // location
        stream.writeByte(currentFrame.getTypeTag());
        stream.writeLong(currentFrame.getClassId());
        stream.writeLong(currentFrame.getMethodId());
        stream.writeLong(currentFrame.getCodeIndex());
        //System.out.println("sending step completed command at index: " + currentFrame.getCodeIndex());
        connection.queuePacket(stream);
    }

    @Override
    public void classUnloaded(KlassRef klass) {
        // TODO(Gregersen) - not implemented yet
    }

    @Override
    public void threadStarted(Object thread) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(SuspendStrategy.NONE);
        stream.writeInt(1); // # events in reply
        stream.writeByte(RequestedJDWPEvents.THREAD_START);
        stream.writeInt(threadStartedRequestId);
        stream.writeLong(ids.getIdAsLong(thread));
        JDWPLogger.log("sending thread started event for thread: " + context.getThreadName(thread), JDWPLogger.LogLevel.THREAD);
        connection.queuePacket(stream);
    }

    @Override
    public void threadDied(Object thread) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(SuspendStrategy.NONE);
        stream.writeInt(1); // # events in reply
        stream.writeByte(RequestedJDWPEvents.THREAD_DEATH);
        stream.writeInt(threadDeathRequestId);
        stream.writeLong(ids.getIdAsLong(thread));
        connection.queuePacket(stream);
    }

    @Override
    public void vmStarted(Object mainThread) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(SuspendStrategy.NONE);
        stream.writeInt(1);
        stream.writeByte(RequestedJDWPEvents.VM_START);
        stream.writeInt(vmStartRequestId != -1 ? vmStartRequestId : 0);
        stream.writeLong(context.getIds().getIdAsLong(mainThread));
        connection.queuePacket(stream);
    }

    @Override
    public void vmDied() {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);
        stream.writeByte(SuspendStrategy.NONE);
        stream.writeInt(1);
        stream.writeByte(RequestedJDWPEvents.VM_DEATH);
        stream.writeInt(vmDeathRequestId != -1 ? vmDeathRequestId : 0);
        connection.queuePacket(stream);
    }

    @Override
    public void addClassUnloadRequestId(int id) {
        // TODO(Gregersen) - not implemented yet
    }

    @Override
    public void addThreadStartedRequestId(int id) {
        JDWPLogger.log("Adding thread start listener", JDWPLogger.LogLevel.THREAD);
        this.threadStartedRequestId = id;
    }

    @Override
    public void addThreadDiedRequestId(int id) {
        JDWPLogger.log("Adding thread death listener", JDWPLogger.LogLevel.THREAD);
        this.threadDeathRequestId = id;
    }

    @Override
    public void addVMDeathRequest(int id) {
        this.vmStartRequestId = id;
    }

    @Override
    public void addVMStartRequest(int id) {
        this.vmDeathRequestId = id;
    }
}
