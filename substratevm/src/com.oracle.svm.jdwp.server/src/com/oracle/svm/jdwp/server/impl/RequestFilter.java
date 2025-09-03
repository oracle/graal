/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class RequestFilter {

    private final int requestId;
    private final EventKind eventKind;
    private final List<Filter> filters = new ArrayList<>();

    private List<BreakpointInfo> breakpointInfos;
    private StepInfo stepInfo;

    public RequestFilter(int requestId, EventKind eventKind) {
        this.requestId = requestId;
        this.eventKind = eventKind;
    }

    public int getRequestId() {
        return requestId;
    }

    public EventKind getEventKind() {
        return eventKind;
    }

    void setStepInfo(StepInfo info) {
        this.stepInfo = info;
    }

    public StepInfo getStepInfo() {
        return stepInfo;
    }

    void addBreakpointInfo(BreakpointInfo info) {
        if (breakpointInfos == null) {
            breakpointInfos = new ArrayList<>(1);
        }
        breakpointInfos.add(info);
    }

    public List<BreakpointInfo> getBreakpointInfos() {
        return breakpointInfos;
    }

    void addCount(int count) {
        filters.add(new Filter.Count(count));
    }

    void addThread(long threadId) {
        filters.add(new Filter.ThreadId(threadId));
    }

    void addClassOnly(ResolvedJavaType type) {
        filters.add(new Filter.ClassOnly(type));
    }

    void addClassNameMatch(String classNamePattern) {
        filters.add(new Filter.ClassNameMatch(classNamePattern));
    }

    void addClassNameExclude(String classNamePattern) {
        filters.add(new Filter.ClassNameExclude(classNamePattern));
    }

    void addLocation(long classId, long methodId, long bci) {
        filters.add(new Filter.Location(classId, methodId, bci));
    }

    void addThis(long objectId) {
        filters.add(new Filter.This(objectId));
    }

    void addPlatformThreadsOnly() {
        filters.add(new Filter.PlatformThreadsOnly());
    }

    public boolean isHit(EventInfo event) {
        for (Filter filter : filters) {
            if (!filter.isHit(event)) {
                return false;
            }
        }
        return true;
    }

    public boolean matchesType(ResolvedJavaType eventType) {
        for (Filter filter : filters) {
            if (!filter.matchesType(eventType)) {
                return false;
            }
        }
        return true;
    }

    private abstract static class Filter {

        abstract boolean isHit(EventInfo event);

        boolean matchesType(@SuppressWarnings("unused") ResolvedJavaType eventType) {
            return true;
        }

        static final class Count extends Filter implements IntUnaryOperator {

            private final AtomicInteger hitCount;

            Count(int count) {
                hitCount = new AtomicInteger(count);
            }

            @Override
            boolean isHit(EventInfo event) {
                int count = hitCount.updateAndGet(this);
                if (count <= 0) {
                    event.disableNextEvents();
                }
                return count == 0;
            }

            @Override
            public int applyAsInt(int lastHitCount) {
                if (lastHitCount > 0) {
                    return lastHitCount - 1;
                } else {
                    return -1;
                }
            }
        }

        static final class ThreadId extends Filter {

            private final long threadId;

            ThreadId(long threadId) {
                this.threadId = threadId;
            }

            @Override
            boolean isHit(EventInfo event) {
                return event.threadId() == threadId;
            }
        }

        static final class ClassOnly extends Filter {

            private final ResolvedJavaType type;

            ClassOnly(ResolvedJavaType type) {
                this.type = type;
            }

            @Override
            boolean isHit(EventInfo event) {
                return type.isAssignableFrom(event.type());
            }

            @Override
            boolean matchesType(ResolvedJavaType eventType) {
                return type.isAssignableFrom(eventType);
            }
        }

        static class ClassNameMatch extends Filter {

            private final String pattern;
            private final ClassNameMatchKind matchKind;

            enum ClassNameMatchKind {
                EXACT,
                STARTS,
                ENDS
            }

            ClassNameMatch(String classNamePattern) {
                int length = classNamePattern.length();
                if (length > 0 && classNamePattern.charAt(0) == '*') {
                    pattern = classNamePattern.substring(1);
                    matchKind = ClassNameMatchKind.ENDS;
                } else if (length > 0 && classNamePattern.charAt(length - 1) == '*') {
                    pattern = classNamePattern.substring(0, length - 1);
                    matchKind = ClassNameMatchKind.STARTS;
                } else {
                    pattern = classNamePattern;
                    matchKind = ClassNameMatchKind.EXACT;
                }
            }

            @Override
            boolean isHit(EventInfo event) {
                String name = event.className();
                return matchesTypeName(name);
            }

            @Override
            boolean matchesType(ResolvedJavaType eventType) {
                String name = eventType.toClassName();
                return matchesTypeName(name);
            }

            private boolean matchesTypeName(String name) {
                return switch (matchKind) {
                    case EXACT -> name.equals(pattern);
                    case STARTS -> name.startsWith(pattern);
                    case ENDS -> name.endsWith(pattern);
                    default -> throw new IllegalStateException("Unknown match kind " + matchKind);
                };
            }
        }

        static final class ClassNameExclude extends ClassNameMatch {

            ClassNameExclude(String classNamePattern) {
                super(classNamePattern);
            }

            @Override
            boolean isHit(EventInfo event) {
                return !super.isHit(event);
            }

            @Override
            boolean matchesType(ResolvedJavaType eventType) {
                return !super.matchesType(eventType);
            }
        }

        static final class Location extends Filter {

            private final long classId;
            private final long methodId;
            private final long bci;

            Location(long classId, long methodId, long bci) {
                this.classId = classId;
                this.methodId = methodId;
                this.bci = bci;
            }

            @Override
            boolean isHit(EventInfo event) {
                return event.classId() == classId && event.methodId() == methodId && event.bci() == bci;
            }
        }

        static final class This extends Filter {

            private final long objectId;

            This(long objectId) {
                this.objectId = objectId;
            }

            @Override
            boolean isHit(EventInfo event) {
                return EventInfo.thisId() == objectId;
            }
        }

        static final class PlatformThreadsOnly extends Filter {

            @Override
            boolean isHit(EventInfo event) {
                return !EventInfo.isThreadVirtual();
            }
        }
    }
}
