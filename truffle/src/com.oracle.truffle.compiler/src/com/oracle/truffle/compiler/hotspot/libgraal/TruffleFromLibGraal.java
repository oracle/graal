/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.compiler.hotspot.libgraal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
        @Signature({long.class, Object.class})
        EngineId,
        @Signature({int.class, Object.class})
        GetCompilableCallCount,
        @Signature({String.class, Object.class})
        GetCompilableName,
        @Signature({byte[].class, Object.class})
        GetCompilerOptions,
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
        @Signature({void.class, Object.class, Object.class, String.class, boolean.class, boolean.class, int.class, long.class})
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
        SetCallCounts,
        @Signature({void.class, long.class})
        OnIsolateShutdown;
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
