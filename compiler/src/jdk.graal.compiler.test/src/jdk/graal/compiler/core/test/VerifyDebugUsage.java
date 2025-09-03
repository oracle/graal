/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.debug.DebugContext.BASIC_LEVEL;

import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.SymbolicSnippetEncoder;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.OptimizationLogImpl;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.phases.common.ReportHotCodePhase;
import jdk.graal.compiler.replacements.ReplacementsImpl;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.test.GraalTest.MethodSource;
import jdk.graal.compiler.truffle.PerformanceInformationHandler;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.phases.inlining.CallTree;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that call sites calling one of the methods in {@link DebugContext} use them correctly.
 * Correct usage of the methods in {@link DebugContext} requires call sites to not eagerly evaluate
 * their arguments. Additionally this phase verifies that no argument is the result of a call to
 * {@link StringBuilder#toString()} or {@link StringBuffer#toString()}. Ideally the parameters at
 * call sites of {@link DebugContext} are eliminated, and do not produce additional allocations, if
 * {@link DebugContext#isDumpEnabled(int)} (or {@link DebugContext#isLogEnabled(int)}, ...) is
 * {@code false}.
 *
 * Methods in {@link DebugContext} checked by this phase are various different versions of
 * {@link DebugContext#log(String)} , {@link DebugContext#dump(int, Object, String)},
 * {@link DebugContext#logAndIndent(String)} and {@link DebugContext#verify(Object, String)}.
 */
public class VerifyDebugUsage extends VerifyStringFormatterUsage {

    MetaAccessProvider metaAccess;

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        metaAccess = context.getMetaAccess();
        ResolvedJavaType debugType = metaAccess.lookupJavaType(DebugContext.class);
        ResolvedJavaType nodeType = metaAccess.lookupJavaType(Node.class);
        ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);
        ResolvedJavaType graalErrorType = metaAccess.lookupJavaType(GraalError.class);
        ResolvedJavaType errorType = metaAccess.lookupJavaType(Error.class);

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            ResolvedJavaType calleeDeclaringClass = callee.getDeclaringClass();
            if (calleeDeclaringClass.equals(debugType)) {
                boolean isDump = calleeName.equals("dump");
                if (calleeName.equals("log") || calleeName.equals("logAndIndent") || calleeName.equals("verify") || isDump) {
                    verifyParameters(metaAccess, t, t.arguments(), stringType, isDump ? 2 : 1);
                }
            }
            if (calleeDeclaringClass.isAssignableFrom(nodeType)) {
                if (calleeName.equals("assertTrue") || calleeName.equals("assertFalse")) {
                    verifyParameters(metaAccess, t, t.arguments(), stringType, 1);
                }
            }
            if (calleeDeclaringClass.isAssignableFrom(graalErrorType) && !calleeDeclaringClass.equals(errorType) && !graph.method().getDeclaringClass().isAssignableFrom(graalErrorType)) {
                if (calleeName.equals("guarantee")) {
                    verifyParameters(metaAccess, t, t.arguments(), stringType, 0);
                }
                if (calleeName.equals("<init>") && callee.getSignature().getParameterCount(false) == 2) {
                    verifyParameters(metaAccess, t, t.arguments(), stringType, 1);
                }
            }
        }
    }

    private static final Set<Integer> DebugLevels = CollectionsUtil.setOf(DebugContext.ENABLED_LEVEL, BASIC_LEVEL, DebugContext.INFO_LEVEL, DebugContext.VERBOSE_LEVEL,
                    DebugContext.DETAILED_LEVEL, DebugContext.VERY_DETAILED_LEVEL);

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with the {@code level}
     * parameter bound to {@link DebugContext#BASIC_LEVEL} and the {@code object} parameter bound to
     * a {@link StructuredGraph} value.
     *
     * This allow list exists to ensure any increase in graph dumps is in line with the policy
     * outlined by {@link DebugContext#BASIC_LEVEL}. If you add a *justified* graph dump at this
     * level, then update the allow list.
     */
    private static final Set<MethodSource> BasicLevelStructuredGraphDumpAllowList = CollectionsUtil.setOf(
                    MethodSource.of(BasePhase.class, "dumpAfter"),
                    MethodSource.of(BasePhase.class, "dumpBefore"),
                    MethodSource.of(GraalCompiler.class, "emitFrontEnd"),
                    MethodSource.of(PerformanceInformationHandler.class, "reportPerformanceWarnings"),
                    MethodSource.of(TruffleCompilerImpl.class, "compilePEGraph"),
                    MethodSource.of(VerifyDebugUsageTest.ValidDumpUsagePhase.class, "run"),
                    MethodSource.of(VerifyDebugUsageTest.InvalidConcatDumpUsagePhase.class, "run"),
                    MethodSource.of(VerifyDebugUsageTest.InvalidDumpUsagePhase.class, "run"),
                    MethodSource.of(SymbolicSnippetEncoder.class, "verifySnippetEncodeDecode"),
                    MethodSource.of("com.oracle.graal.pointsto.phases.InlineBeforeAnalysis", "decodeGraph"),
                    MethodSource.of("com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport", "decodeGraph"),
                    MethodSource.of("com.oracle.svm.hosted.classinitialization.SimulateClassInitializerAbortException", "doAbort"),
                    MethodSource.of(CallTree.class, "dumpBasic"));

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with the {@code level}
     * parameter bound to {@link DebugContext#INFO_LEVEL} and the {@code object} parameter bound to
     * a {@link StructuredGraph} value.
     *
     * This allow list exists to ensure any increase in graph dumps is in line with the policy
     * outlined by {@link DebugContext#INFO_LEVEL}. If you add a *justified* graph dump at this
     * level, then update the allow list.
     */
    private static final Set<MethodSource> InfoLevelStructuredGraphDumpAllowList = CollectionsUtil.setOf(
                    MethodSource.of(GraalCompiler.class, "emitFrontEnd"),
                    MethodSource.of(BasePhase.class, "dumpAfter"),
                    MethodSource.of(ReplacementsImpl.GraphMaker.class, "makeGraph"),
                    MethodSource.of(SnippetTemplate.class, "instantiate"),
                    MethodSource.of(SnippetTemplate.class, "<init>"),
                    MethodSource.of(SymbolicSnippetEncoder.class, "verifySnippetEncodeDecode"),
                    MethodSource.of(CallTree.class, "dumpInfo"));

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with a variable
     * {@code level} parameter and the {@code object} parameter bound to a {@link StructuredGraph}
     * value.
     *
     * If you add a *justified* graph dump with variable level parameter, then update the allow
     * list.
     */
    private static final Set<MethodSource> ParameterizedLevelStructuredGraphDumpAllowList = CollectionsUtil.setOf(
                    MethodSource.of(CallTree.class, "GraphManager", "pe"));

    @Override
    protected void verifyParameters(MetaAccessProvider metaAccess1, MethodCallTargetNode debugCallTarget, List<? extends ValueNode> args, ResolvedJavaType stringType,
                    int startArgIdx, int varArgsIndex) {
        super.verifyParameters(metaAccess1, debugCallTarget, args, stringType, startArgIdx, varArgsIndex);

        ResolvedJavaMethod verifiedCallee = debugCallTarget.targetMethod();
        if (verifiedCallee.getName().equals("dump")) {
            /*
             * The optimization log dumps at a parametrized level, but it must be at least
             * OptimizationLog.MINIMUM_LOG_LEVEL.
             */
            EconomicSet<String> allowedClasses = EconomicSet.create();
            allowedClasses.add(OptimizationLogImpl.OptimizationEntryImpl.class.getName());
            allowedClasses.add(ReportHotCodePhase.class.getName());
            String callerClassName = debugCallTarget.graph().method().format("%H");
            if (!allowedClasses.contains(callerClassName)) {
                ResolvedJavaMethod callerMethod = debugCallTarget.graph().method();
                Integer dumpLevel = ParameterizedLevelStructuredGraphDumpAllowList.stream().noneMatch(ms -> ms.matches(callerMethod))
                                ? verifyDumpLevelParameter(debugCallTarget, verifiedCallee, args.get(1))
                                : null;
                verifyDumpObjectParameter(debugCallTarget, args.get(2), verifiedCallee, dumpLevel);
            }
        }
    }

    /**
     * The {@code level} arg for the {@code Debug.dump(...)} methods must be a reference to one of
     * the {@code Debug.*_LEVEL} constants.
     */
    protected int verifyDumpLevelParameter(MethodCallTargetNode debugCallTarget, ResolvedJavaMethod verifiedCallee, ValueNode arg)
                    throws VerifyPhase.VerificationError {
        // The 'level' arg for the Debug.dump(...) methods must be a reference to one of
        // the Debug.*_LEVEL constants.

        Constant c = arg.asConstant();
        if (c != null) {
            int dumpLevel = ((PrimitiveConstant) c).asInt();
            if (!DebugLevels.contains(dumpLevel)) {
                throw new VerificationError(
                                debugCallTarget, "parameter 0 of call to %s does not match a Debug.*_LEVEL constant: %s.%n", verifiedCallee.format("%H.%n(%p)"), dumpLevel);
            }
            return dumpLevel;
        }
        throw new VerificationError(
                        debugCallTarget, "parameter 0 of call to %s must be a constant, not %s.%n", verifiedCallee.format("%H.%n(%p)"), arg);
    }

    protected void verifyDumpObjectParameter(MethodCallTargetNode debugCallTarget, ValueNode arg, ResolvedJavaMethod verifiedCallee, Integer dumpLevel)
                    throws VerifyPhase.VerificationError {
        ResolvedJavaType argType = ((ObjectStamp) arg.stamp(NodeView.DEFAULT)).type();
        // GR-64309: Calls returning interface type are built with an unrestricted stamp. ArgType is
        // null for SubstrateInstalledCode.
        if (argType != null && metaAccess.lookupJavaType(Graph.class).isAssignableFrom(argType)) {
            verifyStructuredGraphDumping(debugCallTarget, verifiedCallee, dumpLevel);
        }
    }

    /**
     * Verifies that dumping a {@link StructuredGraph} at level {@link DebugContext#BASIC_LEVEL} or
     * {@link DebugContext#INFO_LEVEL} only occurs in white-listed methods.
     */
    protected void verifyStructuredGraphDumping(MethodCallTargetNode debugCallTarget, ResolvedJavaMethod verifiedCallee, Integer dumpLevel)
                    throws VerifyPhase.VerificationError {
        ResolvedJavaMethod method = debugCallTarget.graph().method();
        if (dumpLevel == null) {
            if (ParameterizedLevelStructuredGraphDumpAllowList.stream().noneMatch(ms -> ms.matches(method))) {
                throw new VerificationError(
                                debugCallTarget, "call to %s with parameterized level not in %s.ParameterizedLevelStructuredGraphDumpAllowList.%n", verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        } else if (dumpLevel == DebugContext.BASIC_LEVEL) {
            if (BasicLevelStructuredGraphDumpAllowList.stream().noneMatch(ms -> ms.matches(method))) {
                throw new VerificationError(
                                debugCallTarget, "call to %s with level == DebugContext.BASIC_LEVEL not in %s.BasicLevelStructuredGraphDumpAllowList.%n", verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        } else if (dumpLevel == DebugContext.INFO_LEVEL) {
            if (InfoLevelStructuredGraphDumpAllowList.stream().noneMatch(ms -> ms.matches(method))) {
                throw new VerificationError(
                                debugCallTarget, "call to %s with level == Debug.INFO_LEVEL not in %s.InfoLevelStructuredGraphDumpAllowList.%n", verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        }
    }
}
