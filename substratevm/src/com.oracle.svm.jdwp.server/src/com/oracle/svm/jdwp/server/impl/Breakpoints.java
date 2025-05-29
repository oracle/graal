/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.jdwp.bridge.EventKind;
import com.oracle.svm.jdwp.server.api.BreakpointInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Collects all breakpoints and assures appropriate set/unset of breakpoints in the resident VM.
 */
public final class Breakpoints {

    private final Map<Location, List<BreakpointInfo>> locationBreakpoints = new HashMap<>();
    /** Method enter breakpoints by class ID. */
    private final Map<Long, List<BreakpointInfo>> methodEnterBreakpoints = new HashMap<>();
    /** Method exit breakpoints by class ID. */
    private final Map<Long, List<BreakpointInfo>> methodExitBreakpoints = new HashMap<>();

    public synchronized void addLines(Collection<BreakpointInfo> infos) throws UnknownLocationException {
        for (BreakpointInfo info : infos) {
            assert info.getEventKind() == EventKind.BREAKPOINT : info.getEventKind();
            Location location = new Location(info.getMethodId(), info.getBci());
            try {
                locationBreakpoints.computeIfAbsent(location, loc -> {
                    long methodId = info.getMethodId();
                    long bci = loc.bci();
                    if (ServerJDWP.LOGGER.isLoggable()) {
                        long classId = info.getClassId();
                        ResolvedJavaMethod resolvedJavaMethod = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaMethod(methodId);
                        ResolvedJavaType declaringType = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(classId);
                        ServerJDWP.LOGGER.log(() -> "Breakpoints.add: BCI=" + bci + ", class id = " + classId + ", method id = " + methodId + "\n" +
                                        "  class = " + declaringType.getName() + "\n" +
                                        "  method = " + resolvedJavaMethod.getName());
                    }
                    ServerJDWP.BRIDGE.toggleBreakpoint(methodId, (int) bci, true);
                    ServerJDWP.BRIDGE.setEventEnabled(0, EventKind.BREAKPOINT.ordinal(), true);
                    return new ArrayList<>(1);
                }).add(info);
            } catch (IllegalArgumentException e) {
                throw new UnknownLocationException(e);
            }
        }
    }

    public synchronized void addMethods(Collection<BreakpointInfo> infos) {
        for (BreakpointInfo info : infos) {
            EventKind kind = info.getEventKind();
            assert kind == EventKind.METHOD_ENTRY || kind == EventKind.METHOD_EXIT || kind == EventKind.METHOD_EXIT_WITH_RETURN_VALUE : kind;
            switch (kind) {
                case METHOD_ENTRY ->
                    methodEnterBreakpoints.computeIfAbsent(info.getClassId(), id -> {
                        if (ServerJDWP.LOGGER.isLoggable()) {
                            ResolvedJavaType declaringType = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(id);
                            ServerJDWP.LOGGER.log(() -> "Breakpoints.add method enter: class id = " + id + " class = " + declaringType.getName());
                        }
                        ServerJDWP.BRIDGE.toggleMethodEnterEvent(id, true);
                        ServerJDWP.BRIDGE.setEventEnabled(0, EventKind.METHOD_ENTRY.ordinal(), true);
                        return new ArrayList<>(1);
                    }).add(info);
                case METHOD_EXIT, METHOD_EXIT_WITH_RETURN_VALUE ->
                    methodExitBreakpoints.computeIfAbsent(info.getClassId(), id -> {
                        if (ServerJDWP.LOGGER.isLoggable()) {
                            ResolvedJavaType declaringType = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(id);
                            ServerJDWP.LOGGER.log(() -> "Breakpoints.add method exit: class id = " + id + " class = " + declaringType.getName());
                        }
                        ServerJDWP.BRIDGE.toggleMethodExitEvent(id, true);
                        ServerJDWP.BRIDGE.setEventEnabled(0, EventKind.METHOD_EXIT.ordinal(), true);
                        return new ArrayList<>(1);
                    }).add(info);
                default -> throw new IllegalStateException("Event kind " + kind);
            }
        }
    }

