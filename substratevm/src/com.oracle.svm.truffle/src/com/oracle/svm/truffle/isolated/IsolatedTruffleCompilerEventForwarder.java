/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.isolated;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;

final class IsolatedTruffleCompilerEventForwarder implements TruffleCompilerListener {
    private final ClientHandle<IsolatedEventContext> contextHandle;

    IsolatedTruffleCompilerEventForwarder(ClientHandle<IsolatedEventContext> contextHandle) {
        this.contextHandle = contextHandle;
    }

    @Override
    public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
        onGraalTierFinished0(IsolatedCompileContext.get().getClient(), contextHandle, IsolatedCompileContext.get().hand(graph), graph.getNodeCount());
    }

    @Override
    public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
        onTruffleTierFinished0(IsolatedCompileContext.get().getClient(), contextHandle, IsolatedCompileContext.get().hand(graph), graph.getNodeCount());
    }

    @Override
    public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph, CompilationResultInfo info) {
        IsolatedCompilationResultData data = StackValue.get(IsolatedCompilationResultData.class);
        data.setOriginalObjectHandle(IsolatedCompileContext.get().hand(info));
        data.setTargetCodeSize(info.getTargetCodeSize());
        data.setTotalFrameSize(info.getTotalFrameSize());
        data.setExceptionHandlersCount(info.getExceptionHandlersCount());
        data.setInfopointsCount(info.getInfopointsCount());
        data.setMarksCount(info.getMarksCount());
        data.setDataPatchesCount(info.getDataPatchesCount());
        onSuccess0(IsolatedCompileContext.get().getClient(), contextHandle, IsolatedCompileContext.get().hand(graph), graph.getNodeCount(), data);
    }

    @Override
    public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
        try (CCharPointerHolder reasonCstr = CTypeConversion.toCString(reason)) {
            onFailure0(IsolatedCompileContext.get().getClient(), contextHandle, reasonCstr.get(), bailout, permanentBailout);
        }
    }

    @Override
    public void onCompilationRetry(CompilableTruffleAST compilable) {
        onCompilationRetry0(IsolatedCompileContext.get().getClient(), contextHandle);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void onGraalTierFinished0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<IsolatedEventContext> contextHandle, CompilerHandle<GraphInfo> graphInfo, int nodeCount) {
        IsolatedEventContext context = IsolatedCompileClient.get().unhand(contextHandle);
        context.listener.onGraalTierFinished(context.compilable, new IsolatedGraphInfo(graphInfo, nodeCount));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void onTruffleTierFinished0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<IsolatedEventContext> contextHandle, CompilerHandle<GraphInfo> graphInfo, int nodeCount) {
        IsolatedEventContext context = IsolatedCompileClient.get().unhand(contextHandle);
        context.listener.onTruffleTierFinished(context.compilable, context.inlining, new IsolatedGraphInfo(graphInfo, nodeCount));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void onSuccess0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<IsolatedEventContext> contextHandle,
                    CompilerHandle<GraphInfo> graphInfo, int nodeCount, IsolatedCompilationResultData resultData) {

        IsolatedEventContext context = IsolatedCompileClient.get().unhand(contextHandle);
        context.listener.onSuccess(context.compilable, context.inlining, new IsolatedGraphInfo(graphInfo, nodeCount), new IsolatedCompilationResultInfo(resultData));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void onFailure0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<IsolatedEventContext> contextHandle,
                    CCharPointer reason, boolean bailout, boolean permanentBailout) {
        IsolatedEventContext context = IsolatedCompileClient.get().unhand(contextHandle);
        context.listener.onFailure(context.compilable, CTypeConversion.toJavaString(reason), bailout, permanentBailout);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void onCompilationRetry0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<IsolatedEventContext> contextHandle) {
        IsolatedEventContext context = IsolatedCompileClient.get().unhand(contextHandle);
        context.listener.onCompilationRetry(context.compilable);
    }
}

/** Objects commonly needed for events, gathered for quick access in the client isolate. */
final class IsolatedEventContext {
    final TruffleCompilerListener listener;
    final CompilableTruffleAST compilable;
    final TruffleInliningPlan inlining;

