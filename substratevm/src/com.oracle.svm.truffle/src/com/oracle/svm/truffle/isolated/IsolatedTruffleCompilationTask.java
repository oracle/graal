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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedHandles;
import com.oracle.svm.graal.isolated.IsolatedObjectConstant;
import com.oracle.svm.graal.isolated.IsolatedObjectProxy;
import com.oracle.svm.truffle.api.SubstrateCompilableTruffleAST;
import com.oracle.svm.truffle.isolated.BinaryOutput.ByteArrayBinaryOutput;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;

import jdk.vm.ci.meta.JavaConstant;

final class IsolatedTruffleCompilationTask extends IsolatedObjectProxy<TruffleCompilationTask> implements TruffleCompilationTask {

    IsolatedTruffleCompilationTask(ClientHandle<TruffleCompilationTask> handle) {
        super(handle);
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean isLastTier() {
        return isLastTier0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean isFirstTier() {
        return isFirstTier0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean hasNextTier() {
        return hasNextTier0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant nodeConstant) {
        if (!(nodeConstant instanceof IsolatedObjectConstant)) {
            return null; // not an AST node, therefore not handled by this method
        }
        ClientHandle<?> nodeConstantHandle = ((IsolatedObjectConstant) nodeConstant).getHandle();
        CompilerHandle<TruffleSourceLanguagePosition> position = getPosition0(IsolatedCompileContext.get().getClient(), handle, nodeConstantHandle);
        return IsolatedCompileContext.get().unhand(position);
    }

    @Override
    public Map<String, Object> getDebugProperties(JavaConstant nodeConstant) {
        if (!(nodeConstant instanceof IsolatedObjectConstant)) {
            return Collections.emptyMap(); // not an AST node, therefore not handled by this method
        }
        ClientHandle<?> nodeConstantHandle = ((IsolatedObjectConstant) nodeConstant).getHandle();
        Map<String, Object> debugProperties = new LinkedHashMap<>();
        var debugPropertiesHandle = IsolatedCompileContext.get().hand(debugProperties);
        getDebugProperties0(IsolatedCompileContext.get().getClient(), handle, nodeConstantHandle, debugPropertiesHandle);
        return debugProperties;
    }

    @Override
    public void addTargetToDequeue(TruffleCompilable target) {
        ClientHandle<SubstrateCompilableTruffleAST> targetHandle = ((IsolatedCompilableTruffleAST) target).getHandle();
        addTargetToDequeue0(IsolatedCompileContext.get().getClient(), handle, targetHandle);
    }

    @Override
    public void setCallCounts(int total, int inlined) {
        setCallCounts0(IsolatedCompileContext.get().getClient(), handle, total, inlined);
    }

    @Override
    public void addInlinedTarget(TruffleCompilable target) {
        ClientHandle<SubstrateCompilableTruffleAST> targetHandle = ((IsolatedCompilableTruffleAST) target).getHandle();
        addInlinedTarget0(IsolatedCompileContext.get().getClient(), handle, targetHandle);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.BooleanExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static boolean isCancelled0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).isCancelled();
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.BooleanExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static boolean isLastTier0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).isLastTier();
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.BooleanExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static boolean isFirstTier0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).isFirstTier();
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.BooleanExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static boolean hasNextTier0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).hasNextTier();
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<TruffleSourceLanguagePosition> getPosition0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleCompilationTask> inliningHandle, ClientHandle<?> callNodeConstantHandle) {

        TruffleCompilationTask task = IsolatedCompileClient.get().unhand(inliningHandle);
        JavaConstant callNodeConstant = SubstrateObjectConstant.forObject(IsolatedCompileClient.get().unhand(callNodeConstantHandle));
        TruffleSourceLanguagePosition position = task.getPosition(callNodeConstant);
        if (position == null) {
            return IsolatedHandles.nullHandle();
        }
        return createPositionInCompiler(IsolatedCompileClient.get().getCompiler(), IsolatedCompileClient.get().hand(position),
                        position.getLineNumber(), position.getOffsetStart(), position.getOffsetEnd(), position.getNodeId());
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.VoidExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    @SuppressWarnings("unused")
    private static void fillDebugProperties0(@CEntryPoint.IsolateThreadContext CompilerIsolateThread context,
                    ClientIsolateThread client, CCharPointer buffer, int bufferLength,
                    CompilerHandle<Map<String, Object>> targetPropertiesHandle) {
        Map<String, Object> targetProperties = IsolatedCompileContext.get().unhand(targetPropertiesHandle);
        readDebugMap(targetProperties, BinaryInput.create(buffer, bufferLength));
    }

    private static Map<String, Object> readDebugMap(Map<String, Object> map, BinaryInput in) {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = in.readTypedValue();
            map.put(key, value);
        }
        return map;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.VoidExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    @SuppressWarnings("unused")
    private static void getDebugProperties0(ClientIsolateThread client,
                    ClientHandle<? extends TruffleCompilationTask> inliningHandle,
                    ClientHandle<?> callNodeConstantHandle,
                    CompilerHandle<Map<String, Object>> targetProperties) {
        TruffleCompilationTask task = IsolatedCompileClient.get().unhand(inliningHandle);
        JavaConstant callNodeConstant = SubstrateObjectConstant.forObject(IsolatedCompileClient.get().unhand(callNodeConstantHandle));
        Map<String, Object> debugProperties = task.getDebugProperties(callNodeConstant);
        ByteArrayBinaryOutput out = BinaryOutput.create();
        writeDebugMap(out, debugProperties);
        byte[] array = out.getArray();
        try (CTypeConversion.CCharPointerHolder pin = CTypeConversion.toCBytes(array)) {
            fillDebugProperties0(IsolatedCompileClient.get().getCompiler(), client, pin.get(), array.length, targetProperties);
        }
    }

    private static void writeDebugMap(BinaryOutput out, Map<String, Object> map) {
        out.writeInt(map.size());
        for (Map.Entry<String, Object> e : map.entrySet()) {
            out.writeUTF(e.getKey());
            writeDebugMapValue(out, e.getValue());
        }
    }

    private static void writeDebugMapValue(BinaryOutput out, Object object) {
        Object useValue = object;
        if (!BinaryOutput.isTypedValue(useValue)) {
            useValue = object.toString();
        }
        out.writeTypedValue(useValue);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.VoidExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static void addTargetToDequeue0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleCompilationTask> providerHandle,
                    ClientHandle<SubstrateCompilableTruffleAST> targetHandle) {
        final IsolatedCompileClient isolatedCompileClient = IsolatedCompileClient.get();
        TruffleCompilationTask task = isolatedCompileClient.unhand(providerHandle);
        task.addTargetToDequeue(isolatedCompileClient.unhand(targetHandle));
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.VoidExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static void addInlinedTarget0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleCompilationTask> providerHandle,
                    ClientHandle<SubstrateCompilableTruffleAST> targetHandle) {
        final IsolatedCompileClient isolatedCompileClient = IsolatedCompileClient.get();
        TruffleCompilationTask task = isolatedCompileClient.unhand(providerHandle);
        task.addInlinedTarget(isolatedCompileClient.unhand(targetHandle));
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.VoidExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static void setCallCounts0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleCompilationTask> handle, int total, int inlined) {
        TruffleCompilationTask task = IsolatedCompileClient.get().unhand(handle);
        task.setCallCounts(total, inlined);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<TruffleSourceLanguagePosition> createPositionInCompiler(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ClientHandle<TruffleSourceLanguagePosition> positionHandle, int lineNumber, int offsetStart, int offsetEnd, int nodeId) {
        return IsolatedCompileContext.get().hand(new IsolatedTruffleSourceLanguagePosition(positionHandle, lineNumber, offsetStart, offsetEnd, nodeId));
    }

}
