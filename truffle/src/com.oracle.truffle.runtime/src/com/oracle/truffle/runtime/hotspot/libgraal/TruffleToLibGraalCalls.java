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
package com.oracle.truffle.runtime.hotspot.libgraal;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.DoCompile;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationFactoryName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerVersion;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetDataPatchesCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetExceptionHandlersCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopoints;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopointsCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetMarksCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeTypes;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetSuppliedString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetTargetCodeSize;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.GetTotalFrameSize;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InitializeCompiler;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleCallBoundaryMethod;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleReservedOopMethod;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.NewCompiler;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.PendingTransferToInterpreterOffset;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.PurgePartialEvaluationCaches;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.RegisterRuntime;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id.Shutdown;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id;

/**
 * Native methods linked to libgraal entry points.
 */
final class TruffleToLibGraalCalls {

    /**
     * Invoked first time the isolate is attached.
     */
    @TruffleToLibGraal(Id.InitializeIsolate)
    static native boolean initializeIsolate(long isolateThreadId, Class<?> runtimeClass);

    /**
     * Registers a Truffle runtime. Returns <code>true</code> if this was the first runtime
     * registered and <code>false</code> if there were previous calls to
     * {@link #registerRuntime(long, Object)}.
     */
    @TruffleToLibGraal(RegisterRuntime)
    static native boolean registerRuntime(long isolateThreadId, Object truffleRuntime);

    @TruffleToLibGraal(InitializeRuntime)
    static native long initializeRuntime(long isolateThreadId, TruffleCompilerRuntime truffleRuntime, Class<?> classLoaderDelegate);

    @TruffleToLibGraal(Id.ListCompilerOptions)
    static native byte[] listCompilerOptions(long isolateThreadId);

    @TruffleToLibGraal(Id.CompilerOptionExists)
    static native boolean compilerOptionExists(long isolateThreadId, String optionName);

    @TruffleToLibGraal(Id.ValidateCompilerOption)
    static native String validateCompilerOption(long isolateThreadId, String optionName, String optionValue);

    @TruffleToLibGraal(GetCompilerConfigurationFactoryName)
    static native String getCompilerConfigurationFactoryName(long isolateThreadId, long truffleRuntimeHandle);

    @TruffleToLibGraal(NewCompiler)
    static native long newCompiler(long isolateThreadId, long truffleRuntimeHandle);

    @TruffleToLibGraal(InitializeCompiler)
    static native void initializeCompiler(long isolateThreadId, long compilerHandle, TruffleCompilable compilable, boolean firstInitialization);

    @TruffleToLibGraal(DoCompile)
    static native void doCompile(long isolateThreadId,
                    long compilerHandle,
                    TruffleCompilationTask task,
                    TruffleCompilable compilable,
                    TruffleCompilerListener listener);

    @TruffleToLibGraal(InstallTruffleCallBoundaryMethod)
    static native void installTruffleCallBoundaryMethod(long isolateThreadId, long handle, long methodHandle);

    @TruffleToLibGraal(InstallTruffleReservedOopMethod)
    static native void installTruffleReservedOopMethod(long isolateThreadId, long handle, long methodHandle);

    @TruffleToLibGraal(PendingTransferToInterpreterOffset)
    static native int pendingTransferToInterpreterOffset(long isolateThreadId, long handle, TruffleCompilable compilable);

    @TruffleToLibGraal(Shutdown)
    static native void shutdown(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetNodeCount)
    static native int getNodeCount(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetNodeTypes)
    static native String[] getNodeTypes(long isolateThreadId, long handle, boolean simpleNames);

    @TruffleToLibGraal(GetSuppliedString)
    static native String getSuppliedString(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetTargetCodeSize)
    static native int getTargetCodeSize(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetTotalFrameSize)
    static native int getTotalFrameSize(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetExceptionHandlersCount)
    static native int getExceptionHandlersCount(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetInfopointsCount)
    static native int getInfopointsCount(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetInfopoints)
    static native String[] getInfopoints(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetMarksCount)
    static native int getMarksCount(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetDataPatchesCount)
    static native int getDataPatchesCount(long isolateThreadId, long handle);

    @TruffleToLibGraal(PurgePartialEvaluationCaches)
    static native void purgePartialEvaluationCaches(long isolateThreadId, long compilerHandle);

    @TruffleToLibGraal(GetCompilerVersion)
    static native String getCompilerVersion(long isolateThreadId);
}
