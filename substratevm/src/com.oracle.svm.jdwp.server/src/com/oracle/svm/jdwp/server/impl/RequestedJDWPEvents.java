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
import java.util.List;

import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.EventKind;
import com.oracle.svm.jdwp.bridge.JDWPBridge;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.WritablePacket;
import com.oracle.svm.jdwp.server.api.BreakpointInfo;
import com.oracle.svm.jdwp.server.ClassUtils;
import com.oracle.svm.jdwp.server.api.VMEventListener;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class RequestedJDWPEvents {

    private final VMEventListener eventListener;
    private final DebuggerController controller;

    private int lastRequestId = 0;

    RequestedJDWPEvents(DebuggerController controller) {
        this.controller = controller;
        this.eventListener = controller.getEventListener();
    }

    public Packet registerRequest(Packet packet) {
        Packet.Reader input = packet.newDataReader();
        JDWPContext context = controller.getContext();

        byte eventKindId = (byte) input.readByte();
        byte suspendPolicy = (byte) input.readByte();
        int modifiers = input.readInt();

        EventKind eventKind = EventKind.of(eventKindId);
        if (eventKind == null) {
            ServerJDWP.LOGGER.log(() -> "Unknown event request ID = " + eventKindId);
            throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
        }

        int requestId = ++lastRequestId;
        RequestFilter filter = new RequestFilter(requestId, eventKind);
        ServerJDWP.LOGGER.log(() -> "New event request with ID: " + requestId + " with kind: " + eventKind + ", suspendPolicy=" + suspendPolicy + " and modifiers: " + modifiers);
        for (int i = 0; i < modifiers; i++) {
            byte modKind = (byte) input.readByte();
            ServerJDWP.LOGGER.log(() -> "Handling modKind: " + modKind);
            handleModKind(filter, input, eventKind, modKind, context);
        }

        switch (eventKind) {
            case SINGLE_STEP:
                StepInfo stepInfo = filter.getStepInfo();
                long threadId = stepInfo.threadId();

                eventListener.addStepRequest(requestId, threadId, new SteppingInfo(requestId, stepInfo, suspendPolicy, false, false));
                ServerJDWP.BRIDGE.setSteppingFromLocation(threadId, stepInfo.getInterpreterDepth(), stepInfo.getInterpreterSize(),
                                stepInfo.startLocation().getMethodId(), stepInfo.startLocation().getBci(), JDWPBridge.UNINITIALIZED_LINE_NUMBER);
                ServerJDWP.BRIDGE.setEventEnabled(threadId, EventKind.SINGLE_STEP.ordinal(), true);
                break;
            case METHOD_ENTRY:
            case METHOD_EXIT:
            case METHOD_EXIT_WITH_RETURN_VALUE:
                addMethodBreakRequest(filter, suspendPolicy, context);
                break;
            case BREAKPOINT:
                List<BreakpointInfo> infos = filter.getBreakpointInfos();
                if (infos == null) {
                    // Breakpoint without any break location
                    throw JDWPException.raise(ErrorCode.INVALID_LOCATION);
                }
                for (BreakpointInfo info : infos) {
                    info.addSuspendPolicy(suspendPolicy);
                }
                eventListener.addBreakpointRequest(requestId, infos);
                try {
                    context.getBreakpoints().addLines(infos);
                } catch (UnknownLocationException ex) {
                    // perhaps the debugger's view on the source is out of sync, in which case
                    // the bytecode and source does not match.
                    throw JDWPException.raise(ErrorCode.INVALID_LOCATION);
                }
                break;
            case EXCEPTION:
                ServerJDWP.LOGGER.log(() -> "Submitting new exception breakpoint");
                break;
            case CLASS_PREPARE:
                eventListener.addClassPrepareRequest(new ClassPrepareRequest(filter));
                ServerJDWP.LOGGER.log(() -> "Class prepare request received");
                break;
            case FIELD_ACCESS:
            case FIELD_MODIFICATION: {
                WritablePacket reply = WritablePacket.newReplyTo(packet);
                reply.errorCode(ErrorCode.NOT_IMPLEMENTED);
                return reply;
            }
            case THREAD_START:
                eventListener.addThreadStartedRequestId(requestId, suspendPolicy);
                ServerJDWP.BRIDGE.setThreadRequest(true, true);
                break;
            case THREAD_DEATH:
                eventListener.addThreadDiedRequestId(requestId, suspendPolicy);
                ServerJDWP.BRIDGE.setThreadRequest(false, true);
                break;
            case CLASS_UNLOAD:
                eventListener.addClassUnloadRequestId(requestId);
                break;
            case VM_START: // no debuggers should ask for this event
                eventListener.addVMStartRequest(requestId);
                break;
            case VM_DEATH:
                eventListener.addVMDeathRequest(requestId, suspendPolicy);
                break;
            case MONITOR_CONTENDED_ENTER:
            case MONITOR_CONTENDED_ENTERED:
            case MONITOR_WAIT:
            case MONITOR_WAITED:
                break;
            default: {
                ServerJDWP.LOGGER.log(() -> "unhandled event kind " + eventKind);
                WritablePacket reply = WritablePacket.newReplyTo(packet);
                reply.errorCode(ErrorCode.INVALID_EVENT_TYPE);
                return reply;
            }
        }

        // register the request filter for this event
        controller.getEventFilters().addFilter(filter);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();
        data.writeInt(requestId);
        return reply;
    }

    @SuppressWarnings("static-method")
    private void handleModKind(RequestFilter filter, Packet.Reader input, EventKind eventKind, byte modKind, JDWPContext context) {
        switch (modKind) {
            case 1: {
                int count = input.readInt();
                ServerJDWP.LOGGER.log(() -> "adding count limit: " + count + " to filter");
                if (count <= 0) {
                    throw JDWPException.raise(ErrorCode.INVALID_COUNT);
                }
                filter.addCount(count);
                break;
            }
            case 2: {
                ServerJDWP.LOGGER.log(() -> "unhandled modKind 2");
                throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
                // break;
            }
            case 3: { // limit to specific thread
                long threadId = input.readLong();
                filter.addThread(threadId);
                ServerJDWP.LOGGER.log(() -> "limiting to thread ID: " + threadId);
                break;
            }
            case 4: {
                long refTypeId = input.readLong();
                ResolvedJavaType type = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(refTypeId);
                filter.addClassOnly(type);
                ServerJDWP.LOGGER.log(() -> "RefType is: " + type);
                break;
            }
            case 5: { // class positive pattern
                String classPattern = input.readString();
                filter.addClassNameMatch(classPattern);
                ServerJDWP.LOGGER.log(() -> "adding class name match pattern: " + classPattern);
                break;
            }
            case 6: {
                String classPattern = input.readString();
                filter.addClassNameExclude(classPattern);
                ServerJDWP.LOGGER.log(() -> "adding class name exclude pattern: " + classPattern);
                break;
            }
            case 7: { // location-specific
                byte typeTag = (byte) input.readByte();
                long classId = input.readLong();
                long methodId = input.readLong();
                long bci = input.readLong();

                if (EventKind.BREAKPOINT == eventKind) {
                    LineBreakpointInfo info = new LineBreakpointInfo(filter, context.getBreakpoints(), typeTag, classId, methodId, bci);
                    filter.addBreakpointInfo(info);
                } else {
                    filter.addLocation(classId, methodId, bci);
                }
                ServerJDWP.LOGGER.log(() -> "Adding breakpoint info for location method = " + methodId + ", bci = " + bci);
                break;
            }
            case 8: {
                boolean caught = input.readBoolean();
                boolean unCaught = input.readBoolean();
                ServerJDWP.LOGGER.log(() -> "adding exception filter: caught=" + caught + ", uncaught=" + unCaught);
                break;
            }
            case 9: { // limit to specific field
                long refTypeId = input.readLong();
                long fieldId = input.readLong();
                ServerJDWP.LOGGER.log(() -> "limiting to field: fieldId=" + fieldId + ", refTypeId=" + refTypeId);
                throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
                // break;
            }
            case 10: {
                long threadId = input.readLong();

                int size = input.readInt();
                int depth = input.readInt();

                long startMethodId = 0;
                int startBci = JDWPBridge.UNKNOWN_BCI;
                ThreadRef threadRef = context.getThreadRefIfExists(threadId);
                SuspendedInfo suspendedInfo = threadRef != null ? threadRef.getSuspendedInfo() : null;
                if (suspendedInfo != null) {
                    startMethodId = suspendedInfo.methodId();
                    startBci = (int) suspendedInfo.bci();
                }

                StepInfo.Location startLocation = new StepInfo.Location(startMethodId, startBci, size == SteppingConstants.LINE);
                StepInfo stepInfo = new StepInfo(filter, size, depth, threadId, startLocation);
                filter.setStepInfo(stepInfo);

                ServerJDWP.LOGGER.log(() -> "Step command: size= " + size + ", depth=" + depth);
                break;
            }
            case 11: {
                long thisId = input.readLong();
                ServerJDWP.LOGGER.log(() -> "adding instance filter for object ID: " + thisId);
                filter.addThis(thisId);
                break;
            }
            case 12: {
                ServerJDWP.LOGGER.log(() -> "unhandled modKind 12");
                throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
                // break;
            }
            case 13: {
                ServerJDWP.LOGGER.log(() -> "Adding filter for platform threads");
                filter.addPlatformThreadsOnly();
                break;
            }
            default:
                ServerJDWP.LOGGER.log(() -> "unhandled modKind " + modKind);
                break;
        }
    }

    private void addMethodBreakRequest(RequestFilter filter, byte suspendPolicy, JDWPContext context) {
        Collection<BreakpointInfo> infos = new ArrayList<>();
        for (ResolvedJavaType type : ClassUtils.UNIVERSE.getTypes()) {
            if (filter.matchesType(type)) {
                int typeRefIndex = ClassUtils.UNIVERSE.getTypeIndexFor(type).orElseThrow(IllegalArgumentException::new);
                long classId = ServerJDWP.BRIDGE.typeRefIndexToId(typeRefIndex);
                MethodBreakpointInfo methodInfo = new MethodBreakpointInfo(filter, context.getBreakpoints(), classId);
                methodInfo.addSuspendPolicy(suspendPolicy);
                infos.add(methodInfo);
            }
        }
        context.getBreakpoints().addMethods(infos);
        eventListener.addBreakpointRequest(filter.getRequestId(), infos);
    }

    public Packet clearRequest(Packet packet) {
        Packet.Reader input = packet.newDataReader();
        WritablePacket reply = WritablePacket.newReplyTo(packet);

        byte eventKindId = (byte) input.readByte();
        int requestId = input.readInt();
        EventKind eventKind = EventKind.of(eventKindId);
        if (eventKind == null) {
            ServerJDWP.LOGGER.log(() -> "Unknown event request ID = " + eventKindId);
            throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
        }
        RequestFilter requestFilter = controller.getEventFilters().getRequestFilter(requestId);

        if (requestFilter != null) {
            EventKind kind = requestFilter.getEventKind();
            if (kind == eventKind) {
                switch (eventKind) {
                    case SINGLE_STEP:
                        ServerJDWP.LOGGER.log(() -> "Clearing step command: " + requestId);
                        eventListener.removeStepRequest(requestId);
                        break;
                    case METHOD_EXIT_WITH_RETURN_VALUE:
                    case METHOD_EXIT:
                    case BREAKPOINT:
                    case METHOD_ENTRY:
                    case EXCEPTION:
                        eventListener.removeBreakpointRequest(requestFilter.getRequestId());
                        break;
                    case FIELD_ACCESS:
                    case FIELD_MODIFICATION:
                    case MONITOR_CONTENDED_ENTER:
                    case MONITOR_CONTENDED_ENTERED:
                    case MONITOR_WAIT:
                    case MONITOR_WAITED:
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
                        eventListener.addClassUnloadRequestId(packet.id());
                        break;
                    default:
                        ServerJDWP.LOGGER.log(() -> "unhandled event clear kind " + eventKind);
                        break;
                }
            } else {
                reply.errorCode(ErrorCode.INVALID_EVENT_TYPE);
            }
        } else {
            reply.errorCode(ErrorCode.INVALID_EVENT_TYPE);
        }

        return reply;
    }

    public Packet clearAllBreakpoints(Packet packet) {
        WritablePacket reply = WritablePacket.newReplyTo(packet);

        eventListener.clearAllBreakpointRequests();
        controller.clearBreakpoints();
        return reply;
    }
}
