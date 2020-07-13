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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.CloseCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.CloseDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.CloseDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DoCompile;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DumpChannelClose;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.DumpChannelWrite;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationFactoryName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetCompilerConfigurationName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetDataPatchesCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetDumpChannel;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetExceptionHandlersCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetExecutionID;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetGraphDumpDirectory;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopoints;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInfopointsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetInitialOptions;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetMarksCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetNodeTypes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTargetCodeSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTotalFrameSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTruffleCompilationId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetTruffleCompilationTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.GetVersionProperties;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InitializeRuntime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.InstallTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsBasicDumpEnabled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.IsDumpChannelOpen;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.NewCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.OpenDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.PendingTransferToInterpreterOffset;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.Shutdown;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.TtyWriteByte;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal.Id.TtyWriteBytes;

import java.nio.ByteBuffer;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
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

    @TruffleToLibGraal(GetInitialOptions)
    static native byte[] getInitialOptions(long isolateThreadId, long truffleRuntimeHandle);

    @TruffleToLibGraal(OpenCompilation)
    static native long openCompilation(long isolateThreadId, long handle, CompilableTruffleAST compilable);

    @TruffleToLibGraal(GetCompilerConfigurationName)
    static native String getCompilerConfigurationName(long isolateThreadId, long handle);

    @TruffleToLibGraal(DoCompile)
    static native void doCompile(long isolateThreadId,
                    long compilerHandle,
                    long debugContextHandle,
                    long compilationHandle,
                    byte[] options,
                    TruffleInliningPlan inlining,
                    TruffleCompilationTask task,
                    TruffleCompilerListener listener);

    @TruffleToLibGraal(InstallTruffleCallBoundaryMethods)
    static native void installTruffleCallBoundaryMethods(long isolateThreadId, long handle, CompilableTruffleAST compilable);

    @TruffleToLibGraal(PendingTransferToInterpreterOffset)
    static native int pendingTransferToInterpreterOffset(long isolateThreadId, long handle, CompilableTruffleAST compilable);

    @TruffleToLibGraal(Shutdown)
    static native void shutdown(long isolateThreadId, long handle);

    @TruffleToLibGraal(GetGraphDumpDirectory)
    static native String getGraphDumpDirectory(long isolateThreadId);

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

    @TruffleToLibGraal(OpenDebugContext)
    static native long openDebugContext(long isolateThreadId, long compilerHandle, long compilationHandle, byte[] options);

    @TruffleToLibGraal(CloseDebugContext)
    static native void closeDebugContext(long isolateThreadId, long handle);

    @TruffleToLibGraal(OpenDebugContextScope)
    static native long openDebugContextScope(long isolateThreadId, long ownerHandle, String name, long compilationHandle);

    @TruffleToLibGraal(CloseDebugContextScope)
    static native void closeDebugContextScope(long isolateThreadId, long handle);

    @TruffleToLibGraal(IsBasicDumpEnabled)
    static native boolean isBasicDumpEnabled(long isolateThreadId, long handle);

    @TruffleToLibGraal(CloseCompilation)
    static native void closeCompilation(long isolateThreadId, long compilationHandle);

    @TruffleToLibGraal(GetTruffleCompilationTruffleAST)
    static native CompilableTruffleAST getTruffleCompilationTruffleAST(long isolateThreadId, long compilationHandle);

    @TruffleToLibGraal(GetTruffleCompilationId)
    static native String getTruffleCompilationId(long isolateThreadId, long compilationHandle);

    @TruffleToLibGraal(GetVersionProperties)
    static native byte[] getVersionProperties(long isolateThreadId);

    @TruffleToLibGraal(GetDumpChannel)
    static native long getDumpChannel(long isolateThreadId, long debugContextHandle);

    @TruffleToLibGraal(IsDumpChannelOpen)
    static native boolean isDumpChannelOpen(long isolateThreadId, long channelHandle);

    @TruffleToLibGraal(DumpChannelWrite)
    static native int dumpChannelWrite(long isolateThreadId, long channelHandle, ByteBuffer buffer, int capacity, int position, int limit);

    @TruffleToLibGraal(DumpChannelClose)
    static native void dumpChannelClose(long isolateThreadId, long channelHandle);

    @TruffleToLibGraal(TtyWriteByte)
    static native void ttyWriteByte(long isolateThreadId, int b);

    @TruffleToLibGraal(TtyWriteBytes)
    static native void ttyWriteBytes(long isolateThreadId, byte[] b, int offset, int len);

    @TruffleToLibGraal(GetExecutionID)
    static native String getExecutionID(long isolateThreadId);
}
