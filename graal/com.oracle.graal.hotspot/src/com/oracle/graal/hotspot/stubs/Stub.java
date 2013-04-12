/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;

/**
 * Base class for implementing some low level code providing the out-of-line slow path for a
 * snippet. A concrete stub is defined a subclass of this class.
 * <p>
 * Implementation detail: The stub classes re-use some of the functionality for {@link Snippet}s
 * purely for convenience (e.g., can re-use the {@link ReplacementsImpl}).
 */
public abstract class Stub extends AbstractTemplates implements Snippets {

    /**
     * The method implementing the stub.
     */
    protected final SnippetInfo stubInfo;

    /**
     * The linkage information for the stub.
     */
    protected final HotSpotRuntimeCallTarget linkage;

    /**
     * The code installed for the stub.
     */
    protected InstalledCode stubCode;

    /**
     * Creates a new stub container. The new stub still needs to be
     * {@linkplain #getAddress(Backend) installed}.
     * 
     * @param linkage linkage details for a call to the stub
     */
    public Stub(HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotRuntimeCallTarget linkage, String methodName) {
        super(runtime, replacements, target);
        this.stubInfo = snippet(getClass(), methodName);
        this.linkage = linkage;

    }

    /**
     * Adds the {@linkplain ConstantParameter constant} arguments of this stub.
     */
    protected abstract Arguments makeArguments(SnippetInfo stub);

    protected HotSpotRuntime runtime() {
        return (HotSpotRuntime) runtime;
    }

    /**
     * Gets the method implementing this stub.
     */
    public ResolvedJavaMethod getMethod() {
        return stubInfo.getMethod();
    }

    public HotSpotRuntimeCallTarget getLinkage() {
        return linkage;
    }

    /**
     * Ensures the code for this stub is installed.
     * 
     * @return the entry point address for calls to this stub
     */
    public synchronized long getAddress(Backend backend) {
        if (stubCode == null) {
            Arguments args = makeArguments(stubInfo);
            SnippetTemplate template = template(args);
            StructuredGraph graph = template.copySpecializedGraph();

            PhasePlan phasePlan = new PhasePlan();
            GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL);
            phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
            final CompilationResult compResult = GraalCompiler.compileMethod(runtime(), replacements, backend, runtime().getTarget(), getMethod(), graph, null, phasePlan, OptimisticOptimizations.ALL,
                            new SpeculationLog());

            stubCode = Debug.scope("CodeInstall", new Object[]{runtime(), getMethod()}, new Callable<InstalledCode>() {

                @Override
                public InstalledCode call() {
                    InstalledCode installedCode = runtime().addMethod(getMethod(), compResult);
                    assert installedCode != null : "error installing stub " + getMethod();
                    if (Debug.isDumpEnabled()) {
                        Debug.dump(new Object[]{compResult, installedCode}, "After code installation");
                    }
                    return installedCode;
                }
            });

            assert stubCode != null : "error installing stub " + getMethod();
        }
        return stubCode.getStart();
    }
}
