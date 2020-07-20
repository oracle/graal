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
        AddTargetToDequeue(void.class, Object.class, Object.class),
        AsCompilableTruffleAST(Object.class, Object.class, long.class),
        AsJavaConstant(long.class, Object.class),
        CallNodeHashCode(int.class, Object.class),
        CancelCompilation(boolean.class, Object.class, String.class),
        CompilableToString(String.class, Object.class),
        ConsumeOptimizedAssumptionDependency(void.class, Consumer.class, Object.class),
        CreateInliningPlan(Object.class, Object.class, Object.class, Object.class),
        CreateStringSupplier(Supplier.class, long.class),
        DequeueTargets(void.class, Object.class),
        FindCallNode(Object.class, Object.class, long.class),
        FindDecision(Object.class, Object.class, long.class),
        GetCallCount(int.class, Object.class),
        GetCallNodes(Object[].class, Object.class),
        GetCallTargetForCallNode(long.class, Object.class, long.class),
        GetCompilableCallCount(int.class, Object.class),
        GetCompilableName(String.class, Object.class),
        GetConstantFieldInfo(int.class, Object.class, long.class, boolean.class, int.class),
        GetCurrentCallTarget(Object.class, Object.class),
        GetDescription(String.class, Object.class),
        GetFailedSpeculationsAddress(long.class, Object.class),
        GetFrameSlotKindTagForJavaKind(int.class, Object.class, int.class),
        GetFrameSlotKindTagsCount(int.class, Object.class),
        GetInlineKind(int.class, Object.class, long.class, boolean.class),
        GetJavaKindForFrameSlotKind(int.class, Object.class, int.class),
        GetKnownCallSiteCount(int.class, Object.class),
        GetLanguage(String.class, Object.class),
        GetLineNumber(int.class, Object.class),
        GetLoopExplosionKind(int.class, Object.class, long.class),
        GetNodeRewritingAssumption(long.class, Object.class),
        GetNodeRewritingAssumptionConstant(long.class, Object.class),
        GetNonTrivialNodeCount(int.class, Object.class),
        GetOffsetEnd(int.class, Object.class),
        GetOffsetStart(int.class, Object.class),
        GetPosition(Object.class, Object.class, long.class),
        GetSuppliedString(String.class, Supplier.class),
        GetTargetName(String.class, Object.class),
        GetTruffleCallBoundaryMethods(long[].class, Object.class),
        GetURI(String.class, Object.class),
        IsCancelled(boolean.class, Object.class),
        IsInliningForced(boolean.class, Object.class),
        IsLastTier(boolean.class, Object.class),
        IsSameOrSplit(boolean.class, Object.class, Object.class),
        IsTargetStable(boolean.class, Object.class),
        IsTruffleBoundary(boolean.class, Object.class, long.class),
        IsValueType(boolean.class, Object.class, long.class),
        Log(void.class, Object.class, Object.class, String.class),
        OnCodeInstallation(void.class, Object.class, Object.class, long.class),
        OnCompilationFailed(void.class, Object.class, Supplier.class, boolean.class, boolean.class),
        OnFailure(void.class, Object.class, Object.class, String.class, boolean.class, boolean.class),
        OnGraalTierFinished(void.class, Object.class, Object.class, long.class),
        OnSuccess(void.class, Object.class, Object.class, Object.class, long.class, long.class),
        OnTruffleTierFinished(void.class, Object.class, Object.class, Object.class, long.class),
        RegisterOptimizedAssumptionDependency(Consumer.class, Object.class, long.class),
        ShouldInline(boolean.class, Object.class);
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
