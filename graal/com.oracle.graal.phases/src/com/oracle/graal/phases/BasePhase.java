/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases;

import java.util.regex.*;

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.debug.Debug.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * Base class for all compiler phases. Subclasses should be stateless. There will be one global
 * instance for each compiler phase that is shared for all compilations. VM-, target- and
 * compilation-specific data can be passed with a context object.
 */
public abstract class BasePhase<C> {

    public static final int PHASE_DUMP_LEVEL = 1;
    public static final int BEFORE_PHASE_DUMP_LEVEL = 3;

    private CharSequence name;

    /**
     * Records time spent in {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugTimer timer;

    /**
     * Counts calls to {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugMetric executionCount;

    /**
     * Accumulates the {@linkplain Graph#getNodeCount() live node count} of all graphs sent to
     * {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugMetric inputNodesCount;

    /**
     * Records memory usage within {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugMemUseTracker memUseTracker;

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    private static final Pattern NAME_PATTERN = assertionsEnabled() ? Pattern.compile("[A-Z][A-Za-z0-9]+") : null;

    private static boolean checkName(String name) {
        assert NAME_PATTERN.matcher(name).matches() : "illegal phase name: " + name;
        return true;
    }

    private static class BasePhaseStatistics {
        /**
         * Records time spent in {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugTimer timer;

        /**
         * Counts calls to {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugMetric executionCount;

        /**
         * Accumulates the {@linkplain Graph#getNodeCount() live node count} of all graphs sent to
         * {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugMetric inputNodesCount;

        /**
         * Records memory usage within {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugMemUseTracker memUseTracker;

        BasePhaseStatistics(Class<?> clazz) {
            timer = Debug.timer("PhaseTime_%s", clazz);
            executionCount = Debug.metric("PhaseCount_%s", clazz);
            memUseTracker = Debug.memUseTracker("PhaseMemUse_%s", clazz);
            inputNodesCount = Debug.metric("PhaseNodes_%s", clazz);
        }
    }

    private static final ClassValue<BasePhaseStatistics> statisticsClassValue = new ClassValue<BasePhaseStatistics>() {
        @Override
        protected BasePhaseStatistics computeValue(Class<?> c) {
            return new BasePhaseStatistics(c);
        }
    };

    protected BasePhase() {
        BasePhaseStatistics statistics = statisticsClassValue.get(getClass());
        timer = statistics.timer;
        executionCount = statistics.executionCount;
        memUseTracker = statistics.memUseTracker;
        inputNodesCount = statistics.inputNodesCount;
    }

    protected BasePhase(String name) {
        assert checkName(name);
        this.name = name;
        BasePhaseStatistics statistics = statisticsClassValue.get(getClass());
        timer = statistics.timer;
        executionCount = statistics.executionCount;
        memUseTracker = statistics.memUseTracker;
        inputNodesCount = statistics.inputNodesCount;
    }

    public final void apply(final StructuredGraph graph, final C context) {
        apply(graph, context, true);
    }

    protected final void apply(final StructuredGraph graph, final C context, final boolean dumpGraph) {
        try (DebugCloseable a = timer.start(); Scope s = Debug.scope(getClass(), this); DebugCloseable c = memUseTracker.start()) {
            if (dumpGraph && Debug.isDumpEnabled(BEFORE_PHASE_DUMP_LEVEL)) {
                Debug.dump(BEFORE_PHASE_DUMP_LEVEL, graph, "Before phase %s", getName());
            }
            this.run(graph, context);
            executionCount.increment();
            inputNodesCount.add(graph.getNodeCount());
            if (dumpGraph && Debug.isDumpEnabled(PHASE_DUMP_LEVEL)) {
                Debug.dump(PHASE_DUMP_LEVEL, graph, "%s", getName());
            }
            if (Fingerprint.ENABLED) {
                String graphDesc = graph.method() == null ? graph.name : graph.method().format("%H.%n(%p)");
                Fingerprint.submit("After phase %s nodes in %s are %s", getName(), graphDesc, graph.getNodes().snapshot());
            }
            if (Debug.isVerifyEnabled()) {
                Debug.verify(graph, "%s", getName());
            }
            assert graph.verify();
        } catch (Throwable t) {
            throw Debug.handle(t);
        }
    }

    protected CharSequence createName() {
        String className = BasePhase.this.getClass().getName();
        String s = className.substring(className.lastIndexOf(".") + 1); // strip the package name
        if (s.endsWith("Phase")) {
            s = s.substring(0, s.length() - "Phase".length());
        }
        return s;
    }

    public final CharSequence getName() {
        if (name == null) {
            name = createName();
        }
        return name;
    }

    protected abstract void run(StructuredGraph graph, C context);
}
