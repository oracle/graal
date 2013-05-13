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

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;

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
import com.oracle.graal.hotspot.replacements.*;
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
 * A stub that calls into a HotSpot C/C++ runtime function using the native
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
            if (WordBase.class.isAssignableFrom(argumentTypes[i])) {
                parameterTypes[i] = runtime.lookupJavaType(wordKind().toJavaClass());
            } else {
                parameterTypes[i] = runtime.lookupJavaType(argumentTypes[i]);
            }
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
        return new JavaMethod() {

            public Signature getSignature() {
                Descriptor d = linkage.descriptor;
                Class<?>[] arguments = d.getArgumentTypes();
                JavaType[] parameters = new JavaType[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    parameters[i] = runtime.lookupJavaType(arguments[i]);
                }
                return new HotSpotSignature(runtime.lookupJavaType(d.getResultType()), parameters);
            }

            public String getName() {
                return linkage.descriptor.getName();
            }

            public JavaType getDeclaringClass() {
                return runtime.lookupJavaType(RuntimeCallStub.class);
            }

            @Override
            public String toString() {
                return format("HotSpotStub<%n(%p)>", this);
            }
        };
    }

    static class GraphBuilder {

        public GraphBuilder(Stub stub) {
            this.graph = new StructuredGraph(stub.toString(), null);
            graph.replaceFixed(graph.start(), graph.add(new StubStartNode(stub)));
            this.lastFixedNode = graph.start();
        }

        final StructuredGraph graph;
        private FixedWithNextNode lastFixedNode;

        <T extends Node> T add(T node) {
            T result = graph.add(node);
            assert node == result;
            if (result instanceof FixedNode) {
                assert lastFixedNode != null;
                FixedNode fixed = (FixedNode) result;
                assert fixed.predecessor() == null;
                graph.addAfterFixed(lastFixedNode, fixed);
                if (fixed instanceof FixedWithNextNode) {
                    lastFixedNode = (FixedWithNextNode) fixed;
                } else {
                    lastFixedNode = null;
                }
            }
            return result;
        }
    }

    @Override
    protected StructuredGraph getGraph() {
        Class<?>[] args = linkage.getDescriptor().getArgumentTypes();
        boolean isObjectResult = linkage.getCallingConvention().getReturn().getKind() == Kind.Object;

        GraphBuilder builder = new GraphBuilder(this);

        LocalNode[] locals = createLocals(builder, args);

        ReadRegisterNode thread = prependThread || isObjectResult ? builder.add(new ReadRegisterNode(runtime.threadRegister(), true, false)) : null;
        ValueNode result = createTargetCall(builder, locals, thread);
        createInvoke(builder, StubUtil.class, "handlePendingException", ConstantNode.forBoolean(isObjectResult, builder.graph));
        if (isObjectResult) {
            InvokeNode object = createInvoke(builder, HotSpotReplacementsUtil.class, "getAndClearObjectResult", thread);
            result = createInvoke(builder, StubUtil.class, "verifyObject", object);
        }
        builder.add(new ReturnNode(linkage.descriptor.getResultType() == void.class ? null : result));

        if (Debug.isDumpEnabled()) {
            Debug.dump(builder.graph, "Initial stub graph");
        }

        for (InvokeNode invoke : builder.graph.getNodes(InvokeNode.class).snapshot()) {
            inline(invoke);
        }
        assert builder.graph.getNodes(InvokeNode.class).isEmpty();

        if (Debug.isDumpEnabled()) {
            Debug.dump(builder.graph, "Stub graph before compilation");
        }

        return builder.graph;
    }

    private LocalNode[] createLocals(GraphBuilder builder, Class<?>[] args) {
        LocalNode[] locals = new LocalNode[args.length];
        ResolvedJavaType accessingClass = runtime.lookupJavaType(getClass());
        for (int i = 0; i < args.length; i++) {
            ResolvedJavaType type = runtime.lookupJavaType(args[i]).resolve(accessingClass);
            Kind kind = type.getKind().getStackKind();
            Stamp stamp;
            if (kind == Kind.Object) {
                stamp = StampFactory.declared(type);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            LocalNode local = builder.add(new LocalNode(i, stamp));
            locals[i] = local;
        }
        return locals;
    }

    private InvokeNode createInvoke(GraphBuilder builder, Class<?> declaringClass, String name, ValueNode... hpeArgs) {
        ResolvedJavaMethod method = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name)) {
                assert method == null : "found more than one method in " + declaringClass + " named " + name;
                method = runtime.lookupJavaMethod(m);
            }
        }
        assert method != null : "did not find method in " + declaringClass + " named " + name;
        JavaType returnType = method.getSignature().getReturnType(null);
        MethodCallTargetNode callTarget = builder.add(new MethodCallTargetNode(InvokeKind.Static, method, hpeArgs, returnType));
        InvokeNode invoke = builder.add(new InvokeNode(callTarget, FrameState.UNKNOWN_BCI));
        return invoke;
    }

    private CRuntimeCall createTargetCall(GraphBuilder builder, LocalNode[] locals, ReadRegisterNode thread) {
        if (prependThread) {
            ValueNode[] targetArguments = new ValueNode[1 + locals.length];
            targetArguments[0] = thread;
            System.arraycopy(locals, 0, targetArguments, 1, locals.length);
            return builder.add(new CRuntimeCall(target.descriptor, targetArguments));
        } else {
            return builder.add(new CRuntimeCall(target.descriptor, locals));
        }
    }

    private void inline(InvokeNode invoke) {
        StructuredGraph graph = invoke.graph();
        ResolvedJavaMethod method = ((MethodCallTargetNode) invoke.callTarget()).targetMethod();
        ReplacementsImpl repl = new ReplacementsImpl(runtime, new Assumptions(false), runtime.getTarget());
        StructuredGraph calleeGraph = repl.makeGraph(method, null, null);
        InliningUtil.inline(invoke, calleeGraph, false);
        new NodeIntrinsificationPhase(runtime).apply(graph);
        new WordTypeRewriterPhase(runtime, wordKind()).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);
    }
}
