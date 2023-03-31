/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DoCompile;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationFactoryName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetDataPatchesCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetExceptionHandlersCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopoints;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopointsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetMarksCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeTypes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTargetCodeSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTotalFrameSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleCallBoundaryMethod;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleReservedOopMethod;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.NewCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.PendingTransferToInterpreterOffset;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.PurgePartialEvaluationCaches;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.Shutdown;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;

/**
 * Native methods linked to libgraal entry points.
 */
final class TruffleToLibGraalCalls {

    @TruffleToLibGraal(InitializeRuntime)
    static native long initializeRuntime(long isolateThreadId, TruffleCompilerRuntime truffleRuntime, long classLoaderDelegateId);

    @TruffleToLibGraal(GetCompilerConfigurationFactoryName)
    static native String getCompilerConfigurationFactoryName(long isolateThreadId, long truffleRuntimeHandle);

    @TruffleToLibGraal(NewCompiler)
    static native long newCompiler(long isolateThreadId, long truffleRuntimeHandle);

    @TruffleToLibGraal(InitializeCompiler)
    static native void initializeCompiler(long isolateThreadId, long compilerHandle, byte[] options, CompilableTruffleAST compilable, boolean firstInitialization);

    @TruffleToLibGraal(GetCompilerConfigurationName)
    static native String getCompilerConfigurationName(long isolateThreadId, long handle);

    @TruffleToLibGraal(DoCompile)
    static native void doCompile(long isolateThreadId,
                    long compilerHandle,
                    TruffleCompilationTask task,
                    CompilableTruffleAST compilable,
                    byte[] options,
                    TruffleCompilerListener listener);

    @TruffleToLibGraal(InstallTruffleCallBoundaryMethod)
    static native void installTruffleCallBoundaryMethod(long isolateThreadId, long handle, long methodHandle);

    @TruffleToLibGraal(InstallTruffleReservedOopMethod)
    static native void installTruffleReservedOopMethod(long isolateThreadId, long handle, long methodHandle);

    @TruffleToLibGraal(PendingTransferToInterpreterOffset)
    static native int pendingTransferToInterpreterOffset(long isolateThreadId, long handle, CompilableTruffleAST compilable);

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
}