    public synchronized void removeAll(Collection<BreakpointInfo> infos) {
        for (BreakpointInfo info : infos) {
            doRemove(info);
        }
    }

    synchronized void remove(BreakpointInfo info) {
        doRemove(info);
    }

    private void doRemove(BreakpointInfo info) {
        assert Thread.holdsLock(this);
        EventKind kind = info.getEventKind();
        switch (kind) {
            case BREAKPOINT -> removeLocation(info);
            case METHOD_ENTRY -> removeMethodEnter(info);
            case METHOD_EXIT, METHOD_EXIT_WITH_RETURN_VALUE -> removeMethodExit(info);
            default -> throw new IllegalStateException("Event kind " + kind);
        }
    }

    private void removeLocation(BreakpointInfo info) {
        long methodId = info.getMethodId();
        long bci = info.getBci();
        Location location = new Location(methodId, bci);
        List<BreakpointInfo> infoList = locationBreakpoints.get(location);
        if (infoList != null) {
            infoList.remove(info);
            if (infoList.isEmpty()) {
                if (ServerJDWP.LOGGER.isLoggable()) {
                    long classId = info.getClassId();
                    ResolvedJavaMethod resolvedJavaMethod = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaMethod(methodId);
                    ResolvedJavaType declaringType = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(classId);
                    ServerJDWP.LOGGER.log(() -> "Breakpoints.remove: BCI=" + bci + ", class id = " + classId + ", method id = " + methodId + "\n" +
                                    "  class = " + declaringType.getName() + "\n" +
                                    "  method = " + resolvedJavaMethod.getName());
                }
                ServerJDWP.BRIDGE.toggleBreakpoint(methodId, (int) bci, false);
                locationBreakpoints.remove(location);
                if (locationBreakpoints.isEmpty()) {
                    ServerJDWP.BRIDGE.setEventEnabled(0, EventKind.BREAKPOINT.ordinal(), false);
                }
            }
        }
    }

    private void removeMethodEnter(BreakpointInfo info) {
        long classId = info.getClassId();
        List<BreakpointInfo> infoList = methodEnterBreakpoints.get(classId);
        if (infoList != null) {
            infoList.remove(info);
            if (infoList.isEmpty()) {
                if (ServerJDWP.LOGGER.isLoggable()) {
                    ResolvedJavaType declaringType = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(classId);
                    ServerJDWP.LOGGER.log(() -> "Breakpoints.remove method enter: class id = " + classId + "  class = " + declaringType.getName());
                }
                ServerJDWP.BRIDGE.toggleMethodEnterEvent(classId, false);
                methodEnterBreakpoints.remove(classId);
                if (methodEnterBreakpoints.isEmpty()) {
                    ServerJDWP.BRIDGE.setEventEnabled(0, EventKind.METHOD_ENTRY.ordinal(), false);
                }
            }
        }
    }

    private void removeMethodExit(BreakpointInfo info) {
        long classId = info.getClassId();
        List<BreakpointInfo> infoList = methodExitBreakpoints.get(classId);
        if (infoList != null) {
            infoList.remove(info);
            if (infoList.isEmpty()) {
                if (ServerJDWP.LOGGER.isLoggable()) {
                    ResolvedJavaType declaringType = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaType(classId);
                    ServerJDWP.LOGGER.log(() -> "Breakpoints.remove method exit: class id = " + classId + "  class = " + declaringType.getName());
                }
                ServerJDWP.BRIDGE.toggleMethodExitEvent(classId, false);
                methodExitBreakpoints.remove(classId);
                if (methodExitBreakpoints.isEmpty()) {
                    ServerJDWP.BRIDGE.setEventEnabled(0, EventKind.METHOD_EXIT.ordinal(), false);
                }
            }
        }
    }

    private record Location(long methodId, long bci) {
    }
}
