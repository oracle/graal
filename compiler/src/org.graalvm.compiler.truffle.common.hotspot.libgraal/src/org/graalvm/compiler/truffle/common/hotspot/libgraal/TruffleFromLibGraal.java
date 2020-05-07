/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan.Decision;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.libgraal.jni.annotation.FromLibGraalId;

/**
 * Annotates methods associated with both ends of a libgraal to HotSpot call. This annotation
 * simplifies navigating between these methods in an IDE.
 *
 * The
 * {@code org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor.TruffleFromLibGraalProcessor}
 * processor will produce a helper method for marshaling arguments and making the JNI call.
 */
@Repeatable(TruffleFromLibGraalRepeated.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TruffleFromLibGraal {
    /**
     * Gets the token identifying a call to HotSpot from libgraal.
     */
    Id value();

    /**
     * Identifier for a call to HotSpot from libgraal.
     */
    // Please keep sorted
    enum Id implements FromLibGraalId {
        // @formatter:off
        AsCompilableTruffleAST(CompilableTruffleAST.class, HotSpotTruffleCompilerRuntime.class, long.class),
        AsJavaConstant(long.class, CompilableTruffleAST.class),
        CallNodeHashCode(int.class, TruffleCallNode.class),
        CancelInstalledTask(void.class, CompilableTruffleAST.class),
        CompilableToString(String.class, CompilableTruffleAST.class),
        ConsumeOptimizedAssumptionDependency(void.class, Consumer.class, OptimizedAssumptionDependency.class),
        CreateInliningPlan(TruffleInliningPlan.class, HotSpotTruffleCompilerRuntime.class, CompilableTruffleAST.class, TruffleCompilationTask.class),
        CreateStringSupplier(Supplier.class, long.class),
        FindCallNode(TruffleCallNode.class, TruffleMetaAccessProvider.class, long.class),
        FindDecision(TruffleInliningPlan.Decision.class, TruffleInliningPlan.class, long.class),
        GetCallCount(int.class, TruffleCallNode.class),
        GetCallNodes(TruffleCallNode[].class, CompilableTruffleAST.class),
        GetCallTargetForCallNode(long.class, HotSpotTruffleCompilerRuntime.class, long.class),
        GetCompilableCallCount(int.class, CompilableTruffleAST.class),
        GetCompilableName(String.class, CompilableTruffleAST.class),
        GetConstantFieldInfo(int.class, HotSpotTruffleCompilerRuntime.class, long.class, boolean.class, int.class),
        GetCurrentCallTarget(CompilableTruffleAST.class, TruffleCallNode.class),
        GetDescription(String.class, TruffleSourceLanguagePosition.class),
        GetFailedSpeculationsAddress(long.class, CompilableTruffleAST.class),
        GetFrameSlotKindTagForJavaKind(int.class, HotSpotTruffleCompilerRuntime.class, int.class),
        GetFrameSlotKindTagsCount(int.class, HotSpotTruffleCompilerRuntime.class),
        GetInlineKind(int.class, HotSpotTruffleCompilerRuntime.class, long.class, boolean.class),
        GetJavaKindForFrameSlotKind(int.class, HotSpotTruffleCompilerRuntime.class, int.class),
        GetKnownCallSiteCount(int.class, CompilableTruffleAST.class),
        GetLanguage(String.class, TruffleSourceLanguagePosition.class),
        GetLineNumber(int.class, TruffleSourceLanguagePosition.class),
        GetLoopExplosionKind(int.class, HotSpotTruffleCompilerRuntime.class, long.class),
        GetNodeRewritingAssumption(long.class, TruffleInliningPlan.Decision.class),
        GetNodeRewritingAssumptionConstant(long.class, CompilableTruffleAST.class),
        GetNonTrivialNodeCount(int.class, CompilableTruffleAST.class),
        GetOffsetEnd(int.class, TruffleSourceLanguagePosition.class),
        GetOffsetStart(int.class, TruffleSourceLanguagePosition.class),
        GetPosition(TruffleSourceLanguagePosition.class, TruffleInliningPlan.class, long.class),
        GetSuppliedString(String.class, Supplier.class),
        GetTargetName(String.class, Decision.class),
        GetTruffleCallBoundaryMethods(long[].class, HotSpotTruffleCompilerRuntime.class),
        GetURI(String.class, TruffleSourceLanguagePosition.class),
        IsCancelled(boolean.class, TruffleCompilationTask.class),
        IsInliningForced(boolean.class, TruffleCallNode.class),
        IsLastTier(boolean.class, TruffleCompilationTask.class),
        IsSameOrSplit(boolean.class, CompilableTruffleAST.class, CompilableTruffleAST.class),
        IsTargetStable(boolean.class, Decision.class),
        IsTruffleBoundary(boolean.class, HotSpotTruffleCompilerRuntime.class, long.class),
        IsValueType(boolean.class, HotSpotTruffleCompilerRuntime.class, long.class),
        Log(void.class, HotSpotTruffleCompilerRuntime.class, CompilableTruffleAST.class, String.class),
        OnCodeInstallation(void.class, HotSpotTruffleCompilerRuntime.class, CompilableTruffleAST.class, long.class),
        OnCompilationFailed(void.class, CompilableTruffleAST.class, Supplier.class, boolean.class, boolean.class),
        OnFailure(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, String.class, boolean.class, boolean.class),
        OnGraalTierFinished(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, long.class),
        OnSuccess(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, TruffleInliningPlan.class, long.class, long.class),
        OnTruffleTierFinished(void.class, TruffleCompilerListener.class, CompilableTruffleAST.class, TruffleInliningPlan.class, long.class),
        RegisterOptimizedAssumptionDependency(Consumer.class, HotSpotTruffleCompilerRuntime.class, long.class),
        ShouldInline(boolean.class, Decision.class);
        // @formatter:on

        private final String signature;
        private final String methodName;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

        @Override
        public String getName() {
            return name();
        }

        @Override
        public String getSignature() {
            return signature;
        }

        @Override
        public String getMethodName() {
            return methodName;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        @Override
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
            signature = FromLibGraalId.encodeMethodSignature(returnType, parameterTypes);
            methodName = Character.toLowerCase(name().charAt(0)) + name().substring(1);
        }
    }
}
