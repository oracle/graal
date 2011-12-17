/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;

public abstract class Phase {

    private final String name;
    private final boolean shouldVerify;
    protected GraalContext context;

    protected Phase() {
        this.name = this.getClass().getSimpleName();
        this.shouldVerify = GraalOptions.VerifyPhases;
    }

    protected Phase(String name) {
        this(name, GraalOptions.VerifyPhases);
    }

    protected Phase(String name, boolean shouldVerify) {
        this.name = name;
        this.shouldVerify = shouldVerify;
    }

    protected String getDetailedName() {
        return getName();
    }

    public final void apply(StructuredGraph graph) {
        apply(graph, GraalContext.EMPTY_CONTEXT);
    }

    public final void apply(StructuredGraph graph, GraalContext context) {
        apply(graph, context, true, true);
    }

    public final void apply(StructuredGraph graph, boolean plotOnError, boolean plot) {
        apply(graph,  GraalContext.EMPTY_CONTEXT, plotOnError, plot);
    }

    public final void apply(StructuredGraph graph, GraalContext context, boolean plotOnError, boolean plot) {

        this.context = context;
        try {
            assert graph != null && !shouldVerify || graph.verify();
        } catch (GraalInternalError e) {
            throw e.addContext("start of phase", getDetailedName());
        }

        int startDeletedNodeCount = graph.getDeletedNodeCount();
        int startNodeCount = graph.getNodeCount();
        if (context != null) {
            context.timers.startScope(getName());
        }
        try {
            try {
                run(graph);
            } catch (CiBailout bailout) {
                throw bailout;
            } catch (AssertionError e) {
                throw new GraalInternalError(e);
            } catch (RuntimeException e) {
                throw new GraalInternalError(e);
            } finally {
                if (context != null) {
                    context.timers.endScope();
                }
            }
        } catch (GraalInternalError e) {
            throw e.addContext(graph).addContext("phase", getDetailedName());
        }

        if (context != null) {
            if (GraalOptions.Meter) {
                int deletedNodeCount = graph.getDeletedNodeCount() - startDeletedNodeCount;
                int createdNodeCount = graph.getNodeCount() - startNodeCount + deletedNodeCount;
                context.metrics.get(getName().concat(".executed")).increment();
                context.metrics.get(getName().concat(".deletedNodes")).increment(deletedNodeCount);
                context.metrics.get(getName().concat(".createdNodes")).increment(createdNodeCount);
            }

            boolean shouldFireCompilationEvents = context.isObserved() && this.getClass() != IdentifyBlocksPhase.class && (plot || GraalOptions.PlotVerbose);
            if (shouldFireCompilationEvents && context.timers.currentLevel() < GraalOptions.PlotLevel) {
                context.observable.fireCompilationEvent("After " + getName(), graph);
            }
        }

        try {
            assert !shouldVerify || graph.verify();
        } catch (GraalInternalError e) {
            throw e.addContext("end of phase", getDetailedName());
        }
    }

    public final String getName() {
        return name;
    }

    protected abstract void run(StructuredGraph graph);
}