    IsolatedEventContext(TruffleCompilerListener listener, CompilableTruffleAST compilable, TruffleInliningPlan inlining) {
        this.listener = listener;
        this.compilable = compilable;
        this.inlining = inlining;
    }
}

final class IsolatedGraphInfo implements GraphInfo {
    private final CompilerHandle<GraphInfo> originalObjectHandle;
    private final int nodeCount;

    IsolatedGraphInfo(CompilerHandle<GraphInfo> originalObjectHandle, int nodeCount) {
        this.originalObjectHandle = originalObjectHandle;
        this.nodeCount = nodeCount;
    }

    @Override
    public int getNodeCount() {
        return nodeCount;
    }

    @Override
    public String[] getNodeTypes(boolean simpleNames) {
        ClientHandle<String[]> handle = getNodeTypes0(IsolatedCompileClient.get().getCompiler(), originalObjectHandle, simpleNames);
        return IsolatedCompileClient.get().unhand(handle);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<String[]> getNodeTypes0(@SuppressWarnings("unused") CompilerIsolateThread compiler, CompilerHandle<GraphInfo> infoHandle, boolean simpleNames) {
        GraphInfo info = IsolatedCompileContext.get().unhand(infoHandle);
        return IsolatedCompileContext.get().createStringArrayInClient(info.getNodeTypes(simpleNames));
    }
}

final class IsolatedCompilationResultInfo implements CompilationResultInfo {
    private CompilerHandle<CompilationResultInfo> originalObjectHandle;
    private final int targetCodeSize;
    private final int totalFrameSize;
    private final int exceptionHandlersCount;
    private final int infopointsCount;
    private final int marksCount;
    private final int dataPatchesCount;

    IsolatedCompilationResultInfo(IsolatedCompilationResultData data) {
        originalObjectHandle = data.getOriginalObjectHandle();
        targetCodeSize = data.getTargetCodeSize();
        totalFrameSize = data.getTotalFrameSize();
        exceptionHandlersCount = data.getExceptionHandlersCount();
        infopointsCount = data.getInfopointsCount();
        marksCount = data.getMarksCount();
        dataPatchesCount = data.getDataPatchesCount();
    }

    @Override
    public int getTargetCodeSize() {
        return targetCodeSize;
    }

    @Override
    public int getTotalFrameSize() {
        return totalFrameSize;
    }

    @Override
    public int getExceptionHandlersCount() {
        return exceptionHandlersCount;
    }

    @Override
    public int getInfopointsCount() {
        return infopointsCount;
    }

    @Override
    public String[] getInfopoints() {
        ClientHandle<String[]> handle = getInfopoints0(IsolatedCompileClient.get().getCompiler(), originalObjectHandle);
        return IsolatedCompileClient.get().unhand(handle);
    }

    @Override
    public int getMarksCount() {
        return marksCount;
    }

    @Override
    public int getDataPatchesCount() {
        return dataPatchesCount;
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<String[]> getInfopoints0(@SuppressWarnings("unused") CompilerIsolateThread compiler, CompilerHandle<CompilationResultInfo> infoHandle) {
        CompilationResultInfo info = IsolatedCompileContext.get().unhand(infoHandle);
        return IsolatedCompileContext.get().createStringArrayInClient(info.getInfopoints());
    }
}

@RawStructure
interface IsolatedCompilationResultData extends PointerBase {
    @RawField
    CompilerHandle<CompilationResultInfo> getOriginalObjectHandle();

    @RawField
    void setOriginalObjectHandle(CompilerHandle<CompilationResultInfo> value);

    @RawField
    int getTargetCodeSize();

    @RawField
    void setTargetCodeSize(int value);

    @RawField
    int getTotalFrameSize();

    @RawField
    void setTotalFrameSize(int value);

    @RawField
    int getExceptionHandlersCount();

    @RawField
    void setExceptionHandlersCount(int value);

    @RawField
    int getInfopointsCount();

    @RawField
    void setInfopointsCount(int value);

    @RawField
    int getMarksCount();

    @RawField
    void setMarksCount(int value);

    @RawField
    int getDataPatchesCount();

    @RawField
    void setDataPatchesCount(int value);
}
