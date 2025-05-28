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
import jdk.graal.compiler.java.dataflow.AbstractFrame;
import jdk.graal.compiler.java.dataflow.DataFlowAnalysisException;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConstantExpressionRegistry {

    private static final Object NULL_MARKER = new Object();

    private Map<Pair<ResolvedJavaMethod, Integer>, AbstractFrame<ConstantExpressionAnalyzer.Value>> registry;
    private final AtomicBoolean isSealed = new AtomicBoolean();

    public ConstantExpressionRegistry() {
        registry = new ConcurrentHashMap<>();
        isSealed.set(false);
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

    public Optional<Object> getReceiver(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod) {
        VMError.guarantee(!isSealed(), "Registry is already sealed");
        VMError.guarantee(targetMethod.hasReceiver(), "Receiver requested for static method");
        AbstractFrame<ConstantExpressionAnalyzer.Value> frame = registry.get(Pair.create(callerMethod, bci));
        if (frame == null) {
            return Optional.empty();
        }
        int numOfParameters = targetMethod.getSignature().getParameterCount(false);
        ConstantExpressionAnalyzer.Value receiver = frame.operandStack().getOperand(numOfParameters);
        if (receiver instanceof ConstantExpressionAnalyzer.CompileTimeConstant constant) {
            Object receiverValue = constant.getValue();
            return Optional.of(receiverValue == null ? NULL_MARKER : receiverValue);
        } else {
            return Optional.empty();
        }
    }

    public <T> T getReceiver(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, Class<T> type) {
        Optional<Object> receiver = getReceiver(callerMethod, bci, targetMethod);
        if (receiver.isEmpty()) {
            return null;
        }
        Object receiverValue = receiver.get();
        if (isNull(receiverValue) || !type.isAssignableFrom(receiverValue.getClass())) {
            return null;
        }
        return type.cast(receiver.get());
    }

    public Optional<Object> getArgument(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, int index) {
        VMError.guarantee(!isSealed(), "Registry is already sealed");
        int numOfParameters = targetMethod.getSignature().getParameterCount(false);
        VMError.guarantee(index >= 0 && index < numOfParameters, "Argument index out of bounds");
        AbstractFrame<ConstantExpressionAnalyzer.Value> frame = registry.get(Pair.create(callerMethod, bci));
        if (frame == null) {
            return Optional.empty();
        }
        ConstantExpressionAnalyzer.Value argument = frame.operandStack().getOperand(numOfParameters - index - 1);
        if (argument instanceof ConstantExpressionAnalyzer.CompileTimeConstant constant) {
            Object argumentValue = constant.getValue();
            if (argumentValue == null) {
                return Optional.of(NULL_MARKER);
            } else if (argumentValue instanceof Integer n) {
                /*
                 * Since the analyzer doesn't differentiate between boolean, byte, short, char and
                 * int types, we have to check what the expected type is based on the signature of
                 * the target method and cast the value appropriately.
                 */
                JavaKind parameterKind = targetMethod.getSignature().getParameterKind(index);
                Object properlyTypedValue = switch (parameterKind) {
                    case JavaKind.Boolean -> n != 0;
                    case JavaKind.Byte -> n.byteValue();
                    case JavaKind.Short -> n.shortValue();
                    case JavaKind.Char -> (char) ('0' + n);
                    default -> argumentValue;
                };
                return Optional.of(properlyTypedValue);
            } else {
                return Optional.of(argumentValue);
            }
        } else {
            return Optional.empty();
        }
    }

    public <T> T getArgument(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, int index, Class<T> type) {
        Optional<Object> argument = getArgument(callerMethod, bci, targetMethod, index);
        if (argument.isEmpty()) {
            return null;
        }
        Object argumentValue = argument.get();
        if (isNull(argumentValue) || !type.isAssignableFrom(argumentValue.getClass())) {
            return null;
        }
        return type.cast(argumentValue);
    }

    public boolean isSealed() {
        return isSealed.get();
    }

    public void seal() {
        isSealed.set(true);
        registry = null;
    }

    public static boolean isNull(Object object) {
        return object == NULL_MARKER;
    }
}
