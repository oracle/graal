/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;

public abstract class CallableFromNative extends SubstitutionProfiler {

    public static boolean validParameterCount(Factory factory, Method.MethodVersion methodVersion) {
        /*
         * Static native methods prepends the Class in the arg array, and instance methods do not
         * include the receiver in the parameter count.
         */
        return (factory.parameterCount() == methodVersion.getMethod().getParameterCount() + 1);
    }

    public abstract static class Factory {
        public abstract CallableFromNative create();

        private final String methodName;
        private final NativeSignature nativeSignature;
        private final int parameterCount;
        private final boolean prependEnv;

        protected Factory(String methodName, NativeSignature nativeSignature, int parameterCount, boolean prependEnv) {
            this.methodName = methodName;
            this.nativeSignature = nativeSignature;
            this.parameterCount = parameterCount;
            this.prependEnv = prependEnv;
        }

        public String methodName() {
            return methodName;
        }

        public NativeSignature jniNativeSignature() {
            return nativeSignature;
        }

        public int parameterCount() {
            return parameterCount;
        }

        public NativeType returnType() {
            return nativeSignature.getReturnType();
        }

        public boolean prependEnv() {
            return prependEnv;
        }
    }

    /**
     * Returns the name of the annotation that generated the node.
     *
     * @return The annotation type.
     */
    public abstract String generatedBy();

    /**
     * The method to invoke when coming from native code.
     * 
     * @param env The env corresponding to this callable
     * @param args The arguments to the method. Note that coming from native, the arguments are
     *            formed as follows: {@code env} is passed first, then java objects arguments are
     *            passed as JNI handles.
     */
    public abstract Object invoke(Object env, Object[] args);

    /**
     * The method to invoke when coming from java code.
     * 
     * @param env The env corresponding to this callable
     * @param args Arguments to the method. In this case, the arguments are directly passed, and
     *            there is no need to un-handlify.
     */
    public Object invokeDirect(Object env, Object[] args) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Native method should not be reachable for java substitution");
    }
}
