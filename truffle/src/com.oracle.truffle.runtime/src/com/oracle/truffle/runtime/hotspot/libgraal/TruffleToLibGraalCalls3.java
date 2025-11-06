/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.hotspot.libgraal;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.RegisterRuntime;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

/**
 * The method defined in this class can't be added to {@link TruffleToLibGraalCalls}, because the
 * code has to work also with libgraal which does not have the entry point for the method.
 * Therefore, {@link jdk.vm.ci.hotspot.HotSpotJVMCIRuntime#registerNativeMethods(Class) registering}
 * this class might throw an {@link UnsatisfiedLinkError error}.
 */
final class TruffleToLibGraalCalls3 {

    static final MethodHandle INITIALIZE_RUNTIME_HANDLE;
    static final MethodHandle REGISTER_RUNTIME_HANDLE;

    static {
        boolean linked = false;
        try {
            runtime().registerNativeMethods(TruffleToLibGraalCalls3.class);
            linked = true;
        } catch (UnsatisfiedLinkError e) {
            // The libgraal entry points are not available, pre 25.1 graal compiler.
        }
        if (linked) {
            try {
                MethodType type = MethodType.methodType(long.class, long.class, TruffleCompilerRuntime.class, Class.class, ByteBuffer.class);
                INITIALIZE_RUNTIME_HANDLE = MethodHandles.lookup().findStatic(TruffleToLibGraalCalls3.class, "initializeRuntime", type);
                type = MethodType.methodType(boolean.class, long.class, TruffleCompilerRuntime.class, ByteBuffer.class);
                REGISTER_RUNTIME_HANDLE = MethodHandles.lookup().findStatic(TruffleToLibGraalCalls3.class, "registerRuntime", type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        } else {
            INITIALIZE_RUNTIME_HANDLE = null;
            REGISTER_RUNTIME_HANDLE = null;
        }
    }

    static void initialize() {
        // Runs static initializer to initialize method handles
    }

    @TruffleToLibGraal(InitializeRuntime)
    static native long initializeRuntime(long isolateThreadAddress, TruffleCompilerRuntime truffleRuntime, Class<?> classLoaderDelegate, ByteBuffer javaInstrumentationActive);

    @TruffleToLibGraal(RegisterRuntime)
    static native boolean registerRuntime(long isolateThreadAddress, TruffleCompilerRuntime truffleRuntime, ByteBuffer javaInstrumentationActive);
}
