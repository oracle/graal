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
package com.oracle.truffle.espresso.jdwp.impl;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.VMEventListener;

public final class RequestedJDWPEvents {

    public static final byte SINGLE_STEP = 1;
    public static final byte BREAKPOINT = 2;
    public static final byte FRAME_POP = 3;
    public static final byte EXCEPTION = 4;
    public static final byte USER_DEFINED = 5;
    public static final byte THREAD_START = 6;
    public static final byte THREAD_DEATH = 7;
    public static final byte CLASS_PREPARE = 8;
    public static final byte CLASS_UNLOAD = 9;
    public static final byte CLASS_LOAD = 10;
    public static final byte FIELD_ACCESS = 20;
    public static final byte FIELD_MODIFICATION = 21;
    public static final byte EXCEPTION_CATCH = 30;
    public static final byte METHOD_ENTRY = 40;
    public static final byte METHOD_EXIT = 41;
    public static final byte METHOD_EXIT_WITH_RETURN_VALUE = 42;
    public static final byte MONITOR_CONTENDED_ENTER = 43;
    public static final byte MONITOR_CONTENDED_ENTERED = 44;
    public static final byte MONITOR_WAIT = 45;
    public static final byte MONITOR_WAITED = 46;
    public static final byte VM_START = 90;
    public static final byte VM_DEATH = 99;
    public static final byte VM_DISCONNECTED = 100;

    private final VMEventListener eventListener;
    private final DebuggerController controller;
    private final Ids<Object> ids;

    RequestedJDWPEvents(DebuggerController controller) {
        this.controller = controller;
        this.eventListener = controller.getEventListener();
        this.ids = controller.getContext().getIds();
    }

    public CommandResult registerEvent(Packet packet, Commands callback) {
        ArrayList<Callable<Void>> preFutures = new ArrayList<>();
        ArrayList<Callable<Void>> postFutures = new ArrayList<>();
        PacketStream input = new PacketStream(packet);
        JDWPContext context = controller.getContext();

        byte eventKind = input.readByte();
        byte suspendPolicy = input.readByte();
        int modifiers = input.readInt();

        RequestFilter filter = new RequestFilter(packet.id, eventKind, suspendPolicy);
        controller.fine(() -> "New event request with ID: " + packet.id + " with kind: " + eventKind + " and modifiers: " + modifiers);
        for (int i = 0; i < modifiers; i++) {
            byte modKind = input.readByte();
            controller.fine(() -> "Handling modKind: " + modKind);
            handleModKind(filter, input, modKind, context);
        }

        switch (eventKind) {
            case SINGLE_STEP:
                StepInfo stepInfo = filter.getStepInfo();
                Object thread = stepInfo.getGuestThread();
                switch (stepInfo.getDepth()) {
                    case SteppingConstants.INTO:
                        callback.step(thread, filter, DebuggerCommand.Kind.STEP_INTO);
                        break;
                    case SteppingConstants.OVER:
                        callback.step(thread, filter, DebuggerCommand.Kind.STEP_OVER);
                        break;
                    case SteppingConstants.OUT:
                        callback.step(thread, filter, DebuggerCommand.Kind.STEP_OUT);
                        break;
                }
                break;
            case METHOD_ENTRY:
            case METHOD_EXIT:
            case METHOD_EXIT_WITH_RETURN_VALUE:
                MethodBreakpointInfo methodInfo = new MethodBreakpointInfo(filter);
                methodInfo.addSuspendPolicy(suspendPolicy);
                eventListener.addBreakpointRequest(filter.getRequestId(), methodInfo);
                eventListener.addClassConsumer(methodInfo);
                for (KlassRef klass : context.getAllLoadedClasses()) {
                    methodInfo.accept(klass);
                }
                filter.addBreakpointInfo(methodInfo);
                break;
            case BREAKPOINT:
                BreakpointInfo info = filter.getBreakpointInfo();
                info.addSuspendPolicy(suspendPolicy);
                eventListener.addBreakpointRequest(filter.getRequestId(), info);
                postFutures.add(callback.createLineBreakpointCommand(info));
                break;
            case EXCEPTION:
                info = filter.getBreakpointInfo();
                if (info == null) {
                    // no filtering then, so setup a report all info
                    info = new ExceptionBreakpointInfo(filter, null, true, true);
                }
                info.addSuspendPolicy(suspendPolicy);
                eventListener.addBreakpointRequest(filter.getRequestId(), info);
                preFutures.add(callback.createExceptionBreakpoint(info));
                controller.fine(() -> "Submitting new exception breakpoint");
                break;
            case CLASS_PREPARE:
                eventListener.addClassPrepareRequest(new ClassPrepareRequest(filter));
                controller.fine(() -> "Class prepare request received");
                break;
            case FIELD_ACCESS:
                FieldBreakpointInfo fieldBreakpointInfo = (FieldBreakpointInfo) filter.getBreakpointInfo();
                fieldBreakpointInfo.addSuspendPolicy(suspendPolicy);
                fieldBreakpointInfo.setAccessBreakpoint();
                eventListener.addFieldRequest(fieldBreakpointInfo);
                String location = fieldBreakpointInfo.getKlass().getNameAsString() + "." + fieldBreakpointInfo.getField().getNameAsString();
                controller.fine(() -> "Submitting field access breakpoint: " + location);
                break;
            case FIELD_MODIFICATION:
                fieldBreakpointInfo = (FieldBreakpointInfo) filter.getBreakpointInfo();
                fieldBreakpointInfo.addSuspendPolicy(suspendPolicy);
                fieldBreakpointInfo.setModificationBreakpoint();
                eventListener.addFieldRequest(fieldBreakpointInfo);
                location = fieldBreakpointInfo.getKlass().getNameAsString() + "." + fieldBreakpointInfo.getField().getNameAsString();
                controller.fine(() -> "Submitting field modification breakpoint: " + location);
                break;
            case THREAD_START:
                eventListener.addThreadStartedRequestId(packet.id, suspendPolicy);
                break;
            case THREAD_DEATH:
                eventListener.addThreadDiedRequestId(packet.id, suspendPolicy);
                break;
            case CLASS_UNLOAD:
                eventListener.addClassUnloadRequestId(packet.id);
                break;
            case VM_START: // no debuggers should ask for this event
                eventListener.addVMStartRequest(packet.id);
                break;
            case VM_DEATH:
                eventListener.addVMDeathRequest(packet.id, suspendPolicy);
                break;
            case MONITOR_CONTENDED_ENTER:
                eventListener.addMonitorContendedEnterRequest(packet.id, filter);
                break;
            case MONITOR_CONTENDED_ENTERED:
                eventListener.addMonitorContendedEnteredRequest(packet.id, filter);
                break;
            case MONITOR_WAIT:
                eventListener.addMonitorWaitRequest(packet.id, filter);
                break;
            case MONITOR_WAITED:
                eventListener.addMonitorWaitedRequest(packet.id, filter);
                break;
            default:
                controller.fine(() -> "unhandled event kind " + eventKind);
                break;
        }

        // register the request filter for this event
        controller.getEventFilters().addFilter(filter);
        return new CommandResult(toReply(packet), preFutures, postFutures);
    }

