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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.phases.*;

/**
 * Base class for a stub that calls into a HotSpot C/C++ runtime function using the native
 * {@link CallingConvention}.
 */
public class RuntimeCallStub extends Stub {

    /**
     * The target of the call.
     */
    private final HotSpotRuntimeCallTarget target;

    /**
     * Specifies if the JavaThread value for the current thread is to be prepended to the arguments
     * for the call to {@link #target}.
     */
    protected final boolean prependThread;

    /**
     * Creates a stub for a call to code at a given address.
     * 
     * @param address the address of the code to call
     * @param sig the signature of the call to this stub
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     * @param regConfig used to get the calling convention for the call to this stub from Graal
     *            compiled Java code as well as the calling convention for the call to
     *            {@code address}
     * @param vm the Java to HotSpot C/C++ runtime interface
     */
    public RuntimeCallStub(long address, Descriptor sig, boolean prependThread, HotSpotRuntime runtime, Replacements replacements, RegisterConfig regConfig, CompilerToVM vm) {
        super(runtime, replacements, new HotSpotRuntimeCallTarget(sig, 0L, false, createCallingConvention(runtime, regConfig, sig), vm));
        this.prependThread = prependThread;
        Class[] targetParameterTypes = createTargetParameters(sig);
        Descriptor targetSig = new Descriptor(sig.getName() + ":C", sig.hasSideEffect(), sig.getResultType(), targetParameterTypes);
        CallingConvention targetCc = createCallingConvention(runtime, regConfig, targetSig);
        target = new HotSpotRuntimeCallTarget(targetSig, address, true, targetCc, vm);
    }

    /**
     * Gets the linkage information for the runtime call.
     */
    public HotSpotRuntimeCallTarget getTargetLinkage() {
        return target;
    }

    private static CallingConvention createCallingConvention(HotSpotRuntime runtime, RegisterConfig regConfig, Descriptor d) {
        Class<?>[] argumentTypes = d.getArgumentTypes();
        JavaType[] parameterTypes = new JavaType[argumentTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = runtime.lookupJavaType(argumentTypes[i]);
        }
        TargetDescription target = graalRuntime().getTarget();
        JavaType returnType = runtime.lookupJavaType(d.getResultType());
        return regConfig.getCallingConvention(Type.NativeCall, returnType, parameterTypes, target, false);
    }

    private Class[] createTargetParameters(Descriptor sig) {
        Class[] parameters = sig.getArgumentTypes();
        if (prependThread) {
            Class[] newParameters = new Class[parameters.length + 1];
            System.arraycopy(parameters, 0, newParameters, 1, parameters.length);
            newParameters[0] = Word.class;
            return newParameters;
        }
        return parameters;
    }

    @Override
    protected ResolvedJavaMethod getInstalledCodeOwner() {
        return null;
    }

    @Override
    protected Object debugScopeContext() {
        return new DebugDumpScope(linkage.descriptor.getName());
    }

    @Override
    protected StructuredGraph getGraph() {
        Class<?>[] args = linkage.getDescriptor().getArgumentTypes();
        LocalNode[] locals = new LocalNode[args.length];

        StructuredGraph graph = new StructuredGraph(toString(), null);
        StubStartNode start = graph.add(new StubStartNode(this));
        graph.replaceFixed(graph.start(), start);

        ResolvedJavaType accessingClass = runtime.lookupJavaType(getClass());
        for (int i = 0; i < args.length; i++) {
            JavaType type = runtime.lookupJavaType(args[i]).resolve(accessingClass);
            Kind kind = type.getKind().getStackKind();
            Stamp stamp;
            if (kind == Kind.Object) {
                stamp = StampFactory.declared((ResolvedJavaType) type);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            LocalNode local = graph.unique(new LocalNode(i, stamp));
            locals[i] = local;
        }

        // Create target call
        CRuntimeCall call = createTargetCall(locals, graph, start);

        // Create call to handlePendingException
        ResolvedJavaMethod hpeMethod = resolveMethod(StubUtil.class, "handlePendingException", boolean.class);
        JavaType returnType = hpeMethod.getSignature().getReturnType(null);
        ValueNode[] hpeArgs = {ConstantNode.forBoolean(linkage.getCallingConvention().getReturn().getKind() == Kind.Object, graph)};
        MethodCallTargetNode hpeTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, hpeMethod, hpeArgs, returnType));
        InvokeNode hpeInvoke = graph.add(new InvokeNode(hpeTarget, FrameState.UNKNOWN_BCI));
        List<ValueNode> emptyStack = Collections.emptyList();
        hpeInvoke.setStateAfter(graph.add(new FrameState(null, FrameState.INVALID_FRAMESTATE_BCI, new ValueNode[0], emptyStack, new ValueNode[0], false, false)));
        graph.addAfterFixed(call, hpeInvoke);

        // Create return node
        ReturnNode ret = graph.add(new ReturnNode(linkage.descriptor.getResultType() == void.class ? null : call));
        graph.addAfterFixed(hpeInvoke, ret);

        // Inline call to handlePendingException
        inline(hpeInvoke);

        return graph;
    }

    private CRuntimeCall createTargetCall(LocalNode[] locals, StructuredGraph graph, StubStartNode start) {
        CRuntimeCall call;
        ValueNode[] targetArguments;
        if (prependThread) {
            ReadRegisterNode thread = graph.add(new ReadRegisterNode(runtime.threadRegister(), true, false));
            graph.addAfterFixed(start, thread);
            targetArguments = new ValueNode[1 + locals.length];
            targetArguments[0] = thread;
            System.arraycopy(locals, 0, targetArguments, 1, locals.length);
            call = graph.add(new CRuntimeCall(target.descriptor, targetArguments));
            graph.addAfterFixed(thread, call);
        } else {
            targetArguments = new ValueNode[locals.length];
            System.arraycopy(locals, 0, targetArguments, 0, locals.length);
            call = graph.add(new CRuntimeCall(target.descriptor, targetArguments));
            graph.addAfterFixed(start, call);
        }
        return call;
    }

    private void inline(InvokeNode invoke) {
        StructuredGraph graph = invoke.graph();
        ResolvedJavaMethod method = ((MethodCallTargetNode) invoke.callTarget()).targetMethod();
        ReplacementsImpl repl = new ReplacementsImpl(runtime, new Assumptions(false), runtime.getTarget());
        StructuredGraph hpeGraph = repl.makeGraph(method, null, null);
        InliningUtil.inline(invoke, hpeGraph, false);
        new NodeIntrinsificationPhase(runtime).apply(graph);
        new WordTypeRewriterPhase(runtime, wordKind()).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);
    }

    private ResolvedJavaMethod resolveMethod(Class<?> declaringClass, String name, Class... parameterTypes) {
        try {
            return runtime.lookupJavaMethod(declaringClass.getDeclaredMethod(name, parameterTypes));
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }
}
