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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.CloseCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.CloseDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.CloseDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.DoCompile;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.DumpChannelClose;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.DumpChannelWrite;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetCompilerConfigurationFactoryName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetCompilerConfigurationName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetDataPatchesCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetDumpChannel;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetExceptionHandlersCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetGraphDumpDirectory;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetInfopoints;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetInfopointsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetInitialOptions;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetMarksCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetNodeTypes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTargetCodeSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTotalFrameSize;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTruffleCompilationId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.GetTruffleCompilationTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.InitializeCompiler;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.InitializeRuntime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.InstallTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.IsBasicDumpEnabled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.IsDumpChannelOpen;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.OpenCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.OpenDebugContext;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.OpenDebugContextScope;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.PendingTransferToInterpreterOffset;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.ReleaseHandle;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id.Shutdown;

import java.nio.ByteBuffer;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;

/**
 * Native methods linked to SVM entry points.
 */
final class HotSpotToSVMCalls {

    @HotSpotToSVM(InitializeRuntime)
    static native long initializeRuntime(long isolateThreadId, TruffleCompilerRuntime truffleRuntime, long classLoaderDelegateId);

    @HotSpotToSVM(GetCompilerConfigurationFactoryName)
    static native String getCompilerConfigurationFactoryName(long isolateThreadId);

    @HotSpotToSVM(InitializeCompiler)
    static native long initializeCompiler(long isolateThreadId, long truffleRuntimeHandle);

    @HotSpotToSVM(GetInitialOptions)
    static native byte[] getInitialOptions(long isolateThreadId, long truffleRuntimeHandle);

    @HotSpotToSVM(ReleaseHandle)
    static native void releaseHandle(long isolateThreadId, long handle);

    @HotSpotToSVM(OpenCompilation)
    static native long openCompilation(long isolateThreadId, long handle, CompilableTruffleAST compilable);

    @HotSpotToSVM(GetCompilerConfigurationName)
    static native String getCompilerConfigurationName(long isolateThreadId, long handle);

    @HotSpotToSVM(DoCompile)
    static native void doCompile(long isolateThreadId,
                    long compilerHandle,
                    long debugContextHandle,
                    long compilationHandle,
                    byte[] options,
                    TruffleInliningPlan inlining,
                    TruffleCompilationTask task,
                    TruffleCompilerListener listener);

    @HotSpotToSVM(InstallTruffleCallBoundaryMethods)
    static native void installTruffleCallBoundaryMethods(long isolateThreadId, long handle);

    @HotSpotToSVM(PendingTransferToInterpreterOffset)
    static native int pendingTransferToInterpreterOffset(long isolateThreadId, long handle);

    @HotSpotToSVM(Shutdown)
    static native void shutdown(long isolateThreadId, long handle);

    @HotSpotToSVM(GetGraphDumpDirectory)
    static native String getGraphDumpDirectory(long isolateThreadId);

    @HotSpotToSVM(Log)
    static native void log(long isolateThreadId, String message);

    @HotSpotToSVM(GetNodeCount)
    static native int getNodeCount(long isolateThreadId, long handle);

    @HotSpotToSVM(GetNodeTypes)
    static native String[] getNodeTypes(long isolateThreadId, long handle, boolean simpleNames);

    @HotSpotToSVM(GetSuppliedString)
    static native String getSuppliedString(long isolateThreadId, long handle);

    @HotSpotToSVM(GetTargetCodeSize)
    static native int getTargetCodeSize(long isolateThreadId, long handle);

    @HotSpotToSVM(GetTotalFrameSize)
    static native int getTotalFrameSize(long isolateThreadId, long handle);

    @HotSpotToSVM(GetExceptionHandlersCount)
    static native int getExceptionHandlersCount(long isolateThreadId, long handle);

    @HotSpotToSVM(GetInfopointsCount)
    static native int getInfopointsCount(long isolateThreadId, long handle);

    @HotSpotToSVM(GetInfopoints)
    static native String[] getInfopoints(long isolateThreadId, long handle);

    @HotSpotToSVM(GetMarksCount)
    static native int getMarksCount(long isolateThreadId, long handle);

    @HotSpotToSVM(GetDataPatchesCount)
    static native int getDataPatchesCount(long isolateThreadId, long handle);

    @HotSpotToSVM(OpenDebugContext)
    static native long openDebugContext(long isolateThreadId, long compilerHandle, long compilationHandle, byte[] options);

    @HotSpotToSVM(CloseDebugContext)
    static native void closeDebugContext(long isolateThreadId, long handle);

    @HotSpotToSVM(OpenDebugContextScope)
    static native long openDebugContextScope(long isolateThreadId, long ownerHandle, String name, long compilationHandle);

    @HotSpotToSVM(CloseDebugContextScope)
    static native void closeDebugContextScope(long isolateThreadId, long handle);

    @HotSpotToSVM(IsBasicDumpEnabled)
    static native boolean isBasicDumpEnabled(long isolateThreadId, long handle);

    @HotSpotToSVM(CloseCompilation)
    static native void closeCompilation(long isolateThreadId, long compilationHandle);

    @HotSpotToSVM(GetTruffleCompilationTruffleAST)
    static native CompilableTruffleAST getTruffleCompilationTruffleAST(long isolateThreadId, long compilationHandle);

    @HotSpotToSVM(GetTruffleCompilationId)
    static native String getTruffleCompilationId(long isolateThreadId, long compilationHandle);

    @HotSpotToSVM(GetDumpChannel)
    static native long getDumpChannel(long isolateThreadId, long debugContextHandle);

    @HotSpotToSVM(IsDumpChannelOpen)
    static native boolean isDumpChannelOpen(long isolateThreadId, long channelHandle);

    @HotSpotToSVM(DumpChannelWrite)
    static native int dumpChannelWrite(long isolateThreadId, long channelHandle, ByteBuffer buffer, int capacity, int position, int limit);

    @HotSpotToSVM(DumpChannelClose)
    static native void dumpChannelClose(long isolateThreadId, long channelHandle);
}