    private static PacketStream toReply(Packet packet) {
        PacketStream reply;
        reply = new PacketStream().replyPacket().id(packet.id);
        reply.writeInt(packet.id);
        return reply;
    }

    private void handleModKind(RequestFilter filter, PacketStream input, byte modKind, JDWPContext context) {
        switch (modKind) {
            case 1:
                int count = input.readInt();
                controller.fine(() -> "adding count limit: " + count + " to filter");
                filter.addEventCount(count);
                break;
            case 2:
                controller.fine(() -> "unhandled modKind 2");
                break;
            case 3: // limit to specific thread
                long threadId = input.readLong();
                Object thread = ids.fromId((int) threadId);
                filter.addThread(thread);
                controller.fine(() -> "limiting to thread: " + context.getThreadName(thread));
                break;
            case 4:
                long refTypeId = input.readLong();
                final KlassRef finalKlass = (KlassRef) ids.fromId((int) refTypeId);
                filter.addRefTypeOnly(finalKlass);
                controller.fine(() -> "RefType limit: " + finalKlass);
                break;
            case 5: // class positive pattern
                String pattern = input.readString();
                filter.addIncludePattern(pattern);
                controller.fine(() -> "adding positive refType pattern: " + pattern);
                break;
            case 6:
                pattern = input.readString();
                filter.addExcludePattern(pattern);
                controller.fine(() -> "adding negative refType pattern: " + pattern);
                break;
            case 7: // location-specific
                byte typeTag = input.readByte();
                long classId = input.readLong();
                long methodId = input.readLong();
                long bci = input.readLong();

                final KlassRef finalKlass2 = (KlassRef) ids.fromId((int) classId);
                String slashName = finalKlass2.getTypeAsString();
                MethodRef method = (MethodRef) ids.fromId((int) methodId);
                int line = method.bciToLineNumber((int) bci);

                LineBreakpointInfo info = new LineBreakpointInfo(filter, typeTag, classId, methodId, bci, slashName, line);
                filter.addBreakpointInfo(info);
                controller.fine(() -> "Adding breakpoint info for location: " + finalKlass2.getNameAsString() + "." + method.getNameAsString() + "." + line);
                break;
            case 8:
                refTypeId = input.readLong();
                KlassRef klass = null;
                if (refTypeId != 0) {
                    klass = (KlassRef) ids.fromId((int) refTypeId);
                }

                boolean caught = input.readBoolean();
                boolean unCaught = input.readBoolean();
                ExceptionBreakpointInfo exceptionBreakpointInfo = new ExceptionBreakpointInfo(filter, klass, caught, unCaught);
                filter.addBreakpointInfo(exceptionBreakpointInfo);
                controller.fine(() -> "adding exception filter: caught=" + caught + ", uncaught=" + unCaught);
                break;
            case 9: // limit to specific field
                refTypeId = input.readLong();
                long fieldId = input.readLong();
                klass = (KlassRef) ids.fromId((int) refTypeId);
                FieldRef field = (FieldRef) ids.fromId((int) fieldId);

                FieldBreakpointInfo fieldBreakpointInfo = new FieldBreakpointInfo(filter, klass, field);
                filter.addBreakpointInfo(fieldBreakpointInfo);
                controller.fine(() -> "limiting to field: " + field.getNameAsString());
                break;
            case 10:
                threadId = input.readLong();
                thread = ids.fromId((int) threadId);

                int size = input.readInt();
                int depth = input.readInt();

                StepInfo stepInfo = new StepInfo(size, depth, thread);
                filter.setStepInfo(stepInfo);

                controller.fine(() -> "Step command: size= " + size + ", depth=" + depth);
                break;
            case 11:
                long thisId = input.readLong();
                controller.fine(() -> "adding instance filter for object ID: " + thisId);
                filter.addThisFilterId(thisId);
                break;
            case 12:
                controller.fine(() -> "unhandled modKind 12");
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("fallthrough")
    public CommandResult clearRequest(Packet packet) {
        PacketStream reply = new PacketStream().id(packet.id).replyPacket();
        PacketStream input = new PacketStream(packet);

        byte eventKind = input.readByte();
        int requestId = input.readInt();
        RequestFilter requestFilter = controller.getEventFilters().removeRequestFilter(requestId);

        if (requestFilter != null) {
            byte kind = requestFilter.getEventKind();
            if (kind == eventKind) {
                switch (eventKind) {
                    case SINGLE_STEP:
                        controller.fine(() -> "Clearing step command: " + requestId);
                        controller.clearStepCommand(requestFilter.getStepInfo());
                        break;
                    case METHOD_ENTRY:
                    case METHOD_EXIT_WITH_RETURN_VALUE:
                    case METHOD_EXIT:
                        MethodBreakpointInfo methodInfo = (MethodBreakpointInfo) requestFilter.getBreakpointInfo();
                        eventListener.removeClassConsumer(methodInfo);
                        for (MethodRef method : methodInfo.getMethods()) {
                            method.removeMethodHook(requestFilter.getRequestId());
                        }
                        // fall through to breakpoint request removal
                    case BREAKPOINT:
                    case EXCEPTION:
                        eventListener.removeBreakpointRequest(requestFilter.getRequestId());
                        break;
                    case FIELD_ACCESS:
                    case FIELD_MODIFICATION:
                        FieldBreakpointInfo info = (FieldBreakpointInfo) requestFilter.getBreakpointInfo();
                        eventListener.removeFieldRequest(info.getRequestId(), info.getField());
                        break;
                    case CLASS_PREPARE:
                        eventListener.removeClassPrepareRequest(requestFilter.getRequestId());
                        break;
                    case THREAD_START:
                        eventListener.removeThreadStartedRequestId();
                        break;
                    case THREAD_DEATH:
                        eventListener.removeThreadDiedRequestId();
                        break;
                    case CLASS_UNLOAD:
                        eventListener.addClassUnloadRequestId(packet.id);
                        break;
                    case MONITOR_CONTENDED_ENTER:
                        eventListener.removeMonitorContendedEnterRequest(requestId);
                        break;
                    case MONITOR_CONTENDED_ENTERED:
                        eventListener.removeMonitorContendedEnteredRequest(requestId);
                        break;
                    case MONITOR_WAIT:
                        eventListener.removeMonitorWaitRequest(requestId);
                        break;
                    case MONITOR_WAITED:
                        eventListener.removeMonitorWaitedRequest(requestId);
                        break;
                    default:
                        controller.fine(() -> "unhandled event clear kind " + eventKind);
                        break;
                }
            } else {
                reply.errorCode(ErrorCodes.INVALID_EVENT_TYPE);
            }
        } else {
            reply.errorCode(ErrorCodes.INVALID_EVENT_TYPE);
        }

        return new CommandResult(reply);
    }

    public CommandResult clearAllBreakpointRequests(Packet packet) {
        PacketStream reply = new PacketStream().id(packet.id).replyPacket();

        eventListener.clearAllBreakpointRequests();
        controller.clearBreakpoints();
        return new CommandResult(reply);
    }
}
