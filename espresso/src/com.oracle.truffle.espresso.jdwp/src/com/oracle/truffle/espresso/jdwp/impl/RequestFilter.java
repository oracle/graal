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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public final class RequestFilter {

    private final int requestId;
    private final byte eventKind;
    private final byte suspendPolicy;
    private final List<Filter> filters = new ArrayList<>();
    private volatile boolean active = true;
    private BreakpointInfo breakpointInfo;
    private StepInfo stepInfo;

    public RequestFilter(int requestId, byte eventKind, byte suspendPolicy) {
        this.requestId = requestId;
        this.eventKind = eventKind;
        this.suspendPolicy = suspendPolicy;
    }

    public int getRequestId() {
        return requestId;
    }

    public byte getEventKind() {
        return eventKind;
    }

    public void addIncludePattern(String pattern) {
        filters.add(new Filter.ClassNameInclude(pattern));
    }

    public void addExcludePattern(String pattern) {
        filters.add(new Filter.ClassNameExclude(pattern));
    }

    public void setStepInfo(StepInfo info) {
        this.stepInfo = info;
    }

    public StepInfo getStepInfo() {
        return stepInfo;
    }

    public void addRefTypeOnly(KlassRef klassRef) {
        filters.add(new Filter.ClassOnly(klassRef));
    }

    public void addEventCount(int eventCount) {
        filters.add(new Count(eventCount));
    }

    public void addThread(Object guestThread) {
        filters.add(new Filter.ThreadId(guestThread));
    }

    public void addBreakpointInfo(BreakpointInfo info) {
        this.breakpointInfo = info;
    }

    public BreakpointInfo getBreakpointInfo() {
        return breakpointInfo;
    }

    public void addThisFilterId(long thisId) {
        filters.add(new Filter.This(thisId));
    }

    public byte getSuspendPolicy() {
        return suspendPolicy;
    }

    public boolean isHit(EventInfo event) {
        for (Filter filter : filters) {
            if (!filter.isHit(event)) {
                return false;
            }
        }
        return true;
    }

    public boolean matchesType(KlassRef klass) {
        for (Filter filter : filters) {
            if (!filter.matchesType(klass)) {
                return false;
            }
        }
        return true;
    }

    public boolean isActive() {
        return active;
    }

    final class Count extends Filter implements IntUnaryOperator {

        private final AtomicInteger hitCount;

        Count(int count) {
            hitCount = new AtomicInteger(count);
        }

        @Override
        boolean isHit(EventInfo event) {
            int count = hitCount.updateAndGet(this);
            if (count <= 0) {
                // Deactivate the filter
                RequestFilter.this.active = false;
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

    private abstract static class Filter {

        abstract boolean isHit(EventInfo event);

        boolean matchesType(@SuppressWarnings("unused") KlassRef classType) {
            return true;
        }

        static final class ThreadId extends Filter {

            private final Object guestThread;

            ThreadId(Object guestThread) {
                this.guestThread = guestThread;
            }

            @Override
            boolean isHit(EventInfo event) {
                return event.getThread() == guestThread;
            }
        }

        static final class ClassOnly extends Filter {

            private final KlassRef type;

            ClassOnly(KlassRef type) {
                this.type = type;
            }

            @Override
            boolean isHit(EventInfo event) {
                return type.isAssignable(event.getType());
            }

            @Override
            boolean matchesType(KlassRef eventType) {
                return type.isAssignable(eventType);
            }
        }

        static class ClassNameInclude extends Filter {

            private final String pattern;
            private final ClassNameMatchKind matchKind;

            enum ClassNameMatchKind {
                EXACT,
                STARTS,
                ENDS
            }

            ClassNameInclude(String classNamePattern) {
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
                return matchesTypeName(event.getType());
            }

            @Override
            boolean matchesType(KlassRef klass) {
                return matchesTypeName(klass);
            }

            private boolean matchesTypeName(KlassRef klass) {
                String name = klass.getNameAsString().replace('/', '.');
                return switch (matchKind) {
                    case EXACT -> name.equals(pattern);
                    case STARTS -> name.startsWith(pattern);
                    case ENDS -> name.endsWith(pattern);
                    default -> throw new IllegalStateException("Unknown match kind " + matchKind);
                };
            }
        }

        static final class ClassNameExclude extends ClassNameInclude {

            ClassNameExclude(String classNamePattern) {
                super(classNamePattern);
            }

            @Override
            boolean isHit(EventInfo event) {
                return !super.isHit(event);
            }

            @Override
            boolean matchesType(KlassRef eventType) {
                return !super.matchesType(eventType);
            }
        }

        static final class This extends Filter {

            private final long objectId;

            This(long objectId) {
                this.objectId = objectId;
            }

            @Override
            boolean isHit(EventInfo event) {
                return event.getThisId() == objectId;
            }
        }
    }
}
