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

import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.nodes.CStringNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
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
import com.oracle.graal.hotspot.nodes.*;
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
import com.oracle.graal.word.*;

//JaCoCo Exclude

/**
 * Base class for implementing some low level code providing the out-of-line slow path for a
 * snippet. A stub may make a direct call to a HotSpot C/C++ runtime function. Stubs are installed
 * as an instance of the C++ RuntimeStub class (as opposed to nmethod).
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
     * The registers destroyed by this stub.
     */
    private Set<Register> destroyedRegisters;

    public void initDestroyedRegisters(Set<Register> registers) {
        assert registers != null;
        assert destroyedRegisters == null || registers.equals(destroyedRegisters) : "cannot redefine";
        destroyedRegisters = registers;
    }

    /**
     * Gets the registers defined by this stub. These are the temporaries of this stub and must thus
     * be caller saved by a callers of this stub.
     */
    public Set<Register> getDestroyedRegisters() {
        assert destroyedRegisters != null : "not yet initialized";
        return destroyedRegisters;
    }

    /**
     * Determines if this stub preserves all registers apart from those it
     * {@linkplain #getDestroyedRegisters() destroys}.
     */
    public boolean preservesRegisters() {
        return true;
    }

    /**
     * Creates a new stub container..
     * 
     * @param linkage linkage details for a call to the stub
     */
    public Stub(HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotRuntimeCallTarget linkage) {
        super(runtime, replacements, target);
        this.stubInfo = snippet(getClass(), null);
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
     * Checks the conditions a compilation must satisfy to be installed as a RuntimeStub.
     */
    private boolean checkStubInvariants(CompilationResult compResult) {
        for (DataPatch data : compResult.getDataReferences()) {
            Constant constant = data.constant;
            assert constant.getKind() != Kind.Object : format("%h.%n(%p): ", getMethod()) + "cannot have embedded object constant: " + constant;
            assert constant.getPrimitiveAnnotation() == null : format("%h.%n(%p): ", getMethod()) + "cannot have embedded metadata: " + constant;
        }
        for (Infopoint infopoint : compResult.getInfopoints()) {
            assert infopoint instanceof Call : format("%h.%n(%p): ", getMethod()) + "cannot have non-call infopoint: " + infopoint;
            Call call = (Call) infopoint;
            assert call.target instanceof HotSpotRuntimeCallTarget : format("%h.%n(%p): ", getMethod()) + "cannot have non runtime call: " + call.target;
            HotSpotRuntimeCallTarget callTarget = (HotSpotRuntimeCallTarget) call.target;
            assert callTarget.getAddress() == graalRuntime().getConfig().uncommonTrapStub || callTarget.isCRuntimeCall() : format("%h.%n(%p): ", getMethod()) +
                            "must only call C runtime or deoptimization stub, not " + call.target;
        }
        return true;
    }

    /**
     * Looks for a {@link CRuntimeCall} node intrinsic named {@code name} in {@code stubClass} and
     * returns a {@link Descriptor} based on its signature and the value of {@code hasSideEffect}.
     */
    protected static <T extends Stub> Descriptor descriptorFor(Class<T> stubClass, String name, boolean hasSideEffect) {
        Method found = null;
        for (Method method : stubClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getAnnotation(NodeIntrinsic.class) != null && method.getName().equals(name)) {
                if (method.getAnnotation(NodeIntrinsic.class).value() == CRuntimeCall.class) {
                    assert found == null : "found more than one C runtime call named " + name + " in " + stubClass;
                    assert method.getParameterTypes().length != 0 && method.getParameterTypes()[0] == Descriptor.class : "first parameter of C runtime call '" + name + "' in " + stubClass +
                                    " must be of type " + Descriptor.class.getSimpleName();
                    found = method;
                }
            }
        }
        assert found != null : "could not find C runtime call named " + name + " in " + stubClass;
        List<Class<?>> paramList = Arrays.asList(found.getParameterTypes());
        Class[] cCallTypes = paramList.subList(1, paramList.size()).toArray(new Class[paramList.size() - 1]);
        return new Descriptor(name, hasSideEffect, found.getReturnType(), cCallTypes);
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

                    assert checkStubInvariants(compResult);

                    assert destroyedRegisters != null;
                    code = Debug.scope("CodeInstall", new Callable<InstalledCode>() {

                        @Override
                        public InstalledCode call() {
                            InstalledCode installedCode = runtime().addMethod(getMethod(), compResult);
                            assert installedCode != null : "error installing stub " + getMethod();
                            if (Debug.isDumpEnabled()) {
                                Debug.dump(new Object[]{compResult, installedCode}, "After code installation");
                            }
                            // TTY.println(getMethod().toString());
                            // TTY.println(runtime().disassemble(installedCode));
                            return installedCode;
                        }
                    });
                }
            });
            assert code != null : "error installing stub " + getMethod();
        }
        return code;
    }

    static void log(boolean enabled, String message) {
        if (enabled) {
            printf(message);
        }
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

    static void handlePendingException(boolean isObjectResult) {
        if (clearPendingException(thread())) {
            if (isObjectResult) {
                getAndClearObjectResult(thread());
            }
            DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
        }
    }

    public static final Descriptor VM_MESSAGE_C = descriptorFor(Stub.class, "vmMessageC", false);

    @NodeIntrinsic(CRuntimeCall.class)
    private static native void vmMessageC(@ConstantNodeParameter Descriptor stubPrintfC, boolean vmError, Word format, long v1, long v2, long v3);

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     * 
     * @param message a message string
     */
    public static void printf(String message) {
        vmMessageC(VM_MESSAGE_C, false, cstring(message), 0L, 0L, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     * 
     * @param format a C style printf format value
     * @param value the value associated with the first conversion specifier in {@code format}
     */
    public static void printf(String format, long value) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), value, 0L, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     * 
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     */
    public static void printf(String format, long v1, long v2) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), v1, v2, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     * 
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     * @param v3 the value associated with the third conversion specifier in {@code format}
     */
    public static void printf(String format, long v1, long v2, long v3) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), v1, v2, v3);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link VMErrorNode#vmError(String, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     * 
     * @param message an error message
     */
    public static void fatal(String message) {
        vmMessageC(VM_MESSAGE_C, true, cstring(message), 0L, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     * 
     * @param format a C style printf format value
     * @param value the value associated with the first conversion specifier in {@code format}
     */
    public static void fatal(String format, long value) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), value, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     * 
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     */
    public static void fatal(String format, long v1, long v2) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), v1, v2, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     * 
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     * @param v3 the value associated with the third conversion specifier in {@code format}
     */
    public static void fatal(String format, long v1, long v2, long v3) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), v1, v2, v3);
    }
}
