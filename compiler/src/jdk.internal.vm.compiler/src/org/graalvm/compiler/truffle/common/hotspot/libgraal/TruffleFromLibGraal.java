/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
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
     * Specifies the signature (return and parameter types) of a call from Truffle to libgraal.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Signature {
        /**
         * An array of types where the first element is the return type and the remaining elements
         * are the parameter types of the signature.
         */
        Class<?>[] value();
    }

    /**
     * Identifier for a call to HotSpot from libgraal.
     */
    // Please keep sorted
    enum Id implements FromLibGraalId {
        // @formatter:off
        @Signature({void.class, Object.class, Object.class})
        AddTargetToDequeue,
        @Signature({void.class, Object.class, Object.class})
        AddInlinedTarget,
        @Signature({Object.class, Object.class, long.class})
        AsCompilableTruffleAST,
        @Signature({long.class, Object.class})
        AsJavaConstant,
        @Signature({boolean.class, Object.class, String.class})
        CancelCompilation,
        @Signature({String.class, Object.class})
        CompilableToString,
        @Signature({void.class, Consumer.class, Object.class, long.class})
        ConsumeOptimizedAssumptionDependency,
        @Signature({Supplier.class, long.class})
        CreateStringSupplier,
        @Signature({int.class, Object.class})
        CountDirectCallNodes,
        @Signature({long.class, Object.class, long.class})
        GetCallTargetForCallNode,
        @Signature({int.class, Object.class})
        GetCompilableCallCount,
        @Signature({String.class, Object.class})
        GetCompilableName,
        @Signature({int.class, Object.class, long.class, boolean.class, int.class})
        GetConstantFieldInfo,
        @Signature({String.class, Object.class})
        GetDescription,
        @Signature({long.class, Object.class})
        GetFailedSpeculationsAddress,
        @Signature({int.class, Object.class})
        GetKnownCallSiteCount,
        @Signature({String.class, Object.class})
        GetLanguage,
        @Signature({int.class, Object.class})
        GetLineNumber,
        @Signature({void.class, Object.class})
        PrepareForCompilation,
        @Signature({int.class, Object.class})
        GetNodeId,
        @Signature({String.class, Object.class})
        GetNodeClassName,
        @Signature({byte[].class, Object.class, long.class})
        GetDebugProperties,
        @Signature({int.class, Object.class})
        GetNonTrivialNodeCount,
        @Signature({int.class, Object.class})
        GetOffsetEnd,
        @Signature({int.class, Object.class})
        GetOffsetStart,
        @Signature({Object.class, Object.class, long.class})
        GetPosition,
        @Signature({String.class, Supplier.class})
        GetSuppliedString,
        @Signature({String.class, Object.class})
        GetURI,
        @Signature({boolean.class, Object.class})
        IsCancelled,
        @Signature({boolean.class, Object.class})
        IsLastTier,
        @Signature({boolean.class, Object.class})
        HasNextTier,
        @Signature({boolean.class, Object.class, Object.class})
        IsSameOrSplit,
        @Signature({boolean.class, Object.class, Object.class, Supplier.class})
        IsSuppressedFailure,
        @Signature({boolean.class, Object.class})
        IsTrivial,
        @Signature({boolean.class, Object.class, long.class})
        IsValueType,
        @Signature({void.class, Object.class, String.class, Object.class, String.class})
        Log,
        @Signature({void.class, Object.class, Object.class, long.class})
        OnCodeInstallation,
        @Signature({void.class, Object.class, Supplier.class, boolean.class, boolean.class, boolean.class, boolean.class})
        OnCompilationFailed,
        @Signature({void.class, Object.class, Object.class, Object.class})
        OnCompilationRetry,
        @Signature({void.class, Object.class, Object.class, String.class, boolean.class, boolean.class, int.class})
        OnFailure,
        @Signature({void.class, Object.class, Object.class, long.class})
        OnGraalTierFinished,
        @Signature({void.class, Object.class, Object.class, Object.class, long.class, long.class, int.class})
        OnSuccess,
        @Signature({void.class, Object.class, Object.class, Object.class, long.class})
        OnTruffleTierFinished,
        @Signature({Object.class, Object.class, long.class})
        GetPartialEvaluationMethodInfo,
        @Signature({Object.class, Object.class, long.class})
        GetHostMethodInfo,
        @Signature({Consumer.class, Object.class, long.class})
        RegisterOptimizedAssumptionDependency,
        @Signature({void.class, Object.class, int.class, int.class})
        SetCallCounts;
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

        Id() {
            try {
                Signature sig = Id.class.getDeclaredField(name()).getAnnotation(Signature.class);
                Class<?>[] sigTypes = sig.value();
                this.returnType = sigTypes[0];
                this.parameterTypes = Arrays.copyOfRange(sigTypes, 1, sigTypes.length);
                signature = FromLibGraalId.encodeMethodSignature(returnType, parameterTypes);
                methodName = Character.toLowerCase(name().charAt(0)) + name().substring(1);
            } catch (NoSuchFieldException e) {
                throw new InternalError(e);
            }
        }
    }
}
