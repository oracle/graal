/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Verifies that call sites do not eagerly evaluate their arguments by using string concatenation or
 * {@link String#format}, and additionally verifies that no argument is the result of a call to
 * {@link StringBuilder#toString} or {@link StringBuffer#toString}.
 * <p>
 * Useful for calls that may be eliminated, such as to loggers or guarantees (always-on assertions)
 * and to methods with string-formatting capabilities, with which a string built in the caller can
 * be ill-suited as format string and lead to an {@link IllegalFormatException}.
 */
public abstract class VerifyStringFormatterUsage extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    protected void verifyParameters(MetaAccessProvider metaAccess, MethodCallTargetNode callTarget, NodeInputList<? extends ValueNode> args, ResolvedJavaType stringType,
                    int startArgIdx) {
        if (callTarget.targetMethod().isVarArgs() && args.get(args.count() - 1) instanceof NewArrayNode) {
            // unpack the arguments to the var args
            List<ValueNode> unpacked = new ArrayList<>(args.snapshot());
            NewArrayNode varArgParameter = (NewArrayNode) unpacked.remove(unpacked.size() - 1);
            int firstVarArg = unpacked.size();
            for (Node usage : varArgParameter.usages()) {
                if (usage instanceof StoreIndexedNode) {
                    StoreIndexedNode si = (StoreIndexedNode) usage;
                    unpacked.add(si.value());
                }
            }
            verifyParameters(metaAccess, callTarget, unpacked, stringType, startArgIdx, firstVarArg);
        } else {
            verifyParameters(metaAccess, callTarget, args, stringType, startArgIdx, -1);
        }
    }

    protected void verifyParameters(MetaAccessProvider metaAccess, MethodCallTargetNode debugCallTarget, List<? extends ValueNode> args, ResolvedJavaType stringType,
                    int startArgIdx, int varArgsIndex) {
        ResolvedJavaMethod verifiedCallee = debugCallTarget.targetMethod();
        int argIdx = startArgIdx;
        int varArgsElementIndex = 0;
        for (int i = 0; i < args.size(); i++) {
            ValueNode arg = args.get(i);
            // Ignore if the argument value is used elsewhere too
            if (arg instanceof Invoke && hasSingleUsage(arg)) {
                boolean reportVarArgs = varArgsIndex >= 0 && argIdx >= varArgsIndex;
                Invoke invoke = (Invoke) arg;
                CallTargetNode callTarget = invoke.callTarget();
                if (callTarget instanceof MethodCallTargetNode) {
                    ResolvedJavaMethod m = callTarget.targetMethod();
                    if (m.getName().equals("toString")) {
                        int nonVarArgIdx = reportVarArgs ? argIdx - varArgsElementIndex : argIdx;
                        verifyStringConcat(verifiedCallee, invoke, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1, m);
                        verifyToStringCall(verifiedCallee, stringType, m, invoke, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1);
                    } else if (m.getName().equals("format")) {
                        int nonVarArgIdx = reportVarArgs ? argIdx - varArgsElementIndex : argIdx;
                        verifyFormatCall(verifiedCallee, stringType, m, invoke, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1);

                    } else if (m.getName().equals("linkToTargetMethod") &&
                                    (m.getDeclaringClass().getName().equals("Ljava/lang/invoke/Invokers$Holder;") || m.getDeclaringClass().getName().startsWith("Ljava/lang/invoke/LambdaForm$MH"))) {
                        // This is the shape of an indy'fied string concatenation (JDK-8085796)
                        throw new VerificationError(
                                        debugCallTarget, "parameter %d of call to %s appears to be an indy'fied String concatenation expression.", argIdx, verifiedCallee.format("%H.%n(%p)"));

                    }
                }
            }
            if (varArgsIndex >= 0 && i >= varArgsIndex) {
                varArgsElementIndex++;
            }
            argIdx++;
        }

        /**
         * Format string assumptions: if any of the methods making it to this check contains a
         * string constant with format string specifiers we run some printF checking. The assumption
         * is that any following arguments are the arguments to the printF call.
         */
        verifyLogPrintfFormats(debugCallTarget, metaAccess, startArgIdx);
    }

    protected SnippetReflectionProvider getSnippetReflection() {
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    /**
     * Verify that calls of any form to {@code DebugContext.log(String format, ...)} use correct
     * format specifiers with respect to the argument types.
     */
    private void verifyLogPrintfFormats(final MethodCallTargetNode t, final MetaAccessProvider metaAccess, final int startArgIndex) {
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
        int parameterCount = s.getParameterCount(!callee.isStatic());
        ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);

        /*
         * The first log parameter can have the static type int for the log level, ignore this
         */
        int argIndex = startArgIndex;

        ValueNode formatString = null;
        while (argIndex < parameterCount) {
            ResolvedJavaType argType = typeFromArgument(metaAccess, argIndex, t);
            if (argType.equals(stringType)) {
                formatString = t.arguments().get(argIndex);
                break;
            }
            argIndex++;
        }
        if (argIndex + 1 >= parameterCount) {
            return;
        }
        if (formatString == null) {
            // no format string constant found - can happen if the API is changed to have methods
            // without string parameters in DebugContext
            return;
        }
        if (!formatString.isConstant()) {
            // we can only do something useful if the argument string is a constant
            return;
        }
        final String formatStringVal = getSnippetReflection().asObject(String.class, formatString.asJavaConstant());
        if (formatStringVal == null) {
            throw new VerificationError(t, "Printf call %s violates printf format printing: format string constant could not be read from the VM.", t.targetMethod().format("%H.%n(%p)"));
        }
        if (!formatStringVal.contains("%")) {
            return;
        }
        verifyCorrectFormatString(formatStringVal, t);

        ArrayList<ValueNode> printfArgs = new ArrayList<>();
        for (int i = argIndex + 1; i < parameterCount; i++) {
            if (t.targetMethod().isVarArgs() && i == parameterCount - 1) {
                // vararg parameter
                NewArrayNode varArgParameter = (NewArrayNode) t.arguments().get(i);
                for (Node usage : varArgParameter.usages()) {
                    if (usage instanceof StoreIndexedNode) {
                        StoreIndexedNode si = (StoreIndexedNode) usage;
                        printfArgs.add(si.value());
                    }
                }
            } else {
                // regular parameter
                printfArgs.add(t.arguments().get(i));
            }
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
                 * everything else can only map to {@code %s} since its an object that can be used
                 * as string. So for boxing we use boxed default values for the rest we use null.
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
        verifyPrintFormatCall(formatStringVal, argsBoxed, argTypes, t);
    }

    private static void verifyCorrectFormatString(String formatString, MethodCallTargetNode callee) {
        if (formatString.contains("\n")) {
            throw new VerificationError(callee, "Printf call %s violates printf format specifiers, do not use \\n, use %%n instead", callee.targetMethod().format("%H.%n(%p)"));
        }
    }

    protected void verifyPrintFormatCall(String formatString, ArrayList<Object> argsBoxed, ArrayList<ResolvedJavaType> argTypes, MethodCallTargetNode callee) {
        try {
            String.format(formatString, argsBoxed.toArray());
        } catch (Throwable th) {
            throw new VerificationError(callee, "Printf call %s violates printf format specifiers, argument types %s, cause (%s) = %s ", callee.targetMethod().format("%H.%n(%p)"),
                            Arrays.toString(argTypes.toArray()), th.getClass(), th.getMessage());
        }
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
                throw GraalError.shouldNotReachHereUnexpectedValue(stackKind); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static ResolvedJavaType typeFromArgument(MetaAccessProvider metaAccess, int index, MethodCallTargetNode t) {
        return t.arguments().get(index).stamp(NodeView.DEFAULT).javaType(metaAccess);
    }

    private static boolean hasSingleUsage(Node arg) {
        boolean found = false;
        for (Node usage : arg.usages()) {
            if (!(usage instanceof FrameState)) {
                if (found) {
                    return false;
                }
                found = true;
            }
        }
        return found;
    }

    /**
     * Checks that a given call is not to {@link StringBuffer#toString()} or
     * {@link StringBuilder#toString()}.
     */
    protected static void verifyStringConcat(ResolvedJavaMethod verifiedCallee, Invoke invoke, int argIdx, int varArgsElementIndex, ResolvedJavaMethod callee) {
        if (callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuilder;") || callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuffer;")) {
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                invoke, "element %d of parameter %d of call to %s appears to be a String concatenation expression.%n", varArgsElementIndex, argIdx,
                                verifiedCallee.format("%H.%n(%p)"));
            } else {
                throw new VerificationError(
                                invoke, "parameter %d of call to %s appears to be a String concatenation expression.", argIdx, verifiedCallee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Checks that a given call is not to {@link Object#toString()}.
     */
    protected static void verifyToStringCall(ResolvedJavaMethod verifiedCallee, ResolvedJavaType stringType, ResolvedJavaMethod callee, Invoke invoke, int argIdx,
                    int varArgsElementIndex) {
        if (callee.getSignature().getParameterCount(false) == 0 && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(invoke,
                                "element %d of parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.",
                                varArgsElementIndex, argIdx, verifiedCallee.format("%H.%n(%p)"));
            } else {
                throw new VerificationError(invoke,
                                "parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.", argIdx,
                                verifiedCallee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Checks that a given call is not to {@link String#format(String, Object...)} or
     * {@link String#format(java.util.Locale, String, Object...)}.
     */
    protected static void verifyFormatCall(ResolvedJavaMethod verifiedCallee, ResolvedJavaType stringType, ResolvedJavaMethod callee, Invoke invoke, int argIdx,
                    int varArgsElementIndex) {
        if (callee.getDeclaringClass().equals(stringType) && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(invoke,
                                "element %d of parameter %d of call to %s is a call to String.format() which is redundant (%s does formatting) and forces unnecessary eager evaluation.",
                                varArgsElementIndex, argIdx, verifiedCallee.format("%H.%n(%p)"), verifiedCallee.format("%h.%n"));
            } else {
                throw new VerificationError(invoke,
                                "parameter %d of call to %s is a call to String.format() which is redundant (%s does formatting) and forces unnecessary eager evaluation.",
                                argIdx,
                                verifiedCallee.format("%H.%n(%p)"), verifiedCallee.format("%h.%n"));
            }
        }
    }
}
