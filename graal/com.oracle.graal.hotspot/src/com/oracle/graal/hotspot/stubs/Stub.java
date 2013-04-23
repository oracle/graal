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

import static com.oracle.graal.hotspot.nodes.CStringNode.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

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
    protected InstalledCode code;

    /**
     * The registers defined by this stub.
     */
    private Set<Register> definedRegisters;

    public void initDefinedRegisters(Set<Register> registers) {
        assert registers != null;
        assert definedRegisters == null : "cannot redefine";
        definedRegisters = registers;
    }

    public Set<Register> getDefinedRegisters() {
        assert definedRegisters != null : "not yet initialized";
        return definedRegisters;
    }

    /**
     * Creates a new stub container..
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
     * Gets the code for this stub, compiling it first if necessary.
     */
    public synchronized InstalledCode getCode(final Backend backend) {
        if (code == null) {
            Debug.sandbox("CompilingStub", new Object[]{runtime(), getMethod()}, DebugScope.getConfig(), new Runnable() {

                @Override
                public void run() {

                    Arguments args = makeArguments(stubInfo);
                    SnippetTemplate template = template(args);
                    StructuredGraph graph = template.copySpecializedGraph();

                    PhasePlan phasePlan = new PhasePlan();
                    GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL);
                    phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
                    final CompilationResult compResult = GraalCompiler.compileMethod(runtime(), replacements, backend, runtime().getTarget(), getMethod(), graph, null, phasePlan,
                                    OptimisticOptimizations.ALL, new SpeculationLog());

                    assert definedRegisters != null;
                    code = Debug.scope("CodeInstall", new Callable<InstalledCode>() {

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

                }
            });
            assert code != null : "error installing stub " + getMethod();
        }
        return code;
    }

    static void log(boolean enabled, String format, long value) {
        if (enabled) {
            printf(format, value);
        }
    }

    static void log(boolean enabled, String format, WordBase value) {
        if (enabled) {
            printf(format, value.rawValue());
        }
    }

    static void log(boolean enabled, String format, Word v1, long v2) {
        if (enabled) {
            printf(format, v1.rawValue(), v2);
        }
    }

    static void log(boolean enabled, String format, Word v1, Word v2) {
        if (enabled) {
            printf(format, v1.rawValue(), v2.rawValue());
        }
    }

    public static final Descriptor STUB_PRINTF = new Descriptor("stubPrintf", false, void.class, Word.class, long.class, long.class, long.class);

    @NodeIntrinsic(RuntimeCallNode.class)
    private static native void printf(@ConstantNodeParameter Descriptor stubPrintf, Word format, long v1, long v2, long v3);

    /**
     * Prints a formatted string to the log stream.
     * 
     * @param format a C style printf format value that can contain at most one conversion specifier
     *            (i.e., a sequence of characters starting with '%').
     * @param value the value associated with the conversion specifier
     */
    public static void printf(String format, long value) {
        printf(STUB_PRINTF, cstring(format), value, 0L, 0L);
    }

    public static void printf(String format, long v1, long v2) {
        printf(STUB_PRINTF, cstring(format), v1, v2, 0L);
    }

    public static void printf(String format, long v1, long v2, long v3) {
        printf(STUB_PRINTF, cstring(format), v1, v2, v3);
    }
}
