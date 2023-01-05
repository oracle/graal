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
package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.debug.DebugContext.BASIC_LEVEL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.OptimizationLogImpl;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

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

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            if (callee.getDeclaringClass().equals(debugType)) {
                boolean isDump = calleeName.equals("dump");
                if (calleeName.equals("log") || calleeName.equals("logAndIndent")) {
                    verifyLogPrintfFormats(t);
                }
                if (calleeName.equals("log") || calleeName.equals("logAndIndent") || calleeName.equals("verify") || isDump) {
                    verifyParameters(t, graph, t.arguments(), stringType, isDump ? 2 : 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(nodeType)) {
                if (calleeName.equals("assertTrue") || calleeName.equals("assertFalse")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(graalErrorType) && !graph.method().getDeclaringClass().isAssignableFrom(graalErrorType)) {
                if (calleeName.equals("guarantee")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 0);
                }
                if (calleeName.equals("<init>") && callee.getSignature().getParameterCount(false) == 2) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
        }
    }

    protected SnippetReflectionProvider getSnippetReflection() {
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    /**
     * Verify that calls of any form to {@code DebugContext.log(String format, ...)} use correct
     * format specifiers with respect to the argument types.
     */
    private void verifyLogPrintfFormats(MethodCallTargetNode t) {
        EconomicMap<ResolvedJavaType, JavaKind> boxingTypes = EconomicMap.create();
        boxingTypes.put(metaAccess.lookupJavaType(Integer.class), JavaKind.Int);
        boxingTypes.put(metaAccess.lookupJavaType(Byte.class), JavaKind.Byte);
        boxingTypes.put(metaAccess.lookupJavaType(Character.class), JavaKind.Char);
        boxingTypes.put(metaAccess.lookupJavaType(Boolean.class), JavaKind.Boolean);
        boxingTypes.put(metaAccess.lookupJavaType(Double.class), JavaKind.Double);
        boxingTypes.put(metaAccess.lookupJavaType(Float.class), JavaKind.Float);
        boxingTypes.put(metaAccess.lookupJavaType(Long.class), JavaKind.Long);
        boxingTypes.put(metaAccess.lookupJavaType(Short.class), JavaKind.Short);
        ResolvedJavaMethod callee = t.targetMethod();
        Signature s = callee.getSignature();
        int parameterCount = s.getParameterCount(true);
        ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);
        ResolvedJavaType intType = metaAccess.lookupJavaType(int.class);

        /*
         * The first log parameter can have the static type int for the log level, ignore this
         */
        int argIndex = 1;
        ResolvedJavaType firstArgumentType = typeFromArgument(argIndex, t);
        if (firstArgumentType.equals(intType)) {
            argIndex++;
            firstArgumentType = typeFromArgument(argIndex, t);
        }
        ValueNode formatString = t.arguments().get(argIndex);
        if (!firstArgumentType.equals(stringType)) {
            return;
        }
        // we can only do something useful if the argument string is a constant
        if (formatString.isConstant()) {
            ArrayList<ValueNode> printfArgs = new ArrayList<>();
            for (int i = argIndex + 1; i < parameterCount; i++) {
                printfArgs.add(t.arguments().get(i));
            }
            ArrayList<Object> argsBoxed = new ArrayList<>();
            ArrayList<ResolvedJavaType> argTypes = new ArrayList<>();
            for (ValueNode arg : printfArgs) {
                Stamp argStamp = arg.stamp(NodeView.DEFAULT);
                ResolvedJavaType argType = argStamp.javaType(metaAccess);
                argTypes.add(argType);
                if (argStamp.isPointerStamp()) {
                    /**
                     * For object stamps the only value usages are unboxed/boxed primitive classes,
                     * everything else can only map to {@code %s} since its an object that can be
                     * used as string. So for boxing we use boxed default values for the rest we use
                     * null.
                     */
                    if (boxingTypes.containsKey(argType)) {
                        argsBoxed.add(getBoxedDefaultForKind(boxingTypes.get(argType)));
                    } else {
                        argsBoxed.add(null);
                    }
                } else {
                    /*
                     * For primitive values we can just easily test with a default value.
                     */
                    GraalError.guarantee(argStamp instanceof PrimitiveStamp, "Must be primitive");
                    JavaKind stackKind = argStamp.getStackKind();
                    argsBoxed.add(getBoxedDefaultForKind(stackKind));
                }
            }
            final String formatStringVal = getSnippetReflection().asObject(String.class, formatString.asJavaConstant());
            try {
                String.format(formatStringVal, argsBoxed.toArray());
            } catch (Throwable th) {
                throw new VerificationError(String.format("Printf call %s (%s) in %s violates printf format specifiers, argument types %s", t.targetMethod().format("%H.%n(%p)"),
                                approximateLineNumber(t), t.graph().method().format("%H.%n(%p)"), Arrays.toString(argTypes.toArray())), th);
            }
        }
    }

    private static String approximateLineNumber(CallTargetNode c) {
        FrameState stateBefore = GraphUtil.findLastFrameState((FixedNode) c.invoke().predecessor());
        String stateAfterLineNumber = c.invoke().stateAfter().getCode().asStackTraceElement(c.invoke().stateAfter().bci).toString();
        String stateBeforeLineNumber = stateBefore.getCode().asStackTraceElement(stateBefore.bci).toString();
        return "Between " + stateBeforeLineNumber + " and " + stateAfterLineNumber;
    }

    private static Object getBoxedDefaultForKind(JavaKind stackKind) {
        switch (stackKind) {
            case Boolean:
                return false;
            case Byte:
                return (byte) 0;
            case Char:
                return (char) 0;
            case Short:
                return (short) 0;
            case Int:
                return 0;
            case Double:
                return 0D;
            case Float:
                return 0F;
            case Long:
                return 0L;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private ResolvedJavaType typeFromArgument(int index, MethodCallTargetNode t) {
        return t.arguments().get(index).stamp(NodeView.DEFAULT).javaType(metaAccess);
    }

    private static final Set<Integer> DebugLevels = new HashSet<>(
                    Arrays.asList(DebugContext.ENABLED_LEVEL, BASIC_LEVEL, DebugContext.INFO_LEVEL, DebugContext.VERBOSE_LEVEL, DebugContext.DETAILED_LEVEL, DebugContext.VERY_DETAILED_LEVEL));

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with the {@code level}
     * parameter bound to {@link DebugContext#BASIC_LEVEL} and the {@code object} parameter bound to
     * a {@link StructuredGraph} value.
     *
     * This allow list exists to ensure any increase in graph dumps is in line with the policy
     * outlined by {@link DebugContext#BASIC_LEVEL}. If you add a *justified* graph dump at this
     * level, then update the allow list.
     */
    private static final Set<String> BasicLevelStructuredGraphDumpAllowList = new HashSet<>(Arrays.asList(
                    "org.graalvm.compiler.phases.BasePhase.dumpAfter",
                    "org.graalvm.compiler.phases.BasePhase.dumpBefore",
                    "org.graalvm.compiler.core.GraalCompiler.emitFrontEnd",
                    "org.graalvm.compiler.truffle.compiler.PerformanceInformationHandler.reportPerformanceWarnings",
                    "org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl.compilePEGraph",
                    "org.graalvm.compiler.core.test.VerifyDebugUsageTest$ValidDumpUsagePhase.run",
                    "org.graalvm.compiler.core.test.VerifyDebugUsageTest$InvalidConcatDumpUsagePhase.run",
                    "org.graalvm.compiler.core.test.VerifyDebugUsageTest$InvalidDumpUsagePhase.run",
                    "org.graalvm.compiler.hotspot.SymbolicSnippetEncoder.verifySnippetEncodeDecode",
                    "org.graalvm.compiler.truffle.compiler.phases.inlining.CallTree.dumpBasic"));

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with the {@code level}
     * parameter bound to {@link DebugContext#INFO_LEVEL} and the {@code object} parameter bound to
     * a {@link StructuredGraph} value.
     *
     * This allow list exists to ensure any increase in graph dumps is in line with the policy
     * outlined by {@link DebugContext#INFO_LEVEL}. If you add a *justified* graph dump at this
     * level, then update the allow list.
     */
    private static final Set<String> InfoLevelStructuredGraphDumpAllowList = new HashSet<>(Arrays.asList(
                    "org.graalvm.compiler.core.GraalCompiler.emitFrontEnd",
                    "org.graalvm.compiler.phases.BasePhase.dumpAfter",
                    "org.graalvm.compiler.replacements.ReplacementsImpl$GraphMaker.makeGraph",
                    "org.graalvm.compiler.replacements.SnippetTemplate.instantiate",
                    "org.graalvm.compiler.replacements.SnippetTemplate.<init>",
                    "org.graalvm.compiler.hotspot.SymbolicSnippetEncoder.verifySnippetEncodeDecode",
                    "org.graalvm.compiler.truffle.compiler.phases.inlining.CallTree.dumpInfo"));

    @Override
    protected void verifyParameters(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, List<? extends ValueNode> args, ResolvedJavaType stringType, int startArgIdx, int varArgsIndex) {
        super.verifyParameters(callerGraph, debugCallTarget, args, stringType, startArgIdx, varArgsIndex);

        ResolvedJavaMethod verifiedCallee = debugCallTarget.targetMethod();
        if (verifiedCallee.getName().equals("dump")) {
            /*
             * The optimization log dumps at a parametrized level, but it must be at least
             * OptimizationLog.MINIMUM_LOG_LEVEL.
             */
            String optimizationEntryClassName = OptimizationLogImpl.OptimizationEntryImpl.class.getName();
            String callerClassName = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci()).getClassName();
            if (!optimizationEntryClassName.equals(callerClassName)) {
                int dumpLevel = verifyDumpLevelParameter(callerGraph, debugCallTarget, verifiedCallee, args.get(1));
                verifyDumpObjectParameter(callerGraph, debugCallTarget, args.get(2), verifiedCallee, dumpLevel);
            }
        }
    }

    /**
     * The {@code level} arg for the {@code Debug.dump(...)} methods must be a reference to one of
     * the {@code Debug.*_LEVEL} constants.
     */
    protected int verifyDumpLevelParameter(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, ResolvedJavaMethod verifiedCallee, ValueNode arg)
                    throws org.graalvm.compiler.phases.VerifyPhase.VerificationError {
        // The 'level' arg for the Debug.dump(...) methods must be a reference to one of
        // the Debug.*_LEVEL constants.

        Constant c = arg.asConstant();
        if (c != null) {
            int dumpLevel = ((PrimitiveConstant) c).asInt();
            if (!DebugLevels.contains(dumpLevel)) {
                StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
                throw new VerificationError(
                                "In %s: parameter 0 of call to %s does not match a Debug.*_LEVEL constant: %s.%n", e, verifiedCallee.format("%H.%n(%p)"), dumpLevel);
            }
            return dumpLevel;
        }
        StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
        throw new VerificationError(
                        "In %s: parameter 0 of call to %s must be a constant, not %s.%n", e, verifiedCallee.format("%H.%n(%p)"), arg);
    }

    protected void verifyDumpObjectParameter(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, ValueNode arg, ResolvedJavaMethod verifiedCallee, Integer dumpLevel)
                    throws org.graalvm.compiler.phases.VerifyPhase.VerificationError {
        ResolvedJavaType argType = ((ObjectStamp) arg.stamp(NodeView.DEFAULT)).type();
        if (metaAccess.lookupJavaType(Graph.class).isAssignableFrom(argType)) {
            verifyStructuredGraphDumping(callerGraph, debugCallTarget, verifiedCallee, dumpLevel);
        }
    }

    /**
     * Verifies that dumping a {@link StructuredGraph} at level {@link DebugContext#BASIC_LEVEL} or
     * {@link DebugContext#INFO_LEVEL} only occurs in white-listed methods.
     */
    protected void verifyStructuredGraphDumping(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, ResolvedJavaMethod verifiedCallee, Integer dumpLevel)
                    throws org.graalvm.compiler.phases.VerifyPhase.VerificationError {
        if (dumpLevel == DebugContext.BASIC_LEVEL) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
            String qualifiedMethod = e.getClassName() + "." + e.getMethodName();
            if (!BasicLevelStructuredGraphDumpAllowList.contains(qualifiedMethod)) {
                throw new VerificationError(
                                "In %s: call to %s with level == DebugContext.BASIC_LEVEL not in %s.BasicLevelDumpAllowList.%n", e, verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        } else if (dumpLevel == DebugContext.INFO_LEVEL) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
            String qualifiedMethod = e.getClassName() + "." + e.getMethodName();
            if (!InfoLevelStructuredGraphDumpAllowList.contains(qualifiedMethod)) {
                throw new VerificationError(
                                "In %s: call to %s with level == Debug.INFO_LEVEL not in %s.InfoLevelDumpAllowList.%n", e, verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        }
    }
}
