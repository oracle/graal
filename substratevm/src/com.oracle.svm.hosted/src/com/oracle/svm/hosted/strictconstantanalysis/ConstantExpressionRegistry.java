/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.strictconstantanalysis;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import com.oracle.svm.hosted.dataflow.AbstractFrame;
import com.oracle.svm.hosted.dataflow.DataFlowAnalysisException;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConstantExpressionRegistry {

    public static ConstantExpressionRegistry singleton() {
        return ImageSingletons.lookup(ConstantExpressionRegistry.class);
    }

    /**
     * Representation of inferred {@code null} values in the registry.
     */
    private static final Object NULL_MARKER = new Object();

    private Map<Pair<ResolvedJavaMethod, Integer>, AbstractFrame<ConstantExpressionAnalyzer.Value>> registry;
    private boolean isSealed;

    public ConstantExpressionRegistry() {
        registry = new ConcurrentHashMap<>();
        isSealed = false;
    }

    public void analyzeAndStore(ConstantExpressionAnalyzer analyzer, ResolvedJavaMethod method, IntrinsicContext intrinsicContext) {
        VMError.guarantee(!isSealed(), "Registry is already sealed");
        Bytecode bytecode = getBytecode(method, intrinsicContext);
        try {
            Map<Integer, AbstractFrame<ConstantExpressionAnalyzer.Value>> abstractFrames = analyzer.analyze(bytecode);
            abstractFrames.forEach((key, value) -> registry.put(Pair.create(method, key), value));
        } catch (DataFlowAnalysisException e) {
            LogUtils.warning("Constant expression analysis failed for " + method.format("%H.%n(%p)") + ": " + e.getMessage());
        }
    }

    private static Bytecode getBytecode(ResolvedJavaMethod method, IntrinsicContext intrinsicContext) {
        BytecodeProvider bytecodeProvider = intrinsicContext == null
                        ? ResolvedJavaMethodBytecodeProvider.INSTANCE
                        : intrinsicContext.getBytecodeProvider();
        return bytecodeProvider.getBytecode(method);
    }

    /**
     * Attempt to get the inferred receiver of a {@code targetMethod} invocation at the specified
     * code location.
     *
     * @param callerMethod The method in which {@code targetMethod} is invoked
     * @param bci The BCI of the invocation instruction with respect to {@code callerMethod}
     * @param targetMethod The invoked method
     * @return The Java value of the receiver if it can be inferred and null otherwise. A
     *         {@code null} value is represented by {@link ConstantExpressionRegistry#NULL_MARKER}.
     */
    public Object getReceiver(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod) {
        VMError.guarantee(!isSealed(), "Registry is already sealed");
        VMError.guarantee(targetMethod.hasReceiver(), "Receiver requested for static method");
        AbstractFrame<ConstantExpressionAnalyzer.Value> frame = registry.get(Pair.create(callerMethod, bci));
        if (frame == null) {
            return null;
        }
        int numOfParameters = targetMethod.getSignature().getParameterCount(false);
        ConstantExpressionAnalyzer.Value receiver = frame.operandStack().getOperand(numOfParameters);
        if (receiver instanceof ConstantExpressionAnalyzer.CompileTimeConstant constant) {
            Object receiverValue = constant.getValue();
            return receiverValue == null ? NULL_MARKER : receiverValue;
        } else {
            return null;
        }
    }

    /**
     * Utility method which calls into
     * {@link ConstantExpressionRegistry#getReceiver(ResolvedJavaMethod, int, ResolvedJavaMethod)},
     * but attempts to cast the inferred value into {@code type}.
     * <p>
     * If the inferred value is {@code null}, i.e., {@link ConstantExpressionRegistry#NULL_MARKER},
     * {@code null} is returned, which is the same return value as if the receiver could not be
     * inferred.
     */
    public <T> T getReceiver(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, Class<T> type) {
        Object receiver = getReceiver(callerMethod, bci, targetMethod);
        return tryToCast(receiver, type);
    }

    /**
     * Attempt to get an inferred argument of a {@code targetMethod} invocation at the specified
     * code location.
     *
     * @param callerMethod The method in which {@code targetMethod} is invoked
     * @param bci The BCI of the invocation instruction with respect to {@code callerMethod}
     * @param targetMethod The invoked method
     * @param index The argument index
     * @return The Java value of the argument if it can be inferred and null otherwise. A
     *         {@code null} value is represented by {@link ConstantExpressionRegistry#NULL_MARKER}.
     */
    public Object getArgument(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, int index) {
        VMError.guarantee(!isSealed(), "Registry is already sealed");
        int numOfParameters = targetMethod.getSignature().getParameterCount(false);
        VMError.guarantee(0 <= index && index < numOfParameters, "Argument index out of bounds");
        AbstractFrame<ConstantExpressionAnalyzer.Value> frame = registry.get(Pair.create(callerMethod, bci));
        if (frame == null) {
            return null;
        }
        ConstantExpressionAnalyzer.Value argument = frame.operandStack().getOperand(numOfParameters - index - 1);
        if (argument instanceof ConstantExpressionAnalyzer.CompileTimeConstant constant) {
            Object argumentValue = constant.getValue();
            if (argumentValue == null) {
                return NULL_MARKER;
            } else if (argumentValue instanceof Integer n) {
                /*
                 * Since the analyzer doesn't differentiate between boolean, byte, short, char and
                 * int types, we have to check what the expected type is based on the signature of
                 * the target method and cast the value appropriately.
                 */
                JavaKind parameterKind = targetMethod.getSignature().getParameterKind(index);
                return switch (parameterKind) {
                    case JavaKind.Boolean -> n != 0;
                    case JavaKind.Byte -> n.byteValue();
                    case JavaKind.Short -> n.shortValue();
                    case JavaKind.Char -> (char) ('0' + n);
                    default -> argumentValue;
                };
            } else {
                return argumentValue;
            }
        } else {
            return null;
        }
    }

    /**
     * Utility method which calls into
     * {@link ConstantExpressionRegistry#getArgument(ResolvedJavaMethod, int, ResolvedJavaMethod, int)},
     * but attempts to cast the inferred value into {@code type}.
     * <p>
     * If the inferred value is {@code null}, i.e., {@link ConstantExpressionRegistry#NULL_MARKER},
     * {@code null} is returned, which is the same return value as if the argument could not be
     * inferred.
     */
    public <T> T getArgument(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, int index, Class<T> type) {
        Object argument = getArgument(callerMethod, bci, targetMethod, index);
        return tryToCast(argument, type);
    }

    private static <T> T tryToCast(Object value, Class<T> type) {
        if (value == null || isNull(value) || !type.isAssignableFrom(value.getClass())) {
            return null;
        }
        return type.cast(value);
    }

    public boolean isSealed() {
        return isSealed;
    }

    public void seal() {
        isSealed = true;
        registry = null;
    }

    /**
     * Check if {@code value} represents a {@code null} Java value.
     */
    public static boolean isNull(Object value) {
        return value == NULL_MARKER;
    }
}
