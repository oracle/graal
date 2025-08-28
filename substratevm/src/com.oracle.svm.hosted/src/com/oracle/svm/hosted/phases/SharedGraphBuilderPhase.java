/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import static com.oracle.svm.core.SubstrateUtil.toUnboxedClass;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC2_W;

import java.lang.classfile.Opcode;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.constraints.TypeInstantiationException;
import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.bootstrap.BootstrapMethodConfiguration;
import com.oracle.svm.core.bootstrap.BootstrapMethodConfiguration.BootstrapMethodRecord;
import com.oracle.svm.core.bootstrap.BootstrapMethodInfo;
import com.oracle.svm.core.bootstrap.BootstrapMethodInfo.ExceptionWrapper;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.graal.nodes.DeoptEntryBeginNode;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptEntrySupport;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.graal.nodes.FieldOffsetNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.nodes.foreign.ScopedMethodNode;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.SharedArenaSupport;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BciBlockMapping.BciBlock;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public abstract class SharedGraphBuilderPhase extends GraphBuilderPhase.Instance {

    public SharedGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }

    @Override
    protected void run(StructuredGraph graph) {
        super.run(graph);
        assert providers.getWordTypes() == null || providers.getWordTypes().ensureGraphContainsNoWordTypeReferences(graph);
    }

    public abstract static class SharedBytecodeParser extends BytecodeParser {

        private static final Executable SESSION_EXCEPTION_HANDLER_METHOD;
        private static final Class<?> MAPPED_MEMORY_UTILS_PROXY_CLASS;
        private static final Class<?> ABSTRACT_MEMORY_SEGMENT_IMPL_CLASS;

        static {
            /*
             * Class 'SubstrateForeignUtil' is optional because it is contained in a different
             * distribution which may not always be available.
             */
            Class<?> substrateForeignUtilClass = ReflectionUtil.lookupClass(true, "com.oracle.svm.core.foreign.SubstrateForeignUtil");
            if (substrateForeignUtilClass != null) {
                SESSION_EXCEPTION_HANDLER_METHOD = ReflectionUtil.lookupMethod(substrateForeignUtilClass, "sessionExceptionHandler", MemorySessionImpl.class, Object.class, long.class);
            } else {
                SESSION_EXCEPTION_HANDLER_METHOD = null;
            }
            MAPPED_MEMORY_UTILS_PROXY_CLASS = ReflectionUtil.lookupClass("jdk.internal.access.foreign.MappedMemoryUtilsProxy");
            ABSTRACT_MEMORY_SEGMENT_IMPL_CLASS = ReflectionUtil.lookupClass("jdk.internal.foreign.AbstractMemorySegmentImpl");
        }

        protected List<ValueNode> scopedMemorySessions;

        private int currentDeoptIndex;

        private final boolean explicitExceptionEdges;
        private final boolean linkAtBuildTime;
        protected final BootstrapMethodHandler bootstrapMethodHandler;

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges) {
            this(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, explicitExceptionEdges, LinkAtBuildTimeSupport.singleton().linkAtBuildTime(method.getDeclaringClass()));
        }

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges, boolean linkAtBuildTime) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            this.explicitExceptionEdges = explicitExceptionEdges;
            this.linkAtBuildTime = linkAtBuildTime;
            this.bootstrapMethodHandler = new BootstrapMethodHandler();
        }

        @Override
        protected BciBlockMapping generateBlockMap() {
            if (isDeoptimizationEnabled() && isMethodDeoptTarget()) {
                /*
                 * Need to add blocks representing where deoptimization entrypoint nodes will be
                 * inserted.
                 */
                return DeoptimizationTargetBciBlockMapping.create(stream, code, options, graph.getDebug(), false);
            } else {
                return BciBlockMapping.create(stream, code, options, graph.getDebug(), asyncExceptionLiveness());
            }
        }

        protected boolean shouldVerifyFrameStates() {
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
            if (!shouldVerifyFrameStates()) {
                startFrameState.disableStateVerification();
            }

            super.build(startInstruction, startFrameState);

            if (isMethodDeoptTarget()) {
                /*
                 * All DeoptProxyNodes should be valid.
                 */
                for (DeoptProxyNode deoptProxy : graph.getNodes(DeoptProxyNode.TYPE)) {
                    assert deoptProxy.hasProxyPoint();
                }
            }

            if (!isMethodDeoptTarget() && graph.method() != null) {
                /*
                 * A note on deoptimization, runtime compilation and shared arena support on svm: We
                 * instrument the runtime compiled versions of methods correctly. But instrumenting
                 * the deopt versions is hard because we cannot just create fake frame states (the
                 * frame state verification is very strict in this case) and we would need to
                 * generate appropriate bytecode. If a transition from the runtime compiled method
                 * to the deopt target happens, either a ScopedAccessError happened (i.e. the arena
                 * was closed) or the arena is still valid when initiating the deoptimization.
                 * Unfortunately, the deopt itself happens in a safepoint where other VM operations
                 * may be scheduled as well. Also, lazy deoptimization is interruptible. We can
                 * therefore not guarantee, that the session won't be closed during the transition
                 * to the deopt target. In order to solve this, we will need to insert session
                 * checks after each deopt entry in the deopt target (GR-66841).
                 */
                try {
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before instrumenting @Scoped method");
                    if (AnnotationAccess.isAnnotationPresent(method, ForeignSupport.Scoped.class) && SharedArenaSupport.isAvailable()) {
                        // substituted, only add the scoped node
                        introduceScopeNodes();
                    } else if (AnnotationAccess.isAnnotationPresent(method, SharedArenaSupport.SCOPED_ANNOTATION) && SharedArenaSupport.isAvailable()) {
                        // not substituted, also instrument
                        instrumentScopedMethod();
                    }
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After instrumenting @Scoped method");
                } catch (Throwable e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }
        }

        /**
         * Adds necessary instrumentation for scoped memory accesses. This includes an additional
         * exception handler per session argument that can be used to later create validity checks
         * per memory session.
         */
        private void instrumentScopedMethod() {
            introduceScopeInstrumentationExceptionHandlers();
            introduceScopeNodes();
        }

        /**
         * Takes a given {@code @Scope} annotated method using session arguments and introduces an
         * exception handler per session that can be later duplicated. Code like
         * {@link jdk.internal.misc.ScopedMemoryAccess#getByte(MemorySessionImpl, Object, long)}
         * which calls the {@code getByteInternal} method
         *
         * <pre>
         * {@code @Scoped}
         * private byte getByteInternal(MemorySessionImpl session, Object base, long offset) {
         *     try {
         *         if (session != null) {
         *             session.checkValidStateRaw();
         *         }
         *         return UNSAFE.getByte(base, offset);
         *     } finally {
         *         Reference.reachabilityFence(session);
         *     }
         * }
         * </pre>
         * <p>
         *
         * is transformed into
         *
         * <pre>
         * {@code @Scoped}
         * private byte getByteInternal(MemorySessionImpl session, Object base, long offset) {
         *     try {
         *         SubstrateForeignUtil.sessionExceptionHandler(); // can also throw and the
         *         // exception handlers are merged
         *         if (session != null) {
         *             session.checkValidStateRaw();
         *         }
         *         return UNSAFE.getByte(base, offset);
         *     } finally {
         *         Reference.reachabilityFence(session);
         *     }
         * }
         * </pre>
         */
        private void introduceScopeInstrumentationExceptionHandlers() {
            ResolvedJavaMethod sessionCheckMethod = getMetaAccess().lookupJavaMethod(SESSION_EXCEPTION_HANDLER_METHOD);

            assert sessionCheckMethod != null;
            List<SessionCheck> sessionsToCheck = getSessionArguments(method, graph, getMetaAccess());
            List<UnwindNode> unwinds = graph.getNodes(UnwindNode.TYPE).snapshot();
            // Doing a hosted compile of the scoped memory access methods every method must have an
            // exception handler or exception path unwinding to the caller, There must always be
            // exaclty ONE such path.
            GraalError.guarantee(unwinds.size() == 1, "Exactly one unwind node expected.");

            final UnwindNode unwind = unwinds.get(0);

            for (SessionCheck sessionCheck : sessionsToCheck) {

                Objects.requireNonNull(unwind);
                Objects.requireNonNull(unwind.predecessor());

                FrameState unwindMergeStateTemplate;

                FixedNode prevBegin = (FixedNode) unwind.predecessor();
                while (prevBegin instanceof BeginNode) {
                    prevBegin = (FixedNode) prevBegin.predecessor();
                }
                if (prevBegin instanceof MergeNode m) {
                    unwindMergeStateTemplate = m.stateAfter().duplicateWithVirtualState();
                } else {
                    // try to see if we can walk back and find only an exception object node
                    if (prevBegin instanceof ExceptionObjectNode e) {
                        unwindMergeStateTemplate = e.stateAfter().duplicateWithVirtualState();
                    } else {
                        throw GraalError.shouldNotReachHere("No merge predecessor found for " + unwind + " and prev begin " + prevBegin);
                    }
                }

                GraalError.guarantee(unwindMergeStateTemplate != null, "Must have a state on the unwind predecessor but did not find any for %s", unwind);

                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before inserting exception handlers for scoped unwind paths");
                assert sessionCheck.session != null : Assertions.errorMessage("At least the session must never be null", sessionsToCheck);
                ValueNode[] args = new ValueNode[]{sessionCheck.session, sessionCheck.base == null ? ConstantNode.defaultForKind(JavaKind.Object, graph) : sessionCheck.base,
                                sessionCheck.offset == null ? ConstantNode.defaultForKind(JavaKind.Long, graph) : sessionCheck.offset};
                MethodCallTargetNode mct = graph.addWithoutUnique(new MethodCallTargetNode(InvokeKind.Static, sessionCheckMethod, args, StampPair.createSingle(StampFactory.forVoid()), null));

                ResolvedJavaType tt = getMetaAccess().lookupJavaType(Throwable.class);
                assert tt != null;
                Stamp s = StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(tt));
                ExceptionObjectNode eon = graph.add(new ExceptionObjectNode(s));
                GraalError.guarantee(eon.stamp(NodeView.DEFAULT) != null, "Must have a stamp %s", eon);

                eon.setStateAfter(graph.addOrUnique(new FrameState(0, eon, graph.start().stateAfter().getCode(), false)));

                /* a random bci 0, we are injecting an artificial call */
                final int callBCI = 0;
                InvokeWithExceptionNode invoke = graph.add(new InvokeWithExceptionNode(mct, eon, callBCI));
                invoke.setStateAfter(graph.start().stateAfter().duplicateWithVirtualState());

                // hang the invoke in
                FixedNode afterStart = graph.start().next();
                graph.start().setNext(null);
                invoke.setNext(afterStart);
                graph.start().setNext(invoke);

                // hang exception handlers in
                MergeNode newMergeBeforeUnwind = graph.add(new MergeNode());
                EndNode oldUnwindEnd = graph.add(new EndNode());
                EndNode newUnwindEnd = graph.add(new EndNode());

                // connect exception object to new end
                eon.setNext(newUnwindEnd);

                FixedWithNextNode beforeUnwind = (FixedWithNextNode) unwind.predecessor();
                beforeUnwind.setNext(null);
                beforeUnwind.setNext(oldUnwindEnd);

                newMergeBeforeUnwind.setNext(unwind);
                newMergeBeforeUnwind.addForwardEnd(oldUnwindEnd);
                newMergeBeforeUnwind.addForwardEnd(newUnwindEnd);

                ValuePhiNode eonPhi = graph.addWithoutUnique(new ValuePhiNode(unwind.exception().stamp(NodeView.DEFAULT).unrestricted(), newMergeBeforeUnwind));
                eonPhi.addInput(unwind.exception());
                eonPhi.addInput(eon);

                unwindMergeStateTemplate.replaceAllInputs(unwind.exception(), eonPhi);
                newMergeBeforeUnwind.setStateAfter(unwindMergeStateTemplate);

                // duplicate for next occurrence
                unwindMergeStateTemplate = unwindMergeStateTemplate.duplicateWithVirtualState();
                unwind.setException(eonPhi);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After inserting exception handlers for scoped unwind paths %s", invoke);
            }
        }

        /**
         * Represents a memory session triplet in {@code @Scoped} annotated methods. A scoped memory
         * access normally consists of the session object, a base object and an offset.
         */
        record SessionCheck(ValueNode session,
                        ValueNode base,
                        ValueNode offset) {
        }

        /**
         * Computes any arguments related to memory session checks in a scoped method.
         * <p>
         * We are looking for 2 kinds of patterns here
         * <p>
         * 1: parameters starting with {@code MemorySessionImpl session, Object o, long offset}
         * followed by more we are not interested in
         * <p>
         * and
         * <p>
         * 2: parameters starting with 2 sessions
         * {@code MemorySessionImpl aSession, MemorySessionImpl bSession, Object a, long aOffset, Object b, long bOffset}
         * followed by more we are not interested in.
         */
        private static List<SessionCheck> getSessionArguments(ResolvedJavaMethod method, StructuredGraph graph, MetaAccessProvider metaAccess) {
            assert method != null;
            final ResolvedJavaType sessionType = ((AnalysisType) metaAccess.lookupJavaType(MemorySessionImpl.class)).getWrapped();
            assert sessionType != null;
            final ResolvedJavaType abstractSegmentImpl = ((AnalysisType) metaAccess.lookupJavaType(ABSTRACT_MEMORY_SEGMENT_IMPL_CLASS)).getWrapped();
            assert abstractSegmentImpl != null;
            final ResolvedJavaType baseType = ((AnalysisType) metaAccess.lookupJavaType(Object.class)).getWrapped();
            assert baseType != null;
            final ResolvedJavaType offsetType = ((AnalysisType) metaAccess.lookupJavaType(long.class)).getWrapped();
            assert offsetType != null;
            final ResolvedJavaType utilsType = ((AnalysisType) metaAccess.lookupJavaType(MAPPED_MEMORY_UTILS_PROXY_CLASS)).getWrapped();
            assert utilsType != null;
            final ResolvedJavaType classType = ((SubstitutionType) ((AnalysisType) metaAccess.lookupJavaType(Class.class)).getWrapped()).getOriginal();
            assert classType != null;

            Parameter[] p = method.getParameters();
            if (!p[0].getType().equals(sessionType)) {
                // no sessions involved
                return List.of();
            }
            if (p.length < 3) {
                // length does not match
                return List.of();
            }

            int pIndex = method.hasReceiver() ? 1 : 0;
            if (p[1].getType().equals(utilsType)) {
                // eg forceInternal(MemorySessionImpl session, MappedMemoryUtilsProxy mappedUtils,
                // FileDescriptor fd, long address, boolean isSync, long index, long length) {
                ValueNode session = graph.getParameter(pIndex++);
                pIndex++; // skip mappedUtils
                pIndex++; // skip fd
                ValueNode offset = graph.getParameter(pIndex++);
                SessionCheck check = new SessionCheck(session, null, offset);
                verifySession(sessionType, baseType, offsetType, check, metaAccess);
                return List.of(check);
            } else if (p[1].getType().equals(sessionType)) {
                // 2 session case
                ValueNode s1Session = graph.getParameter(pIndex++);
                ValueNode s2Session = graph.getParameter(pIndex++);
                ValueNode s1Base = graph.getParameter(pIndex++);
                ValueNode s1Offset = graph.getParameter(pIndex++);
                ValueNode s2Base = graph.getParameter(pIndex++);
                ValueNode s2Offset = graph.getParameter(pIndex++);
                SessionCheck s1 = new SessionCheck(s1Session, s1Base, s1Offset);
                SessionCheck s2 = new SessionCheck(s2Session, s2Base, s2Offset);
                verifySession(sessionType, baseType, offsetType, s1, metaAccess);
                verifySession(sessionType, baseType, offsetType, s2, metaAccess);
                return List.of(s1, s2);
            } else {
                // 1 session case
                ValueNode session = graph.getParameter(pIndex++);
                if (p[1].getType().equals(classType)) {
                    // example with a vmClass -
                    // storeIntoMemorySegmentScopedInternal(MemorySessionImpl session,
                    // Class<? extends V> vmClass, Class<E> e, int length,V v,
                    // AbstractMemorySegmentImpl msp, long offset,

                    /*
                     * For all patterns involving a vmClass the following holds: session is always
                     * p[0], and vmClass is always p[1]. However, in between we can have Class<E> e,
                     * int length, V v, M m then msp, offset, M m, S s, offsetInRange etc.
                     * 
                     * We always map AbstractMemorySegmentImpl msp to base and offset to offset
                     * (which always comes after). There can be arguments after offset which we
                     * ignore. And we skip all arguments between vmClass and msp. So any arg between
                     * vmClass and msp can be skipped.
                     *
                     * If any of these invariants stops holding the verifySession call below will
                     * fail hard and we will be noticed of new/changed API.
                     */
                    while (!p[pIndex].getType().equals(abstractSegmentImpl)) {
                        pIndex++;
                    }

                    // use msp as base and offset as offset
                    ValueNode base = graph.getParameter(pIndex++); // use msp
                    ValueNode offset = graph.getParameter(pIndex++);

                    SessionCheck check = new SessionCheck(session, base, offset);
                    verifySession(sessionType, baseType, offsetType, check, metaAccess);
                    return List.of(check);
                } else {
                    ValueNode base = graph.getParameter(pIndex++);
                    ValueNode offset = graph.getParameter(pIndex++);
                    SessionCheck check = new SessionCheck(session, base, offset);
                    verifySession(sessionType, baseType, offsetType, check, metaAccess);
                    return List.of(check);
                }
            }
        }

        private static void verifySession(ResolvedJavaType sessionType, ResolvedJavaType baseType, ResolvedJavaType offsetType, SessionCheck check, MetaAccessProvider metaAccess) {
            GraalError.guarantee(sessionType.isAssignableFrom(((AnalysisType) check.session.stamp(NodeView.DEFAULT).javaType(metaAccess)).getWrapped()), "Session type must match, but is %s",
                            check.session.stamp(NodeView.DEFAULT));
            if (check.base != null) {
                // base can be null
                GraalError.guarantee(baseType.isAssignableFrom(((AnalysisType) check.base.stamp(NodeView.DEFAULT).javaType(metaAccess)).getWrapped()), "Base type must match, but is %s",
                                check.base.stamp(NodeView.DEFAULT));
            }
            GraalError.guarantee(offsetType.isAssignableFrom(((AnalysisType) check.offset.stamp(NodeView.DEFAULT).javaType(metaAccess)).getWrapped()), "Offset type must match, but is %s",
                            check.offset.stamp(NodeView.DEFAULT));
        }

        /**
         * This method has memory accesses to (potentially shared) memory arenas (project panama).
         * In order to properly guarantee there are no unknown calls left in code potentially
         * accessing a shared arena, we mark this method's region (start to each sink) with scoped.
         * <p>
         * Later when we expand exception handlers for the shared arena code, we verify this based
         * on the scopes created here.
         */
        private void introduceScopeNodes() {
            ScopedMethodNode startScope = graph.add(new ScopedMethodNode());
            graph.addAfterFixed(graph.start(), startScope);
            for (Node n : graph.getNodes()) {
                if (n instanceof ControlSinkNode sink) {
                    graph.addBeforeFixed(sink, graph.add(new ScopedMethodNode(startScope)));
                }
            }
        }

        @Override
        protected RuntimeException throwParserError(Throwable e) {
            if (e instanceof UserException) {
                throw (UserException) e;
            }
            if (e instanceof UnsupportedFeatureException) {
                throw (UnsupportedFeatureException) e;
            }
            throw super.throwParserError(e);
        }

        private boolean checkWordTypes() {
            return getWordTypes() != null;
        }

        /**
         * {@link Fold} and {@link NodeIntrinsic} can be deferred during parsing/decoding. Only by
         * the end of {@linkplain SnippetTemplate#instantiate Snippet instantiation} do they need to
         * have been processed.
         * <p>
         * This is how SVM handles snippets. They are parsed with plugins disabled and then encoded
         * and stored in the image. When the snippet is needed at runtime the graph is decoded and
         * the plugins are run during the decoding process. If they aren't handled at this point
         * then they will never be handled.
         */
        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(NodeIntrinsic.class);
        }

        @Override
        protected JavaMethod lookupMethodInPool(int cpi, int opcode) {
            JavaMethod result = super.lookupMethodInPool(cpi, opcode);
            if (result == null) {
                throw VMError.shouldNotReachHere("Discovered an unresolved callee while parsing " + method.asStackTraceElement(bci()) + '.');
            }
            return result;
        }

        @Override
        protected void genLoadConstant(int cpi, int opcode) {
            try {
                if (super.lookupConstant(cpi, opcode, false) == null) {
                    Object resolvedObject = bootstrapMethodHandler.loadConstantDynamic(cpi, opcode);
                    if (resolvedObject instanceof ValueNode valueNode) {
                        JavaKind javaKind = valueNode.getStackKind();
                        assert (opcode == LDC2_W) == javaKind.needsTwoSlots();
                        frameState.push(javaKind, valueNode);
                    } else {
                        super.genLoadConstantHelper(resolvedObject, opcode);
                    }
                    return;
                }
                super.genLoadConstant(cpi, opcode);
            } catch (BootstrapMethodError | IncompatibleClassChangeError | IllegalArgumentException | NoClassDefFoundError ex) {
                bootstrapMethodHandler.handleBootstrapException(ex, "constant");
            }
        }

        /**
         * Native image can suffer high contention when synchronizing resolution and initialization
         * of a type referenced by a constant pool entry. Such synchronization should be unnecessary
         * for native-image.
         */
        @Override
        protected Object loadReferenceTypeLock() {
            return null;
        }

        @Override
        protected void maybeEagerlyResolve(int cpi, int bytecode) {
            lastUnresolvedElementException = null;
            try {
                super.maybeEagerlyResolve(cpi, bytecode);
            } catch (UnresolvedElementException e) {
                if (e.getCause() instanceof LambdaConversionException || e.getCause() instanceof LinkageError || e.getCause() instanceof IllegalAccessError) {
                    /*
                     * Ignore LinkageError, LambdaConversionException or IllegalAccessError if
                     * thrown from eager resolution attempt. This is usually followed by a call to
                     * ConstantPool.lookupType() which should return an UnresolvedJavaType which we
                     * know how to deal with.
                     */
                    lastUnresolvedElementException = e;
                } else {
                    throw e;
                }
            }
        }

        /**
         * The type resolution error, if any, encountered in the last call to
         * {@link #maybeEagerlyResolve}.
         */
        UnresolvedElementException lastUnresolvedElementException;

        @Override
        protected JavaType maybeEagerlyResolve(JavaType type, ResolvedJavaType accessingClass) {
            try {
                return super.maybeEagerlyResolve(type, accessingClass);
            } catch (LinkageError e) {
                /*
                 * Type resolution fails if the type is missing or has an incompatible change. Just
                 * erase the type by returning the Object type. This is the same handling as in
                 * WrappedConstantPool, which is not triggering when parsing is done with the
                 * HotSpot universe instead of the AnalysisUniverse.
                 */
                return getMetaAccess().lookupJavaType(Object.class);
            }
        }

        @Override
        protected void handleIllegalNewInstance(JavaType type) {
            /*
             * If linkAtBuildTime was set for type, report the error during image building,
             * otherwise defer the error reporting to runtime.
             */
            if (linkAtBuildTime) {
                String message = "Cannot instantiate " + type.toJavaName() + ". " +
                                LinkAtBuildTimeSupport.singleton().errorMessageFor(method.getDeclaringClass());
                throw new TypeInstantiationException(message);
            } else {
                ExceptionSynthesizer.throwException(this, InstantiationError.class, type.toJavaName());
            }
        }

        @Override
        protected void handleUnresolvedNewInstance(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedNewObjectArray(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedNewMultiArray(JavaType type) {
            handleUnresolvedType(type.getElementalType());
        }

        @Override
        protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
            // The INSTANCEOF byte code refers to a type that could not be resolved.
            // INSTANCEOF must not throw an exception if the object is null.
            BeginNode nullObj = graph.add(new BeginNode());
            BeginNode nonNullObj = graph.add(new BeginNode());
            append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)),
                            nullObj, nonNullObj, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Case where the object is not null, and type could not be resolved: Throw an
            // exception.
            lastInstr = nonNullObj;
            handleUnresolvedType(type);

            // Case where the object is null: INSTANCEOF does not care about the type.
            // Push zero to the byte code stack, then continue running normally.
            lastInstr = nullObj;
            frameState.push(JavaKind.Int, appendConstant(JavaConstant.INT_0));
        }

        @Override
        protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
            // The CHECKCAST byte code refers to a type that could not be resolved.
            // CHECKCAST must throw an exception if, and only if, the object is not null.
            BeginNode nullObj = graph.add(new BeginNode());
            BeginNode nonNullObj = graph.add(new BeginNode());
            append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)),
                            nullObj, nonNullObj, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Case where the object is not null, and type could not be resolved: Throw an
            // exception.
            lastInstr = nonNullObj;
            handleUnresolvedType(type);

            // Case where the object is null: CHECKCAST does not care about the type.
            // Push "null" to the byte code stack, then continue running normally.
            lastInstr = nullObj;
            frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER));
        }

        @Override
        protected void handleUnresolvedLoadConstant(JavaType unresolvedType) {
            handleUnresolvedType(unresolvedType);
        }

        @Override
        protected void handleUnresolvedExceptionType(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedStoreField(JavaField field) {
            handleUnresolvedField(field);
        }

        @Override
        protected void handleUnresolvedLoadField(JavaField field) {
            handleUnresolvedField(field);
        }

        @Override
        protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
            handleUnresolvedMethod(javaMethod);
        }

        /**
         * This method is used to delay errors from image build-time to run-time. It does so by
         * invoking a synthesized method that throws an instance like the one given as throwable in
         * the given GraphBuilderContext. If the given throwable has a non-null cause, a
         * cause-instance of the same type with a proper cause-message is created first that is then
         * passed to the method that creates and throws the outer throwable-instance.
         */
        public static <T extends Throwable> void replaceWithThrowingAtRuntime(SharedBytecodeParser b, T throwable) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                var metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
                /*
                 * Invoke method that creates and throws throwable-instance with message and cause
                 */
                var errorCtor = ReflectionUtil.lookupConstructor(true, throwable.getClass(), String.class, Throwable.class);
                boolean hasCause = errorCtor != null;
                Invoke causeCtorInvoke = null;
                if (!hasCause) {
                    /*
                     * Invoke method that creates and throws throwable-instance with message
                     */
                    errorCtor = ReflectionUtil.lookupConstructor(throwable.getClass(), String.class);
                } else {
                    /* Invoke method that creates a cause-instance with cause-message */
                    var causeCtor = ReflectionUtil.lookupConstructor(cause.getClass(), String.class);
                    ResolvedJavaMethod causeCtorMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(causeCtor), false);
                    ValueNode causeMessageNode = ConstantNode.forConstant(b.getConstantReflection().forString(cause.getMessage()), metaAccess, b.getGraph());
                    causeCtorInvoke = (Invoke) b.appendInvoke(InvokeKind.Static, causeCtorMethod, new ValueNode[]{causeMessageNode}, null);
                }

                ResolvedJavaMethod throwingMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
                ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(throwable.getMessage()), metaAccess, b.getGraph());
                /*
                 * As this invoke will always throw, its state after will not respect the expected
                 * stack effect.
                 */
                boolean verifyStates = b.getFrameStateBuilder().disableStateVerification();
                ValueNode[] args = hasCause ? new ValueNode[]{messageNode, causeCtorInvoke.asNode()} : new ValueNode[]{messageNode};
                b.appendInvoke(InvokeKind.Static, throwingMethod, args, null);
                b.getFrameStateBuilder().setStateVerification(verifyStates);
                b.add(new LoweredDeadEndNode());
            } else {
                replaceWithThrowingAtRuntime(b, throwable.getClass(), throwable.getMessage());
            }
        }

        /**
         * This method is used to delay errors from image build-time to run-time. It does so by
         * invoking a synthesized method that creates an instance of type throwableClass with
         * throwableMessage as argument and then throws that instance in the given
         * GraphBuilderContext.
         */
        public static void replaceWithThrowingAtRuntime(SharedBytecodeParser b, Class<? extends Throwable> throwableClass, String throwableMessage) {
            /*
             * This method is currently not able to replace
             * ExceptionSynthesizer.throwException(GraphBuilderContext, Method, String) because
             * there are places where GraphBuilderContext.getMetaAccess() does not contain a
             * UniverseMetaAccess (e.g. in case of ParsingReason.EarlyClassInitializerAnalysis). If
             * we can access the ParsingReason in here we will be able to get rid of throwException.
             */
            var errorCtor = ReflectionUtil.lookupConstructor(throwableClass, String.class);
            var metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
            ResolvedJavaMethod throwingMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
            ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(throwableMessage), b.getMetaAccess(), b.getGraph());
            boolean verifyStates = b.getFrameStateBuilder().disableStateVerification();
            b.appendInvoke(InvokeKind.Static, throwingMethod, new ValueNode[]{messageNode}, null);
            b.getFrameStateBuilder().setStateVerification(verifyStates);
            b.add(new LoweredDeadEndNode());
        }

        protected void handleUnresolvedType(JavaType type) {
            /*
             * If linkAtBuildTime was set for type, report the error during image building,
             * otherwise defer the error reporting to runtime.
             */
            if (linkAtBuildTime) {
                reportUnresolvedElement("type", type.toJavaName());
            } else {
                ExceptionSynthesizer.throwException(this, NoClassDefFoundError.class, type.toJavaName());
            }
        }

        private void handleUnresolvedField(JavaField field) {
            JavaType declaringClass = field.getDeclaringClass();
            if (!typeIsResolved(declaringClass)) {
                /* The field could not be resolved because its declaring class is missing. */
                handleUnresolvedType(declaringClass);
            } else {
                /*
                 * If linkAtBuildTime was set for type, report the error during image building,
                 * otherwise defer the error reporting to runtime.
                 */
                if (linkAtBuildTime) {
                    reportUnresolvedElement("field", field.format("%H.%n"));
                } else {
                    ExceptionSynthesizer.throwException(this, NoSuchFieldError.class, field.format("%H.%n"));
                }
            }
        }

        private void handleUnresolvedMethod(JavaMethod javaMethod) {
            JavaType declaringClass = javaMethod.getDeclaringClass();
            if (!typeIsResolved(declaringClass)) {
                /* The method could not be resolved because its declaring class is missing. */
                handleUnresolvedType(declaringClass);
            } else {
                /*
                 * If linkAtBuildTime was set for type, report the error during image building,
                 * otherwise defer the error reporting to runtime.
                 */
                if (linkAtBuildTime) {
                    reportUnresolvedElement("method", javaMethod.format("%H.%n(%P)"));
                } else {
                    ExceptionSynthesizer.throwException(this, findResolutionError((ResolvedJavaType) declaringClass, javaMethod), javaMethod.format("%H.%n(%P)"));
                }
            }
        }

        /**
         * Finding the correct exception that needs to be thrown at run time is a bit tricky, since
         * JVMCI does not report that information back when method resolution fails. We need to look
         * down the class hierarchy to see if there would be an appropriate method with a matching
         * signature which is just not accessible.
         * <p>
         * We do all the method lookups (to search for a method with the same signature as
         * searchMethod) using reflection and not JVMCI because the lookup can throw all sorts of
         * errors, and we want to ignore the errors without any possible side effect on AnalysisType
         * and AnalysisMethod.
         */
        private static Class<? extends IncompatibleClassChangeError> findResolutionError(ResolvedJavaType declaringType, JavaMethod searchMethod) {
            Class<?>[] searchSignature = signatureToClasses(searchMethod);
            Class<?> searchReturnType = null;
            if (searchMethod.getSignature().getReturnType(null) instanceof ResolvedJavaType) {
                searchReturnType = OriginalClassProvider.getJavaClass(searchMethod.getSignature().getReturnType(null));
            }

            Class<?> declaringClass = OriginalClassProvider.getJavaClass(declaringType);
            for (Class<?> cur = declaringClass; cur != null; cur = cur.getSuperclass()) {
                Executable[] methods = null;
                try {
                    if (searchMethod.getName().equals("<init>")) {
                        methods = cur.getDeclaredConstructors();
                    } else {
                        methods = cur.getDeclaredMethods();
                    }
                } catch (Throwable ignored) {
                    /*
                     * A linkage error was thrown, or something else random is wrong with the class
                     * files. Ignore this class.
                     */
                }
                if (methods != null) {
                    for (Executable method : methods) {
                        if (Arrays.equals(searchSignature, method.getParameterTypes()) &&
                                        (method instanceof Constructor || (searchMethod.getName().equals(method.getName()) && searchReturnType == ((Method) method).getReturnType()))) {
                            if (Modifier.isAbstract(method.getModifiers())) {
                                return AbstractMethodError.class;
                            } else {
                                return IllegalAccessError.class;
                            }
                        }
                    }
                }
                if (searchMethod.getName().equals("<init>")) {
                    /* For constructors, do not search in superclasses. */
                    break;
                }
            }
            return NoSuchMethodError.class;
        }

        private static Class<?>[] signatureToClasses(JavaMethod method) {
            int paramCount = method.getSignature().getParameterCount(false);
            Class<?>[] result = new Class<?>[paramCount];
            for (int i = 0; i < paramCount; i++) {
                JavaType parameterType = method.getSignature().getParameterType(i, null);
                if (parameterType instanceof ResolvedJavaType) {
                    result[i] = OriginalClassProvider.getJavaClass(parameterType);
                }
            }
            return result;
        }

        private void reportUnresolvedElement(String elementKind, String elementAsString) {
            reportUnresolvedElement(elementKind, elementAsString, lastUnresolvedElementException);
        }

        private void reportUnresolvedElement(String elementKind, String elementAsString, Throwable cause) {
            String message = "Discovered unresolved " + elementKind + " during parsing: " + elementAsString + ". " +
                            LinkAtBuildTimeSupport.singleton().errorMessageFor(method.getDeclaringClass());
            throw new UnresolvedElementException(message, cause);
        }

        @Override
        protected void emitCheckForInvokeSuperSpecial(ValueNode[] args) {
            /* Not implemented in SVM (GR-4854) */
        }

        @Override
        protected boolean canInlinePartialIntrinsicExit() {
            return false;
        }

        @Override
        protected void genIf(ValueNode x, Condition cond, ValueNode y) {
            if (checkWordTypes()) {
                if ((x.getStackKind() == JavaKind.Object && y.getStackKind() == getWordTypes().getWordKind()) ||
                                (x.getStackKind() == getWordTypes().getWordKind() && y.getStackKind() == JavaKind.Object)) {
                    throw UserError.abort("Should not compare Word to Object in condition at %s in %s", method, method.asStackTraceElement(bci()));
                }
            }

            super.genIf(x, cond, y);
        }

        @Override
        public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
            boolean isStatic = targetMethod.isStatic();
            if (!isStatic) {
                checkWordType(args[0], targetMethod.getDeclaringClass(), "call receiver");
            }
            for (int i = 0; i < targetMethod.getSignature().getParameterCount(false); i++) {
                checkWordType(args[i + (isStatic ? 0 : 1)], targetMethod.getSignature().getParameterType(i, null), "call argument");
            }

            return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp);
        }

        public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, Class<?> returnClass, JavaTypeProfile profile) {
            JavaType returnType = getMetaAccess().lookupJavaType(returnClass);
            return createMethodCallTarget(invokeKind, targetMethod, args, returnType, profile);
        }

        @Override
        protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
            checkWordType(returnVal, method.getSignature().getReturnType(null), "return value");

            super.genReturn(returnVal, returnKind);
        }

        @Override
        protected int minLockDepthAtMonitorExit(boolean inEpilogue) {
            /**
             * The
             * {@code javasoft.sqe.tests.vm.instr.monitorexit.monitorexit009.monitorexit00901m1.monitorexit00901m1}
             * test implies that unlocking the method synchronized object can be structured locking:
             *
             * <pre>
             * synchronized void foo() {
             *   monitorexit this // valid unlock of method synchronize object
             *   // do something
             *   monitorenter this
             *   return
             * }
             * </pre>
             *
             * To be JCK compliant, it is only required to always have at least one locked object
             * before performing a monitorexit.
             */
            return 1;
        }

        @Override
        protected void handleUnsupportedJsr(String msg) {
            genThrowUnsupportedFeatureError(msg);
        }

        @Override
        protected void handleUnstructuredLocking(String msg, boolean isDeadEnd) {
            ValueNode methodSynchronizedObjectSnapshot = methodSynchronizedObject;
            if (getDispatchBlock(bci()) == blockMap.getUnwindBlock()) {
                // methodSynchronizeObject is unlocked in synchronizedEpilogue
                genReleaseMonitors(false);

                if (method.isSynchronized() && frameState.lockDepth(false) == 0) {
                    /*
                     * methodSynchronizedObject has been released unexpectedly but the parser is
                     * already creating an IllegalMonitorStateException. Thus, set the
                     * methodSynchronizedObject to null, to signal that the synchronizedEpilogue
                     * need not be executed, as this would cause an exception loop due to the empty
                     * lock stack
                     */
                    methodSynchronizedObject = null;
                }
            }

            FrameStateBuilder stateBeforeExc = frameState.copy();
            lastInstr = emitBytecodeExceptionCheck(graph.unique(LogicConstantNode.contradiction()), true, BytecodeExceptionKind.UNSTRUCTURED_LOCKING);
            // lastInstr is the start of the never entered no-exception branch
            if (isDeadEnd) {
                append(new UnreachableControlSinkNode());
                lastInstr = null;
            } else {
                append(new UnreachableNode());
                frameState = stateBeforeExc;
            }
            methodSynchronizedObject = methodSynchronizedObjectSnapshot;
        }

        @Override
        protected void handleMismatchAtMonitorexit() {
            genReleaseMonitors(true);
            genThrowUnsupportedFeatureError("Unexpected lock object at monitorexit. Native Image enforces structured locking (JVMS 2.11.10)");
        }

        @Override
        protected FixedNode handleUnstructuredLockingForUnwindTarget(String msg, FrameStateBuilder state) {
            FrameStateBuilder oldFs = frameState;
            FixedWithNextNode oldLastInstr = lastInstr;
            frameState = state;

            BeginNode holder = graph.add(new BeginNode());
            lastInstr = holder;
            handleUnstructuredLocking(msg, true);

            frameState = oldFs;
            lastInstr = oldLastInstr;

            FixedNode result = holder.next();
            holder.setNext(null);
            holder.safeDelete();

            return result;
        }

        @Override
        protected Target checkUnstructuredLocking(Target target, BciBlock targetBlock, FrameStateBuilder mergeState) {
            if (mergeState.areLocksMergeableWith(target.getState())) {
                return target;
            }
            // Create an UnsupportedFeatureException and unwind.
            FixedWithNextNode originalLast = lastInstr;
            FrameStateBuilder originalState = frameState;

            BeginNode holder = graph.add(new BeginNode());
            lastInstr = holder;
            frameState = target.getState().copy();
            genReleaseMonitors(true);
            genThrowUnsupportedFeatureError("Incompatible lock states at merge. Native Image enforces structured locking (JVMS 2.11.10)");

            FixedNode exceptionPath = holder.next();

            Target newTarget;
            if (target.getOriginalEntry() == null) {
                newTarget = new Target(exceptionPath, frameState, null, false);
                target.getEntry().replaceAtPredecessor(exceptionPath);
                target.getEntry().safeDelete();
            } else {
                newTarget = new Target(target.getEntry(), frameState, exceptionPath, false);
                target.getOriginalEntry().replaceAtPredecessor(exceptionPath);
                target.getOriginalEntry().safeDelete();
            }

            holder.setNext(null);
            holder.safeDelete();

            lastInstr = originalLast;
            frameState = originalState;

            return newTarget;
        }

        private void genReleaseMonitors(boolean includeMethodSynchronizeObject) {
            final int monitorsToRelease = (method.isSynchronized() && !includeMethodSynchronizeObject) ? frameState.lockDepth(false) - 1 : frameState.lockDepth(false);
            for (int i = 0; i < monitorsToRelease; i++) {
                MonitorIdNode id = frameState.peekMonitorId();
                ValueNode lock = frameState.popLock();
                frameState.pushLock(lock, id);
                genMonitorExit(lock, null, bci(), includeMethodSynchronizeObject, false);
            }
        }

        private void genThrowUnsupportedFeatureError(String msg) {
            ConstantNode messageNode = ConstantNode.forConstant(getConstantReflection().forString(msg), getMetaAccess(), getGraph());
            ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(SnippetRuntime.UNSUPPORTED_FEATURE, messageNode));
            lastInstr.setNext(foreignCallNode);
            foreignCallNode.setNext(graph.add(new LoweredDeadEndNode()));
            lastInstr = null;
        }

        private void checkWordType(ValueNode value, JavaType expectedType, String reason) {
            if (expectedType.getJavaKind() == JavaKind.Object && checkWordTypes()) {
                boolean isWordTypeExpected = getWordTypes().isWord(expectedType);
                boolean isWordValue = value.getStackKind() == getWordTypes().getWordKind();

                if (isWordTypeExpected && !isWordValue) {
                    throw UserError.abort("Expected Word but got Object for %s in %s", reason, method.asStackTraceElement(bci()));
                } else if (!isWordTypeExpected && isWordValue) {
                    throw UserError.abort("Expected Object but got Word for %s in %s. One possible cause for this error is when word values are passed into lambdas as parameters " +
                                    "or from variables in an enclosing scope, which is not supported, but can be solved by instead using explicit classes (including anonymous classes).",
                                    reason, method.asStackTraceElement(bci()));
                }
            }
        }

        @Override
        protected boolean needsExplicitNullCheckException(ValueNode object) {
            return needsExplicitException() && object.getStackKind() == JavaKind.Object;
        }

        @Override
        protected boolean needsExplicitStoreCheckException(ValueNode array, ValueNode value) {
            return needsExplicitException() && value.getStackKind() == JavaKind.Object;
        }

        @Override
        public boolean needsExplicitException() {
            return explicitExceptionEdges && !parsingIntrinsic();
        }

        @Override
        protected boolean needsIncompatibleClassChangeErrorCheck() {
            /*
             * Note that the explicit check for incompatible class changes is necessary even when
             * explicit exception edges for other exception are not required. We have no mechanism
             * to do the check implicitly as part of interface calls. Interface calls are vtable
             * calls both in AOT compiled code and JIT compiled code.
             */
            return !parsingIntrinsic();
        }

        @Override
        protected boolean needsExplicitIncompatibleClassChangeError() {
            /*
             * For AOT compilation, incompatible class change checks must be BytecodeExceptionNode.
             * For JIT compilation at image run time, they must be guards.
             */
            return needsExplicitException();
        }

        @Override
        public boolean isPluginEnabled(GraphBuilderPlugin plugin) {
            return true;
        }

        protected static boolean isDeoptimizationEnabled() {
            return DeoptimizationSupport.enabled();
        }

        protected final boolean isMethodDeoptTarget() {
            return SubstrateCompilationDirectives.isDeoptTarget(method);
        }

        @Override
        protected boolean asyncExceptionLiveness() {
            /*
             * Only methods which can deoptimize need to consider live locals from asynchronous
             * exception handlers.
             */
            if (method instanceof MultiMethod) {
                return ((MultiMethod) method).getMultiMethodKey() == SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;
            }

            /*
             * If deoptimization is enabled, then must assume that any method can deoptimize at any
             * point while throwing an exception.
             */
            return isDeoptimizationEnabled();
        }

        @Override
        protected void clearNonLiveLocalsAtTargetCreation(BciBlockMapping.BciBlock block, FrameStateBuilder state) {
            /*
             * In order to match potential DeoptEntryNodes, within runtime compiled code it is not
             * possible to clear non-live locals at the start of an exception dispatch block if
             * deoptimizations can be present, as exception dispatch blocks have the same deopt bci
             * as the exception.
             */
            if ((!(isDeoptimizationEnabled() && block instanceof BciBlockMapping.ExceptionDispatchBlock)) || isMethodDeoptTarget()) {
                super.clearNonLiveLocalsAtTargetCreation(block, state);
            }
        }

        @Override
        protected void clearNonLiveLocalsAtLoopExitCreation(BciBlockMapping.BciBlock block, FrameStateBuilder state) {
            /*
             * In order to match potential DeoptEntryNodes, within runtime compiled code it is not
             * possible to clear non-live locals when deoptimizations can be present.
             */
            if (!isDeoptimizationEnabled() || isMethodDeoptTarget()) {
                super.clearNonLiveLocalsAtLoopExitCreation(block, state);
            }
        }

        @Override
        protected void createExceptionDispatch(BciBlockMapping.ExceptionDispatchBlock block) {
            if (block instanceof DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) {
                /*
                 * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
                 * Afterwards, this block should jump to either the original ExceptionDispatchBlock
                 * or the UnwindBlock if there is no handler.
                 */
                assert block instanceof DeoptimizationTargetBciBlockMapping.DeoptExceptionDispatchBlock;
                insertDeoptNode((DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) block);
                List<BciBlockMapping.BciBlock> successors = block.getSuccessors();
                assert successors.size() <= 1;
                BciBlockMapping.BciBlock successor = successors.isEmpty() ? blockMap.getUnwindBlock() : successors.get(0);
                appendGoto(successor);
            } else {
                super.createExceptionDispatch(block);
            }
        }

        @Override
        protected void iterateBytecodesForBlock(BciBlockMapping.BciBlock block) {
            if (block instanceof DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) {
                /*
                 * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
                 * Afterwards, this block should jump to the original BciBlock.
                 */
                assert block instanceof DeoptimizationTargetBciBlockMapping.DeoptBciBlock;
                assert block.getSuccessors().size() == 1 || block.getSuccessors().size() == 2;
                assert block.getSuccessor(0).isInstructionBlock();
                stream.setBCI(block.getStartBci());
                insertDeoptNode((DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) block);
                appendGoto(block.getSuccessor(0));
            } else {
                super.iterateBytecodesForBlock(block);
            }
        }

        /**
         * Inserts either a DeoptEntryNode or DeoptProxyAnchorNode into the graph.
         */
        private void insertDeoptNode(DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint deopt) {
            /*
             * Ensuring current frameState matches the expectations of the DeoptEntryInsertionPoint.
             */
            if (deopt instanceof DeoptimizationTargetBciBlockMapping.DeoptBciBlock) {
                assert !frameState.rethrowException();
            } else {
                assert deopt instanceof DeoptimizationTargetBciBlockMapping.DeoptExceptionDispatchBlock;
                assert frameState.rethrowException();
            }

            int proxifiedInvokeBci = deopt.proxifiedInvokeBci();
            boolean isProxy = deopt.isProxy();
            DeoptEntrySupport deoptNode;
            if (isProxy) {
                deoptNode = graph.add(new DeoptProxyAnchorNode(proxifiedInvokeBci));
            } else {
                boolean proxifysInvoke = deopt.proxifysInvoke();
                deoptNode = graph.add(proxifysInvoke ? DeoptEntryNode.create(proxifiedInvokeBci) : DeoptEntryNode.create());
            }
            FrameState stateAfter = frameState.create(deopt.frameStateBci(), deoptNode);
            deoptNode.setStateAfter(stateAfter);
            if (lastInstr != null) {
                lastInstr.setNext(deoptNode.asFixedNode());
            }

            if (isProxy) {
                lastInstr = (DeoptProxyAnchorNode) deoptNode;
            } else {
                DeoptEntryNode deoptEntryNode = (DeoptEntryNode) deoptNode;
                deoptEntryNode.setNext(graph.add(new DeoptEntryBeginNode()));

                /*
                 * DeoptEntries for positions not during an exception dispatch (rethrowException)
                 * also must be linked to their exception target.
                 */
                if (!deopt.isExceptionDispatch()) {
                    /*
                     * Saving frameState so that different modifications can be made for next() and
                     * exceptionEdge().
                     */
                    FrameStateBuilder originalFrameState = frameState.copy();

                    /* Creating exception object and its state after. */
                    ExceptionObjectNode newExceptionObject = graph.add(new ExceptionObjectNode(getMetaAccess()));
                    frameState.clearStack();
                    frameState.push(JavaKind.Object, newExceptionObject);
                    frameState.setRethrowException(true);
                    int bci = ((DeoptimizationTargetBciBlockMapping.DeoptBciBlock) deopt).getStartBci();
                    newExceptionObject.setStateAfter(frameState.create(bci, newExceptionObject));
                    deoptEntryNode.setExceptionEdge(newExceptionObject);

                    /* Inserting proxies for the exception edge. */
                    insertProxies(newExceptionObject, frameState);

                    /* Linking exception object to exception target. */
                    newExceptionObject.setNext(handleException(newExceptionObject, bci, false));

                    /* Now restoring FrameState so proxies can be inserted for the next() edge. */
                    frameState = originalFrameState;
                } else {
                    /* Otherwise, indicate that the exception edge is not reachable. */
                    AbstractBeginNode newExceptionEdge = graph.add(new UnreachableBeginNode());
                    newExceptionEdge.setNext(graph.add(new LoweredDeadEndNode()));
                    deoptEntryNode.setExceptionEdge(newExceptionEdge);
                }

                /* Correctly setting last instruction. */
                lastInstr = deoptEntryNode.next();
            }

            insertProxies(deoptNode.asFixedNode(), frameState);
        }

        private void insertProxies(FixedNode deoptTarget, FrameStateBuilder state) {
            /*
             * At a deoptimization point we wrap non-constant locals (and java stack elements) with
             * proxy nodes. This is to avoid global value numbering on locals (or derived
             * expressions). The effect is that when a local is accessed after a deoptimization
             * point it is really loaded from its location. This is similar to what happens in the
             * GraphBuilderPhase if entryBCI is set for OSR.
             */
            state.insertProxies(value -> createProxyNode(value, deoptTarget));
            currentDeoptIndex++;
        }

        private ValueNode createProxyNode(ValueNode value, FixedNode deoptTarget) {
            ValueNode v = DeoptProxyNode.create(value, deoptTarget, currentDeoptIndex);
            if (v.graph() != null) {
                return v;
            }
            return graph.addOrUniqueWithInputs(v);
        }

        @Override
        protected boolean forceLoopPhis() {
            return isMethodDeoptTarget() || super.forceLoopPhis();
        }

        @Override
        public boolean allowDeoptInPlugins() {
            return super.allowDeoptInPlugins();
        }

        public class BootstrapMethodHandler {
            private static final Method CLASS_DATA_METHOD = ReflectionUtil.lookupMethod(MethodHandles.class, "classData", MethodHandles.Lookup.class, String.class, Class.class);

            private Object loadConstantDynamic(int cpi, int opcode) {
                BootstrapMethodInvocation bootstrap;
                try {
                    bootstrap = constantPool.lookupBootstrapMethodInvocation(cpi, -1);
                } catch (Throwable ex) {
                    handleBootstrapException(ex, "constant");
                    return ex;
                }

                if (bootstrap != null) {
                    Executable bootstrapMethod = OriginalMethodProvider.getJavaMethod(bootstrap.getMethod());

                    /*
                     * MethodHandles.classData is used as a bootstrap method by, e.g, certain lambda
                     * classes. It is pretty simple: it just reads the classData field from its
                     * invoking class. Unfortunately, it also force-initializes the invoking class.
                     * Therefore, we cannot just treat it as "safe at build time". The class
                     * initialization is also completely useless because the invoking class must be
                     * already initialized by the time the boostrap method is executed.
                     *
                     * We replicate the implementation of the bootstrap method here without doing
                     * the class initialization.
                     */
                    if (CLASS_DATA_METHOD.equals(bootstrapMethod) && ConstantDescs.DEFAULT_NAME.equals(bootstrap.getName()) && bootstrap.getStaticArguments().isEmpty()) {
                        Class<?> invokingClass = OriginalClassProvider.getJavaClass(method.getDeclaringClass());
                        Object classData = SharedSecrets.getJavaLangAccess().classData(invokingClass);
                        if (classData == null) {
                            return JavaConstant.NULL_POINTER;
                        }
                        JavaConstant classDataConstant = getSnippetReflection().forObject(classData);
                        ResolvedJavaType bootstrapType = getConstantReflection().asJavaType(bootstrap.getType());
                        /* Safety check that the classData has the requested type. */
                        if (bootstrapType.isAssignableFrom(getMetaAccess().lookupJavaType(classDataConstant))) {
                            return classDataConstant;
                        }
                    }

                    if (!BootstrapMethodConfiguration.singleton().isCondyAllowedAtBuildTime(bootstrapMethod)) {
                        int parameterLength = bootstrap.getMethod().getParameters().length;
                        List<JavaConstant> staticArguments = bootstrap.getStaticArguments();
                        boolean isVarargs = bootstrap.getMethod().isVarArgs();
                        Class<?> typeClass = getSnippetReflection().asObject(Class.class, bootstrap.getType());
                        boolean isPrimitive = typeClass.isPrimitive();

                        for (JavaConstant argument : staticArguments) {
                            if (argument.getJavaKind().isObject()) {
                                Object arg = getSnippetReflection().asObject(Object.class, argument);
                                if (arg instanceof UnresolvedJavaType) {
                                    return arg;
                                }
                            }
                        }

                        if (isBootstrapInvocationInvalid(bootstrap, parameterLength, staticArguments, isVarargs, typeClass)) {
                            /*
                             * The number of provided arguments does not match the signature of the
                             * bootstrap method or the provided type does not match the return type
                             * of the bootstrap method. Calling lookupConstant with
                             * allowBootstrapMethodInvocation set to true correctly throws the
                             * intended BootstrapMethodError.
                             */
                            return SharedBytecodeParser.super.lookupConstant(cpi, opcode, true);
                        }

                        Object resolvedObject = resolveLinkedObject(bci(), cpi, opcode, bootstrap, parameterLength, staticArguments, isVarargs, isPrimitive);
                        if (resolvedObject instanceof Throwable) {
                            return resolvedObject;
                        }
                        ValueNode resolvedObjectNode = (ValueNode) resolvedObject;

                        if (typeClass.isPrimitive()) {
                            JavaKind constantKind = getMetaAccessExtensionProvider().getStorageKind(getMetaAccess().lookupJavaType(typeClass));
                            resolvedObjectNode = append(UnboxNode.create(getMetaAccess(), getConstantReflection(), resolvedObjectNode, constantKind));
                        }

                        return resolvedObjectNode;
                    }
                }
                return SharedBytecodeParser.super.lookupConstant(cpi, opcode, true);
            }

            /**
             * Produces a graph executing the given bootstrap method and linking the result if it is
             * the first execution. This corresponds to the following code:
             *
             * <pre>
             * if (bootstrapMethodInfo.object == null) {
             *     MethodHandles.Lookup lookup = MethodHandles.lookup();
             *     try {
             *         bootstrapMethodInfo.object = bootstrapMethod(lookup, name, type, staticArguments);
             *     } catch (Throwable throwable) {
             *         bootstrapMethodInfo.object = new ExceptionWrapper(throwable);
             *     }
             * }
             * Object result = bootstrapMethodInfo.object;
             * if (result instanceOf ExceptionWrapper exceptionWrapper) {
             *     throw exceptionWrapper.throwable;
             * }
             * return result;
             * </pre>
             */
            protected Object resolveLinkedObject(int bci, int cpi, int opcode, BootstrapMethodInvocation bootstrap, int parameterLength, List<JavaConstant> staticArgumentsList,
                            boolean isVarargs, boolean isPrimitiveConstant) {
                ResolvedJavaMethod bootstrapMethod = bootstrap.getMethod();

                /* Step 1: Initialize the BootstrapMethodInfo. */

                BootstrapMethodRecord bootstrapMethodRecord = new BootstrapMethodRecord(bci, cpi, ((AnalysisMethod) method).getMultiMethod(MultiMethod.ORIGINAL_METHOD));
                BootstrapMethodInfo bootstrapMethodInfo = BootstrapMethodConfiguration.singleton().getBootstrapMethodInfoCache().computeIfAbsent(bootstrapMethodRecord,
                                key -> new BootstrapMethodInfo());
                ConstantNode bootstrapMethodInfoNode = ConstantNode.forConstant(getSnippetReflection().forObject(bootstrapMethodInfo), getMetaAccess(), getGraph());

                /*
                 * Step 2: Check if the call site or the constant is linked or if it previously
                 * threw an exception to rethrow it.
                 */

                Field bootstrapObjectField = ReflectionUtil.lookupField(BootstrapMethodInfo.class, "object");
                AnalysisField bootstrapObjectResolvedField = (AnalysisField) getMetaAccess().lookupJavaField(bootstrapObjectField);
                LoadFieldNode bootstrapObjectFieldNode = append(LoadFieldNode.create(getAssumptions(), bootstrapMethodInfoNode, bootstrapObjectResolvedField, MemoryOrderMode.ACQUIRE));

                ValueNode[] arguments = new ValueNode[parameterLength];

                /*
                 * The bootstrap method can be VarArgs, which means we have to group the last static
                 * arguments in an array.
                 */
                if (isVarargs) {
                    JavaType varargClass = bootstrapMethod.getParameters()[parameterLength - 1].getType().getComponentType();
                    arguments[arguments.length - 1] = append(new NewArrayNode(((AnalysisMetaAccess) getMetaAccess()).getUniverse().lookup(varargClass),
                                    ConstantNode.forInt(staticArgumentsList.size() - arguments.length + 4, getGraph()), true));
                }

                /*
                 * Prepare the static arguments before continuing as an exception in a nested
                 * constant dynamic aborts the graph generation.
                 */
                for (int i = 0; i < staticArgumentsList.size(); ++i) {
                    JavaConstant constant = staticArgumentsList.get(i);
                    ValueNode currentNode;
                    if (constant instanceof PrimitiveConstant primitiveConstant) {
                        int argCpi = primitiveConstant.asInt();
                        Object argConstant = loadConstantDynamic(argCpi, opcode == Opcode.INVOKEDYNAMIC.bytecode() ? Opcode.LDC.bytecode() : opcode);
                        if (argConstant instanceof ValueNode valueNode) {
                            ResolvedJavaMethod.Parameter[] parameters = bootstrapMethod.getParameters();
                            if (valueNode.getStackKind().isPrimitive() && i + 3 <= parameters.length && !parameters[i + 3].getKind().isPrimitive()) {
                                currentNode = append(BoxNode.create(valueNode, getMetaAccess().lookupJavaType(valueNode.getStackKind().toBoxedJavaClass()), valueNode.getStackKind()));
                            } else {
                                currentNode = valueNode;
                            }
                        } else if (argConstant instanceof Throwable || argConstant instanceof UnresolvedJavaType) {
                            /* A nested constant dynamic threw. */
                            return argConstant;
                        } else if (argConstant instanceof JavaConstant javaConstant) {
                            currentNode = ConstantNode.forConstant(javaConstant, getMetaAccess(), getGraph());
                        } else {
                            throw VMError.shouldNotReachHere("Unexpected constant value: " + argConstant);
                        }
                        if (isVarargs && i + 4 >= parameterLength) {
                            /* Primitive arguments in the vararg area have to be boxed. */
                            JavaKind stackKind = currentNode.getStackKind();
                            if (stackKind.isPrimitive()) {
                                currentNode = append(BoxNode.create(currentNode, getMetaAccess().lookupJavaType(stackKind.toBoxedJavaClass()), stackKind));
                            }
                        }
                    } else {
                        /*
                         * Primitive arguments in the non vararg area have to be unboxed to match
                         * the parameters of the bootstrap method, which is handled by
                         * createConstant. The arguments in the vararg area have to stay boxed as
                         * they are passed as Objects.
                         */
                        currentNode = (isVarargs && i + 4 >= parameterLength) ? ConstantNode.forConstant(constant, getMetaAccess(), getGraph()) : createConstant(constant);
                    }
                    addArgument(isVarargs, arguments, i + 3, currentNode);
                }

                LogicNode condition = graph.unique(IsNullNode.create(bootstrapObjectFieldNode));

                EndNode falseEnd = graph.add(new EndNode());

                /*
                 * Step 3: If the call site or the constant is not linked, execute the bootstrap
                 * method and link the outputted call site or constant to the constant pool index.
                 * Otherwise, go to step 4.
                 */

                InvokeWithExceptionNode lookup = invokeMethodAndAdd(bci, MethodHandles.class, MethodHandles.Lookup.class, "lookup", InvokeKind.Static, ValueNode.EMPTY_ARRAY);
                ValueNode lookupNode = frameState.pop(JavaKind.Object);

                /*
                 * Step 3.1: Prepare the arguments for the bootstrap method. The first three are
                 * always the same. The other ones are the static arguments.
                 */

                addArgument(isVarargs, arguments, 0, lookupNode);
                ConstantNode bootstrapName = ConstantNode.forConstant(getConstantReflection().forString(bootstrap.getName()), getMetaAccess(), getGraph());
                addArgument(isVarargs, arguments, 1, bootstrapName);
                addArgument(isVarargs, arguments, 2, ConstantNode.forConstant(bootstrap.getType(), getMetaAccess(), getGraph()));

                if (bootstrapMethod.isConstructor()) {
                    ValueNode[] oldArguments = arguments;
                    arguments = new ValueNode[arguments.length + 1];
                    arguments[0] = graph.add(new NewInstanceNode(bootstrapMethod.getDeclaringClass(), true));
                    System.arraycopy(oldArguments, 0, arguments, 1, oldArguments.length);
                }

                Class<?> returnClass = OriginalClassProvider.getJavaClass(bootstrapMethod.getSignature().getReturnType(null));

                InvokeWithExceptionNode bootstrapObject;
                ValueNode bootstrapObjectNode;
                /* A bootstrap method can only be either static or a constructor. */
                if (bootstrapMethod.isConstructor()) {
                    bootstrapObject = invokeMethodAndAddCustomExceptionHandler(bci, void.class, InvokeKind.Special, arguments, bootstrapMethod);
                    bootstrapObjectNode = arguments[0];
                } else {
                    bootstrapObject = invokeMethodAndAddCustomExceptionHandler(bci, returnClass, InvokeKind.Static, arguments, bootstrapMethod);
                    bootstrapObjectNode = frameState.pop(bootstrapObject.getStackKind());
                }
                ValueNode finalBootstrapObjectNode = bootstrapObjectNode;
                FixedWithNextNode fixedFinalBootstrapObjectNode = null;
                if (isPrimitiveConstant) {
                    fixedFinalBootstrapObjectNode = graph.add(
                                    BoxNode.create(bootstrapObjectNode, getMetaAccess().lookupJavaType(bootstrapObjectNode.getStackKind().toBoxedJavaClass()), bootstrapObjectNode.getStackKind()));
                    finalBootstrapObjectNode = fixedFinalBootstrapObjectNode;
                }

                /*
                 * If an exception occurs during the bootstrap method execution, store it in the
                 * BootstrapMethodInfo instead of directly throwing.
                 */
                InvokeWithExceptionNode exceptionWrapperNode = wrapException(bci, bootstrapObject.exceptionEdge());
                bootstrapObject.exceptionEdge().setNext(exceptionWrapperNode);
                MergeNode linkMerge = graph.add(new MergeNode());
                linkMerge.setStateAfter(createFrameState(stream.nextBCI(), linkMerge));
                EndNode n = graph.add(new EndNode());
                exceptionWrapperNode.setNext(n);
                linkMerge.addForwardEnd(n);

                EndNode noExceptionEnd = graph.add(new EndNode());
                finalBootstrapObjectNode = graph.unique(new ValuePhiNode(StampFactory.object(), linkMerge, exceptionWrapperNode, finalBootstrapObjectNode));

                /*
                 * Step 3.2: Link the call site or the constant outputted by the bootstrap method.
                 */

                ConstantNode nullConstant = ConstantNode.forConstant(JavaConstant.NULL_POINTER, getMetaAccess(), getGraph());
                ValueNode offset = graph.addOrUniqueWithInputs(FieldOffsetNode.create(JavaKind.Long, bootstrapObjectResolvedField));
                FieldLocationIdentity fieldLocationIdentity = new FieldLocationIdentity(bootstrapObjectResolvedField);
                FixedWithNextNode linkBootstrapObject = graph.add(
                                new UnsafeCompareAndSwapNode(bootstrapMethodInfoNode, offset, nullConstant, finalBootstrapObjectNode, JavaKind.Object, fieldLocationIdentity, MemoryOrderMode.RELEASE));
                ((StateSplit) linkBootstrapObject).setStateAfter(createFrameState(stream.nextBCI(), (StateSplit) linkBootstrapObject));

                EndNode trueEnd = graph.add(new EndNode());

                if (bootstrapMethod.isConstructor()) {
                    lookup.setNext((FixedNode) bootstrapObjectNode);
                    ((FixedWithNextNode) bootstrapObjectNode).setNext(bootstrapObject);
                } else {
                    lookup.setNext(bootstrapObject);
                }
                if (isPrimitiveConstant) {
                    bootstrapObject.setNext(fixedFinalBootstrapObjectNode);
                    fixedFinalBootstrapObjectNode.setNext(noExceptionEnd);
                } else {
                    bootstrapObject.setNext(noExceptionEnd);
                }
                linkMerge.addForwardEnd(noExceptionEnd);
                linkMerge.setNext(linkBootstrapObject);
                linkBootstrapObject.setNext(trueEnd);

                append(new IfNode(condition, lookup, falseEnd, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

                MergeNode mergeNode = append(new MergeNode());
                mergeNode.setStateAfter(createFrameState(stream.nextBCI(), mergeNode));
                mergeNode.addForwardEnd(trueEnd);
                mergeNode.addForwardEnd(falseEnd);

                /* Step 4: Fetch the object from the BootstrapMethodInfo. */

                LoadFieldNode result = append(LoadFieldNode.create(getAssumptions(), bootstrapMethodInfoNode, bootstrapObjectResolvedField, MemoryOrderMode.ACQUIRE));

                /* If the object is an exception, it is thrown. */
                TypeReference exceptionWrapper = TypeReference.create(getAssumptions(), getMetaAccess().lookupJavaType(ExceptionWrapper.class));
                LogicNode instanceOfException = graph.unique(InstanceOfNode.create(exceptionWrapper, result));
                EndNode checkExceptionTrueEnd = graph.add(new EndNode());
                EndNode checkExceptionFalseEnd = graph.add(new EndNode());

                Field exceptionWrapperField = ReflectionUtil.lookupField(ExceptionWrapper.class, "throwable");
                ResolvedJavaField exceptionWrapperResolvedField = getMetaAccess().lookupJavaField(exceptionWrapperField);
                LoadFieldNode throwable = graph.add(LoadFieldNode.create(getAssumptions(), result, exceptionWrapperResolvedField));
                InvokeWithExceptionNode bootstrapMethodError = throwBootstrapMethodError(bci, throwable);
                throwable.setNext(bootstrapMethodError);
                bootstrapMethodError.setNext(checkExceptionTrueEnd);

                append(new IfNode(instanceOfException, throwable, checkExceptionFalseEnd, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

                MergeNode checkExceptionMergeNode = append(new MergeNode());
                checkExceptionMergeNode.setStateAfter(createFrameState(stream.nextBCI(), checkExceptionMergeNode));
                checkExceptionMergeNode.addForwardEnd(checkExceptionTrueEnd);
                checkExceptionMergeNode.addForwardEnd(checkExceptionFalseEnd);

                return result;
            }

            private void addArgument(boolean isVarargs, ValueNode[] arguments, int i, ValueNode currentNode) {
                if (isVarargs && i >= arguments.length - 1) {
                    VMError.guarantee(currentNode.getStackKind() == JavaKind.Object, "Must have an Object value to store into an Objet[] array: %s at index %s", currentNode, i);
                    StoreIndexedNode storeIndexedNode = append(new StoreIndexedNode(arguments[arguments.length - 1], ConstantNode.forInt(i + 1 - arguments.length, getGraph()), null, null,
                                    JavaKind.Object, currentNode));
                    storeIndexedNode.setStateAfter(createFrameState(stream.nextBCI(), storeIndexedNode));
                } else {
                    arguments[i] = currentNode;
                }
            }

            private InvokeWithExceptionNode wrapException(int bci, ValueNode exception) {
                Constructor<?> exceptionWrapperCtor = ReflectionUtil.lookupConstructor(ExceptionWrapper.class, Throwable.class);
                AnalysisMetaAccess metaAccess = (AnalysisMetaAccess) getMetaAccess();
                ResolvedJavaMethod exceptionWrapperMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(exceptionWrapperCtor), false);
                InvokeWithExceptionNode exceptionWrapper = invokeMethodAndAdd(bci, ExceptionWrapper.class, InvokeKind.Static, new ValueNode[]{exception}, exceptionWrapperMethod);
                frameState.pop(JavaKind.Object);
                return exceptionWrapper;
            }

            protected InvokeWithExceptionNode throwBootstrapMethodError(int bci, ValueNode exception) {
                Constructor<?> errorCtor = ReflectionUtil.lookupConstructor(BootstrapMethodError.class, Throwable.class);
                AnalysisMetaAccess metaAccess = (AnalysisMetaAccess) getMetaAccess();
                ResolvedJavaMethod bootstrapMethodErrorMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
                return invokeMethodAndAdd(bci, void.class, InvokeKind.Static, new ValueNode[]{exception}, bootstrapMethodErrorMethod);
            }

            /**
             * Perform checks on a bootstrap method invocation. The checks verifying the number of
             * arguments cannot be performed at run time as they cause build time errors when not
             * met. The type checks could be replaced by run time class casts.
             * <p>
             * HotSpot performs those checks in {@code ConstantBootstraps.makeConstant} and
             * {@code CallSite.makeSite} by casting the classes and method types. Since the
             * bootstrap method is converted to a {@link java.lang.invoke.MethodHandle}, incorrect
             * types in the bootstrap method declaration are detected in
             * {@link java.lang.invoke.MethodHandle#invoke(Object...)}.
             */
            private boolean isBootstrapInvocationInvalid(BootstrapMethodInvocation bootstrap, int parameterLength, List<JavaConstant> staticArgumentsList, boolean isVarargs, Class<?> typeClass) {
                ResolvedJavaMethod bootstrapMethod = bootstrap.getMethod();
                return (isVarargs && parameterLength > (3 + staticArgumentsList.size())) || (!isVarargs && parameterLength != (3 + staticArgumentsList.size())) ||
                                (bootstrapMethod.getSignature().getReturnType(null) instanceof UnresolvedJavaType) ||
                                !(OriginalClassProvider.getJavaClass(bootstrapMethod.getSignature().getReturnType(null)).isAssignableFrom(typeClass) || bootstrapMethod.isConstructor()) ||
                                !checkBootstrapParameters(bootstrapMethod, bootstrap.getStaticArguments(), true);
            }

            protected boolean checkBootstrapParameters(ResolvedJavaMethod bootstrapMethod, List<JavaConstant> staticArguments, boolean condy) {
                int parametersLength = bootstrapMethod.getParameters().length;
                Class<?>[] parameters = signatureToClasses(bootstrapMethod);
                if (bootstrapMethod.isVarArgs()) {
                    /*
                     * The mismatch in the number of arguments causes a WrongMethodTypeException in
                     * MethodHandle.invoke on the bootstrap method invocation.
                     */
                    parameters[parametersLength - 1] = parameters[parametersLength - 1].getComponentType();
                }
                if (!bootstrapMethod.isVarArgs() && 3 + staticArguments.size() != parameters.length) {
                    return false;
                }
                for (int i = 0; i < parametersLength; ++i) {
                    if (i == 0) {
                        /* HotSpot performs this check in ConstantBootstraps#makeConstant. */
                        if (!(condy ? parameters[i].equals(MethodHandles.Lookup.class) : parameters[i].isAssignableFrom(MethodHandles.Lookup.class))) {
                            return false;
                        }
                    } else if (i == 1) {
                        /*
                         * This parameter is converted to a String in
                         * MethodHandleNatives#linkCallSite and
                         * MethodHandleNatives#linkDynamicConstant. Not having a String here causes
                         * a ClassCastException in MethodHandle.invoke.
                         */
                        if (!parameters[i].isAssignableFrom(String.class)) {
                            return false;
                        }
                    } else if (i == 2) {
                        /*
                         * This parameter is converted to a Class/MethodType in
                         * MethodHandleNatives#linkCallSite/MethodHandleNatives#linkDynamicConstant.
                         * Not having a Class/MethodType here causes a ClassCastException in
                         * MethodHandle.invoke.
                         */
                        if (!parameters[i].isAssignableFrom(condy ? Class.class : MethodType.class)) {
                            return false;
                        }
                    } else {
                        if (!(bootstrapMethod.isVarArgs() && staticArguments.size() == i - 3) && staticArguments.get(i - 3) instanceof ImageHeapConstant imageHeapConstant) {
                            Class<?> parameterClass = OriginalClassProvider.getJavaClass(imageHeapConstant.getType());
                            /*
                             * Having incompatible types here causes a ClassCastException in
                             * MethodHandle.invoke on the bootstrap method invocation.
                             */
                            if (!(parameters[i].isAssignableFrom(parameterClass) || toUnboxedClass(parameters[i]).isAssignableFrom(toUnboxedClass(parameterClass)))) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }

            protected void handleBootstrapException(Throwable ex, String elementKind) {
                if (linkAtBuildTime) {
                    reportUnresolvedElement(elementKind, method.format("%H.%n(%P)"), ex);
                } else {
                    replaceWithThrowingAtRuntime(SharedBytecodeParser.this, ex);
                }
            }

            protected ConstantNode createConstant(JavaConstant constant) {
                JavaConstant primitiveConstant = getConstantReflection().unboxPrimitive(constant);
                return ConstantNode.forConstant(primitiveConstant == null ? constant : primitiveConstant, getMetaAccess(), getGraph());
            }

            protected Invoke invokeMethodAndAppend(int bci, Class<?> clazz, Class<?> returnClass, String name, InvokeKind invokeKind, ValueNode[] arguments, Class<?>... classes) {
                ResolvedJavaMethod invokedMethod = lookupResolvedJavaMethod(clazz, name, classes);
                CallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, invokedMethod, arguments, returnClass, null));
                return createNonInlinedInvoke(ExceptionEdgeAction.INCLUDE_AND_HANDLE, bci, callTarget, callTarget.returnStamp().getTrustedStamp().getStackKind());
            }

            protected InvokeWithExceptionNode invokeMethodAndAdd(int bci, Class<?> clazz, Class<?> returnClass, String name, InvokeKind invokeKind, ValueNode[] arguments,
                            Class<?>... classes) {
                ResolvedJavaMethod invokedMethod = lookupResolvedJavaMethod(clazz, name, classes);
                return invokeMethodAndAdd(bci, returnClass, invokeKind, arguments, invokedMethod);
            }

            protected InvokeWithExceptionNode invokeMethodAndAdd(int bci, Class<?> returnClass, InvokeKind invokeKind, ValueNode[] arguments, ResolvedJavaMethod invokedMethod) {
                CallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, invokedMethod, arguments, returnClass, null));
                InvokeWithExceptionNode invoke = graph.add(
                                createInvokeWithException(bci, callTarget, callTarget.returnStamp().getTrustedStamp().getStackKind(), ExceptionEdgeAction.INCLUDE_AND_HANDLE));
                invoke.setStateAfter(createFrameState(stream.nextBCI(), invoke));
                return invoke;
            }

            protected InvokeWithExceptionNode invokeMethodAndAddCustomExceptionHandler(int bci, Class<?> returnClass, InvokeKind invokeKind, ValueNode[] arguments, ResolvedJavaMethod invokedMethod) {
                CallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, invokedMethod, arguments, returnClass, null));
                ExceptionObjectNode exceptionObject = graph.add(new ExceptionObjectNode(getMetaAccess()));
                FrameStateBuilder dispatchState = frameState.copy();
                dispatchState.clearStack();
                dispatchState.pushReturn(JavaKind.Object, exceptionObject);
                dispatchState.setRethrowException(true);
                exceptionObject.setStateAfter(dispatchState.create(stream.nextBCI(), exceptionObject));
                InvokeWithExceptionNode invoke = graph.add(new InvokeWithExceptionNode(callTarget, exceptionObject, bci));
                frameState.pushReturn(callTarget.returnStamp().getTrustedStamp().getStackKind(), invoke);
                invoke.setStateAfter(createFrameState(stream.nextBCI(), invoke));
                return invoke;
            }

            private ResolvedJavaMethod lookupResolvedJavaMethod(Class<?> clazz, String name, Class<?>... classes) {
                try {
                    return getMetaAccess().lookupJavaMethod(clazz.getDeclaredMethod(name, classes));
                } catch (NoSuchMethodException e) {
                    throw GraalError.shouldNotReachHere("Could not find method in " + clazz + " named " + name);
                }
            }
        }

        @Override
        public void setIsParsingScopedMemoryMethod(ValueNode scopedMemorySession) {
            if (scopedMemorySessions == null) {
                scopedMemorySessions = new ArrayList<>();
            }
            scopedMemorySessions.add(scopedMemorySession);
        }

        @Override
        public List<ValueNode> getScopedMemorySessions() {
            return scopedMemorySessions;
        }
    }
}
