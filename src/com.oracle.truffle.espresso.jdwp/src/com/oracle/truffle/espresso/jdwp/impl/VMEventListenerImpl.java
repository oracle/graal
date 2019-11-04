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

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;

public class VMEventListenerImpl implements VMEventListener {

    private final SocketConnection connection;
    private final Ids ids;
    private final JDWPContext context;
    private HashMap<Integer, ClassPrepareRequest> classPrepareRequests = new HashMap<>();
    private HashMap<Integer, BreakpointInfo> breakpointRequests = new HashMap<>();
    private int threadStartedRequestId;
    private int threadDeathRequestId;

    public VMEventListenerImpl(SocketConnection connection, JDWPContext context) {
        this.connection = connection;
        this.ids = context.getIds();
        this.context = context;
    }

    @Override
    public Callable addClassPrepareRequest(ClassPrepareRequest request) {
        classPrepareRequests.put(request.getRequestId(), request);
        // check if the class has been already prepared and send the event

        // optimize for fully qualified class name pattern
        String pattern = request.getPattern().pattern();
        KlassRef[] klasses = context.findLoadedClass(pattern.replace('.', '/'));
        if (klasses.length > 0) {
            for (KlassRef klass : klasses) {
                // great, we can simply send a class prepare event for the class
                return getPreparedCallable(request, klass);
            }
        } else {
            KlassRef[] allLoadedClasses = context.getAllLoadedClasses();
            for (KlassRef klass : allLoadedClasses) {
                for (ClassPrepareRequest cpr : getAllClassPrepareRequests()) {
                    String dotName = klass.getNameAsString().replace('/', '.');
                    Matcher matcher = cpr.getPattern().matcher(dotName);

                    if (matcher.matches()) {
                        return getPreparedCallable(request, klass);
                    }
                }
            }
        }
        return null;
    }

    private Callable getPreparedCallable(ClassPrepareRequest request, KlassRef klass) {
        return new Callable() {
            @Override
            public Object call() throws Exception {
                Object thread = klass.getPrepareThread();
                if (request.getThread() == null || request.getThread() == thread) {
                    classPrepared(klass, thread);
                }
                return null;
            }
        };
    }

    @Override
    public void removeClassPrepareRequest(int requestId) {
        classPrepareRequests.remove(requestId);
    }

    @Override
    public void addBreakpointRequest(int requestId, BreakpointInfo info) {
        breakpointRequests.put(requestId, info);
    }

    @Override
    public void removeBreakpointRequest(int requestId) {
        BreakpointInfo remove = breakpointRequests.remove(requestId);
        Breakpoint breakpoint = remove.getBreakpoint();
        breakpoint.dispose();
    }

    @Override
    public void classPrepared(KlassRef klass, Object guestThread) {
        // prepare the event and ship
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        // check if event should be reported based on the current patterns
        String dotName = klass.getNameAsString().replace('/', '.');
        ClassPrepareRequest[] allClassPrepareRequests = getAllClassPrepareRequests();
        ArrayList<ClassPrepareRequest> toSend = new ArrayList<>();

        for (ClassPrepareRequest cpr : allClassPrepareRequests) {
            Matcher matcher = cpr.getPattern().matcher(dotName);

            if (matcher.matches()) {
                toSend.add(cpr);
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

        stream.writeByte(SuspendStrategy.EVENT_THREAD);
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
        connection.queuePacket(stream);
    }

    @Override
    public void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, JDWPCallFrame callFrame) {
        PacketStream stream = new PacketStream().commandPacket().commandSet(64).command(100);

        stream.writeByte(SuspendStrategy.EVENT_THREAD);
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
        //System.out.println("Thread: " + thread + " started based on request: " + threadStartedRequestId) ;
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
    public void addClassUnloadRequestId(int id) {
        // TODO(Gregersen) - not implemented yet
    }

    @Override
    public void addThreadStartedRequestId(int id) {
        this.threadStartedRequestId = id;
    }

    @Override
    public void addThreadDiedRequestId(int id) {
        this.threadDeathRequestId = id;
    }
}
