/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.debug.DebugDumpScope;
import com.oracle.graal.debug.DebugMemUseTracker;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.debug.internal.DebugValue;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Debug phase to illustrate the usage of {@link DebugMethodMetrics}. In contrast to the global
 * {@link DebugValue} implementations like {@link DebugCounter}, {@link DebugTimer} and
 * {@link DebugMemUseTracker}, {@link DebugMethodMetrics} allow to record counters for a given
 * method per compilation.
 * <p>
 * If {@link Debug#isMethodMeterEnabled()} this phase will also record the compilation ID from the
 * associated JVMCI compilation request (if existing) and the bytecode size of the method as defined
 * in {@link ResolvedJavaMethod#getCodeSize()}.
 */
public class MethodMetricsPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        if (Debug.isMethodMeterEnabled()) {
            /*
             * not all compilations will have this ID
             *
             * hack to get compilation ID is set in compilationtask#runCompilation
             */
            DebugDumpScope ddSc = Debug.contextLookup(DebugDumpScope.class);
            if (ddSc != null) {
                Debug.methodMetrics(graph.method()).addToMetric(Integer.parseInt(ddSc.name.replace("%", "")), "CompilationID");
            }
        }
        Debug.methodMetrics(graph.method()).addToMetric(graph.method().getCodeSize(), "BytecodeSize");
    }

}
