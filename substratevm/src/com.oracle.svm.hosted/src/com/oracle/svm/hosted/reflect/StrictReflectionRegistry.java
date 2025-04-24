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
package com.oracle.svm.hosted.reflect;

import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.reflect.ReflectionBytecodeAnalyzer.ReflectionAnalysisValue;
import com.oracle.svm.util.LogUtils;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import jdk.graal.compiler.java.dataflow.AbstractFrame;
import jdk.graal.compiler.java.dataflow.DataFlowAnalysisException;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import org.graalvm.collections.Pair;

import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StrictReflectionRegistry {

    private static final Object NULL_MARKER = new Object();

    private final Map<Pair<ResolvedJavaMethod, Integer>, AbstractFrame<ReflectionAnalysisValue>> registry;
    private final ReflectionBytecodeAnalyzer analyzer;
    private final Set<HashableJavaMethod> reflectionTargets;

    public StrictReflectionRegistry(Providers providers, ImageClassLoader loader) {
        this.registry = new ConcurrentHashMap<>();
        this.analyzer = new ReflectionBytecodeAnalyzer(providers, loader);
        this.reflectionTargets = Set.of(
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "forName", String.class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getField", String.class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredField", String.class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getConstructor", Class[].class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredConstructor", Class[].class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getMethod", String.class, Class[].class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredMethod", String.class, Class[].class)),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getFields")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredFields")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getConstructors")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredConstructors")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getMethods")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredMethods")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getClasses")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getDeclaredClasses")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getNestMembers")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getPermittedSubclasses")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getRecordComponents")),
                        HashableJavaMethod.make(analyzer.getMethod(Class.class, "getSigners")));
    }

    public void analyzeMethod(ResolvedJavaMethod method, IntrinsicContext intrinsicContext) {
        Bytecode bytecode = getBytecode(method, intrinsicContext);
        try {
            Map<Integer, AbstractFrame<ReflectionAnalysisValue>> abstractFrames = analyzer.analyze(bytecode);
            abstractFrames.forEach((key, value) -> registry.put(Pair.create(method, key), value));
        } catch (DataFlowAnalysisException e) {
            LogUtils.warning("Constant reflection analysis failed for " + method.format("%H.%n(%p)") + ": " + e.getMessage());
        }
    }

    private static Bytecode getBytecode(ResolvedJavaMethod method, IntrinsicContext intrinsicContext) {
        BytecodeProvider bytecodeProvider = intrinsicContext == null
                        ? ResolvedJavaMethodBytecodeProvider.INSTANCE
                        : intrinsicContext.getBytecodeProvider();
        return bytecodeProvider.getBytecode(method);
    }

    public Optional<Object> getConstantOperand(ResolvedJavaMethod callerMethod, int bci, ResolvedJavaMethod targetMethod, int index) {
        AbstractFrame<ReflectionAnalysisValue> frame = registry.get(Pair.create(callerMethod, bci));
        if (frame == null) {
            return Optional.empty();
        }
        int numOfOperands = targetMethod.getSignature().getParameterCount(targetMethod.hasReceiver());
        ReflectionAnalysisValue operand = frame.getOperand(numOfOperands - index - 1);
        if (operand instanceof ReflectionBytecodeAnalyzer.CompileTimeConstant constant) {
            Object value = constant.getValue();
            if (value == null) {
                value = NULL_MARKER;
            }
            /*
             * Since the analyzer doesn't differentiate between boolean, byte, short, char and int
             * types, we have to check what the expected type is based on the signature of the
             * target method and cast the value appropriately.
             */
            if (!targetMethod.hasReceiver() || index != 0) {
                int parameterIndex = targetMethod.hasReceiver() ? index - 1 : index;
                JavaKind parameterKind = targetMethod.getSignature().getParameterKind(parameterIndex);
                if (value instanceof Integer n) {
                    value = switch (parameterKind) {
                        case JavaKind.Boolean -> n != 0;
                        case JavaKind.Byte -> n.byteValue();
                        case JavaKind.Short -> n.shortValue();
                        case JavaKind.Char -> (char) ('0' + n);
                        default -> value;
                    };
                }
            }
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }

    public static boolean isNull(Object object) {
        return object == NULL_MARKER;
    }

    public boolean isStrictReflectionTarget(JavaMethod method) {
        return reflectionTargets.contains(HashableJavaMethod.make(method));
    }

    private record HashableJavaMethod(JavaMethod method) implements JavaMethod {

        public static HashableJavaMethod make(JavaMethod method) {
            return new HashableJavaMethod(method);
        }

        @Override
        public String getName() {
            return method.getName();
        }

        @Override
        public JavaType getDeclaringClass() {
            return method.getDeclaringClass();
        }

        @Override
        public Signature getSignature() {
            return method.getSignature();
        }

        @Override
        public String format(String format) throws IllegalFormatException {
            return method.format(format);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HashableJavaMethod that = (HashableJavaMethod) o;
            return Objects.equals(getDeclaringClass().getName(), that.getDeclaringClass().getName()) &&
                            Objects.equals(getName(), that.getName()) &&
                            Objects.equals(getSignature().toMethodDescriptor(), that.getSignature().toMethodDescriptor());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getDeclaringClass().getName(), getName(), getSignature().toMethodDescriptor());
        }
    }
}
