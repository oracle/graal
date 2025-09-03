/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.phases;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;

public class BaseTier<C> extends PhaseSuite<C> {

    /**
     * Time spent in hinted GC in frontend.
     */
    public static final TimerKey HIRHintedGC = DebugContext.timer("HIRHintedGC").doc("Time spent in hinted GC performed before each HIR phase.");

    public LoopPolicies createLoopPolicies(@SuppressWarnings("unused") OptionValues options) {
        return new DefaultLoopPolicies();
    }

    @SuppressWarnings({"try"})
    @Override
    protected void run(StructuredGraph graph, C context) {
        for (BasePhase<? super C> phase : getPhases()) {
            LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
            if (libgraal != null) {
                /*
                 * Notify the libgraal runtime that most objects allocated in previous HIR phase are
                 * dead and can be reclaimed. This will lower the chance of allocation failure in
                 * the next HIR phase.
                 */
                try (DebugCloseable timer = HIRHintedGC.start(graph.getDebug())) {
                    libgraal.notifyLowMemoryPoint(false);
                    libgraal.processReferences();
                }
            }
            phase.apply(graph, context);
        }
    }
}
