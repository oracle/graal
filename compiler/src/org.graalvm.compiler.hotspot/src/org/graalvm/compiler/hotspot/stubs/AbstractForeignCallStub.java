/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCallee;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
import static org.graalvm.compiler.nodes.ConstantNode.forBoolean;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.ForeignCallSnippets.Templates;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.GraphKit;
import org.graalvm.compiler.replacements.nodes.ReadRegisterNode;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A {@linkplain #getGraph generated} stub for a {@link Transition non-leaf} foreign call from
 * compiled code. A stub is required for such calls as the caller may be scheduled for
 * deoptimization while the call is in progress. And since these are foreign/runtime calls on slow
 * paths, we don't want to force the register allocator to spill around the call. As such, this stub
 * saves and restores all allocatable registers. It also
 * {@linkplain ForeignCallSnippets#handlePendingException handles} any exceptions raised during the
 * foreign call.
 */
public abstract class AbstractForeignCallStub extends Stub {

    protected final HotSpotJVMCIRuntime jvmciRuntime;

    /**
     * The target of the call.
     */
    protected final HotSpotForeignCallLinkage target;

    /**
     * Specifies if the JavaThread value for the current thread is to be prepended to the arguments
     * for the call to {@link #target}.
     */
    protected final boolean prependThread;

    /**
     * Creates a stub for a call to code at a given address.
     *
     * @param address the address of the code to call
     * @param descriptor the signature of the call to this stub
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     */
    public AbstractForeignCallStub(OptionValues options,
                    HotSpotJVMCIRuntime runtime,
                    HotSpotProviders providers,
                    long address,
                    HotSpotForeignCallDescriptor descriptor,
                    boolean prependThread) {
        super(options, providers, HotSpotForeignCallLinkageImpl.create(providers.getMetaAccess(),
                        providers.getCodeCache(),
                        providers.getWordTypes(),
                        providers.getForeignCalls(),
                        descriptor,
                        0L,
                        COMPUTES_REGISTERS_KILLED,
                        JavaCall,
                        JavaCallee));
        this.jvmciRuntime = runtime;
        this.prependThread = prependThread;
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        HotSpotForeignCallDescriptor targetSig = getTargetSignature(descriptor);
        target = HotSpotForeignCallLinkageImpl.create(metaAccess,
                        providers.getCodeCache(),
                        providers.getWordTypes(),
                        providers.getForeignCalls(),
                        targetSig,
                        address,
                        DESTROYS_ALL_CALLER_SAVE_REGISTERS,
                        NativeCall,
                        NativeCall);
    }

    protected abstract HotSpotForeignCallDescriptor getTargetSignature(HotSpotForeignCallDescriptor descriptor);

    /**
     * Gets the linkage information for the call from this stub.
     */
    public final HotSpotForeignCallLinkage getTargetLinkage() {
        return target;
    }

    @Override
    protected final ResolvedJavaMethod getInstalledCodeOwner() {
        return null;
    }

    private class DebugScopeContext implements JavaMethod, JavaMethodContext {
        @Override
        public JavaMethod asJavaMethod() {
            return this;
        }

        @Override
        public Signature getSignature() {
            ForeignCallDescriptor d = linkage.getDescriptor();
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            Class<?>[] arguments = d.getArgumentTypes();
            ResolvedJavaType[] parameters = new ResolvedJavaType[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = metaAccess.lookupJavaType(arguments[i]);
            }
            return new HotSpotSignature(jvmciRuntime, metaAccess.lookupJavaType(d.getResultType()), parameters);
        }

        @Override
        public String getName() {
            return linkage.getDescriptor().getName();
        }

        @Override
        public JavaType getDeclaringClass() {
            return providers.getMetaAccess().lookupJavaType(ForeignCallStub.class);
        }

        @Override
        public String toString() {
            return format("ForeignCallStub<%n(%p)>");
        }
    }

    @Override
    protected final Object debugScopeContext() {
        return new DebugScopeContext() {

        };
    }

    /**
     * Creates a graph for this stub.
     * <p>
     * If the stub returns an object, the graph created corresponds to this pseudo code:
     *
     * <pre>
     *     Object foreignFunctionStub(args...) {
     *         foreignFunction(currentThread,  args);
     *         if ((shouldClearException && clearPendingException(thread())) || (!shouldClearException && hasPendingException(thread)) {
     *             getAndClearObjectResult(thread());
     *             DeoptimizeCallerNode.deopt(None, RuntimeConstraint);
     *         }
     *         return verifyObject(getAndClearObjectResult(thread()));
     *     }
     * </pre>
     *
     * If the stub returns a primitive or word, the graph created corresponds to this pseudo code
     * (using {@code int} as the primitive return type):
     *
     * <pre>
     *     int foreignFunctionStub(args...) {
     *         int result = foreignFunction(currentThread,  args);
     *         if ((shouldClearException && clearPendingException(thread())) || (!shouldClearException && hasPendingException(thread)) {
     *             DeoptimizeCallerNode.deopt(None, RuntimeConstraint);
     *         }
     *         return result;
     *     }
     * </pre>
     *
     * If the stub is void, the graph created corresponds to this pseudo code:
     *
     * <pre>
     *     void foreignFunctionStub(args...) {
     *         foreignFunction(currentThread,  args);
     *         if ((shouldClearException && clearPendingException(thread())) || (!shouldClearException && hasPendingException(thread)) {
     *             DeoptimizeCallerNode.deopt(None, RuntimeConstraint);
     *         }
     *     }
     * </pre>
     *
     * In each example above, the {@code currentThread} argument is the C++ JavaThread value (i.e.,
     * %r15 on AMD64) and is only prepended if {@link #prependThread} is true.
     */
    @Override
    @SuppressWarnings("try")
    protected final StructuredGraph getGraph(DebugContext debug, CompilationIdentifier compilationId) {
        WordTypes wordTypes = providers.getWordTypes();
        boolean isObjectResult = returnsObject();
        // Do we want to clear the pending exception?
        boolean shouldClearException = shouldClearException();
        try {
            HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();
            Templates foreignCallSnippets = lowerer.getForeignCallSnippets();
            ResolvedJavaMethod handlePendingException = foreignCallSnippets.handlePendingException.getMethod();
            ResolvedJavaMethod getAndClearObjectResult = foreignCallSnippets.getAndClearObjectResult.getMethod();
            ResolvedJavaMethod verifyObject = foreignCallSnippets.verifyObject.getMethod();
            ResolvedJavaMethod thisMethod = getGraphMethod();
            GraphKit kit = new GraphKit(debug, thisMethod, providers, wordTypes, providers.getGraphBuilderPlugins(), compilationId, toString(), false, true);
            StructuredGraph graph = kit.getGraph();
            graph.getGraphState().forceDisableFrameStateVerification();
            ReadRegisterNode thread = kit.append(new ReadRegisterNode(providers.getRegisters().getThreadRegister(), wordTypes.getWordKind(), true, false));
            ValueNode result = createTargetCall(kit, thread);
            kit.createIntrinsicInvoke(handlePendingException, thread, forBoolean(shouldClearException, graph), forBoolean(isObjectResult, graph));
            if (isObjectResult) {
                InvokeNode object = kit.createIntrinsicInvoke(getAndClearObjectResult, thread);
                result = kit.createIntrinsicInvoke(verifyObject, object);
            }
            kit.append(new ReturnNode(linkage.getDescriptor().getResultType() == void.class ? null : result));
            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Initial stub graph");

            kit.inlineInvokesAsIntrinsics("Foreign call stub.", "Backend");

            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Stub graph before compilation");
            return graph;
        } catch (Exception e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    protected abstract boolean returnsObject();

    protected abstract boolean shouldClearException();

    private ResolvedJavaMethod getGraphMethod() {
        ResolvedJavaMethod thisMethod = null;
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        for (ResolvedJavaMethod method : metaAccess.lookupJavaType(AbstractForeignCallStub.class).getDeclaredMethods()) {
            if (method.getName().equals("getGraph")) {
                if (thisMethod == null) {
                    thisMethod = method;
                } else {
                    throw new InternalError("getGraph is ambiguous");
                }
            }
        }
        if (thisMethod == null) {
            throw new InternalError("Can't find " + getClass().getSimpleName() + ".getGraph");
        }
        if (IS_BUILDING_NATIVE_IMAGE) {
            HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();
            replacements.findSnippetMethod(thisMethod);
        }
        return thisMethod;
    }

    protected ParameterNode[] createParameters(GraphKit kit) {
        Class<?>[] args = linkage.getDescriptor().getArgumentTypes();
        ParameterNode[] params = new ParameterNode[args.length];
        MetaAccessProvider metaAccess = HotSpotReplacementsImpl.noticeTypes(providers.getMetaAccess());
        ResolvedJavaType accessingClass = metaAccess.lookupJavaType(getClass());
        for (int i = 0; i < args.length; i++) {
            ResolvedJavaType type = metaAccess.lookupJavaType(args[i]).resolve(accessingClass);
            StampPair stamp = StampFactory.forDeclaredType(kit.getGraph().getAssumptions(), type, false);
            ParameterNode param = kit.unique(new ParameterNode(i, stamp));
            params[i] = param;
        }
        return params;
    }

    protected abstract ValueNode createTargetCall(GraphKit kit, ReadRegisterNode thread);
}
