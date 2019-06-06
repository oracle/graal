/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common.hotspot.libgraal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan.Decision;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;

/**
 * Annotates methods associated with both ends of a SVM to HotSpot call. This annotation simplifies
 * navigating between these methods in an IDE.
 *
 * The {@code org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor.HotSpotCallProcessor}
 * processor will produce a helper method for marshaling arguments and making the JNI call.
 */
@Repeatable(SVMToHotSpotRepeated.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SVMToHotSpot {
    /**
     * Gets the token identifying a call to HotSpot from SVM.
     */
    Id value();

    /**
     * Identifier for a call to HotSpot from SVM.
     */
    // Please keep sorted
    enum Id {
        // @formatter:off
        AsCompilableTruffleAST(CompilableTruffleAST.class, HotSpotTruffleCompilerRuntime.class, long.class),
        AsJavaConstant(long.class, CompilableTruffleAST.class),
        CompilableToString(String.class, CompilableTruffleAST.class),
        ConsumeOptimizedAssumptionDependency(void.class, Consumer.class, OptimizedAssumptionDependency.class),
        CreateException(Throwable.class, String.class),
        CreateInliningPlan(TruffleInliningPlan.class, HotSpotTruffleCompilerRuntime.class, CompilableTruffleAST.class, TruffleCompilationTask.class),
        CreateStringSupplier(Supplier.class, long.class),
        FindDecision(TruffleInliningPlan.Decision.class, TruffleInliningPlan.class, long.class),
        GetCallTargetForCallNode(long.class, HotSpotTruffleCompilerRuntime.class, long.class),
        GetCompilableName(String.class, CompilableTruffleAST.class),
        GetConstantFieldInfo(int.class, HotSpotTruffleCompilerRuntime.class, long.class, boolean.class, int.class),
        GetDescription(String.class, TruffleSourceLanguagePosition.class),
        GetFailedSpeculationsAddress(long.class, CompilableTruffleAST.class),
        GetFrameSlotKindTagForJavaKind(int.class, HotSpotTruffleCompilerRuntime.class, int.class),
        GetFrameSlotKindTagsCount(int.class, HotSpotTruffleCompilerRuntime.class),
        GetInlineKind(int.class, HotSpotTruffleCompilerRuntime.class, long.class, boolean.class),
        GetJavaKindForFrameSlotKind(int.class, HotSpotTruffleCompilerRuntime.class, int.class),
        GetLanguage(String.class, TruffleSourceLanguagePosition.class),
        GetLineNumber(int.class, TruffleSourceLanguagePosition.class),
        GetLoopExplosionKind(int.class, HotSpotTruffleCompilerRuntime.class, long.class),
        GetNodeRewritingAssumption(long.class, TruffleInliningPlan.Decision.class),
        GetOffsetEnd(int.class, TruffleSourceLanguagePosition.class),
        GetOffsetStart(int.class, TruffleSourceLanguagePosition.class),
        GetPosition(TruffleSourceLanguagePosition.class, TruffleInliningPlan.class, long.class),
        GetStackTrace(StackTraceElement[].class, Throwable.class),
        GetStackTraceElementClassName(String.class, StackTraceElement.class),
        GetStackTraceElementFileName(String.class, StackTraceElement.class),
        GetStackTraceElementLineNumber(int.class, StackTraceElement.class),
        GetStackTraceElementMethodName(String.class, StackTraceElement.class),
        GetSuppliedString(String.class, Supplier.class),
        GetTargetName(String.class, Decision.class),
        GetThrowableMessage(String.class, Throwable.class),
        GetTruffleCallBoundaryMethods(long[].class, HotSpotTruffleCompilerRuntime.class),
        GetURI(String.class, TruffleSourceLanguagePosition.class),
        IsCancelled(boolean.class, TruffleCompilationTask.class),
        IsLastTier(boolean.class, TruffleCompilationTask.class),
        IsTargetStable(boolean.class, Decision.class),
        IsTruffleBoundary(boolean.class, HotSpotTruffleCompilerRuntime.class, long.class),
        IsValueType(boolean.class, HotSpotTruffleCompilerRuntime.class, long.class),
        Log(void.class, HotSpotTruffleCompilerRuntime.class, String.class),
        OnCodeInstallation(void.class, HotSpotTruffleCompilerRuntime.class, CompilableTruffleAST.class, long.class),
        OnCompilationFailed(void.class, CompilableTruffleAST.class, Supplier.class, boolean.class, boolean.class),
        OnFailure(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, String.class, boolean.class, boolean.class),
        OnGraalTierFinished(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, long.class),
        OnSuccess(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, TruffleInliningPlan.class, long.class, long.class),
        OnTruffleTierFinished(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, TruffleInliningPlan.class, long.class),
        RegisterOptimizedAssumptionDependency(Consumer.class, HotSpotTruffleCompilerRuntime.class, long.class),
        ShouldInline(boolean.class, Decision.class),
        UpdateStackTrace(Throwable.class, Throwable.class, String[].class);
        // @formatter:on

        private final String signature;
        private final String methodName;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

        public String getSignature() {
            return signature;
        }

        public String getMethodName() {
            return methodName;
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        @Override
        public String toString() {
            return methodName + signature;
        }

        Id(Class<?> returnType, Class<?>... parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            StringBuilder builder = new StringBuilder("(");
            for (Class<?> type : parameterTypes) {
                encodeType(type, builder);
            }
            builder.append(")");
            encodeType(returnType, builder);
            signature = builder.toString();
            methodName = Character.toLowerCase(name().charAt(0)) + name().substring(1);
        }

        private static void encodeType(Class<?> type, StringBuilder buf) {
            String desc;
            if (type == boolean.class) {
                desc = "Z";
            } else if (type == byte.class) {
                desc = "B";
            } else if (type == char.class) {
                desc = "C";
            } else if (type == short.class) {
                desc = "S";
            } else if (type == int.class) {
                desc = "I";
            } else if (type == long.class) {
                desc = "J";
            } else if (type == float.class) {
                desc = "F";
            } else if (type == double.class) {
                desc = "D";
            } else if (type == void.class) {
                desc = "V";
            } else if (type.isArray()) {
                buf.append('[');
                encodeType(type.getComponentType(), buf);
                return;
            } else {
                desc = "L" + type.getName().replace('.', '/') + ";";
            }
            buf.append(desc);
        }
    }
}
