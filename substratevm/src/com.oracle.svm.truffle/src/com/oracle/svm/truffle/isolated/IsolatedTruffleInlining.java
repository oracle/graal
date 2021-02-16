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
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.nativeimage.c.function.CEntryPoint;

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

import jdk.vm.ci.meta.JavaConstant;

final class IsolatedTruffleInlining<T extends TruffleMetaAccessProvider> extends IsolatedObjectProxy<T> implements TruffleMetaAccessProvider {
    IsolatedTruffleInlining(ClientHandle<T> handle) {
        super(handle);
    }

    @Override
    public TruffleCallNode findCallNode(JavaConstant callNodeConstant) {
        ClientHandle<?> callNodeConstantHandle = ((IsolatedObjectConstant) callNodeConstant).getHandle();
        ClientHandle<TruffleCallNode> callNodeHandle = findCallNode0(IsolatedCompileContext.get().getClient(), handle, callNodeConstantHandle);
        return new IsolatedTruffleCallNode(callNodeHandle);
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
    public void addTargetToDequeue(CompilableTruffleAST target) {
        ClientHandle<SubstrateCompilableTruffleAST> targetHandle = ((IsolatedCompilableTruffleAST) target).getHandle();
        addTargetToDequeue0(IsolatedCompileContext.get().getClient(), handle, targetHandle);
    }

    @Override
    public void setCallCount(int count) {
        setCallCount0(IsolatedCompileContext.get().getClient(), handle, count);
    }

    @Override
    public void setInlinedCallCount(int count) {
        setInlinedCallCount0(IsolatedCompileContext.get().getClient(), handle, count);
    }

    @Override
    public void addInlinedTarget(CompilableTruffleAST target) {
        ClientHandle<SubstrateCompilableTruffleAST> targetHandle = ((IsolatedCompilableTruffleAST) target).getHandle();
        addInlinedTarget0(IsolatedCompileContext.get().getClient(), handle, targetHandle);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<TruffleCallNode> findCallNode0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> inliningHandle, ClientHandle<?> callNodeConstantHandle) {

        TruffleMetaAccessProvider inlining = IsolatedCompileClient.get().unhand(inliningHandle);
        JavaConstant callNodeConstant = SubstrateObjectConstant.forObject(IsolatedCompileClient.get().unhand(callNodeConstantHandle));
        TruffleCallNode callNode = inlining.findCallNode(callNodeConstant);
        return IsolatedCompileClient.get().hand(callNode);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<TruffleSourceLanguagePosition> getPosition0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> inliningHandle, ClientHandle<?> callNodeConstantHandle) {

        TruffleMetaAccessProvider inlining = IsolatedCompileClient.get().unhand(inliningHandle);
        JavaConstant callNodeConstant = SubstrateObjectConstant.forObject(IsolatedCompileClient.get().unhand(callNodeConstantHandle));
        TruffleSourceLanguagePosition position = inlining.getPosition(callNodeConstant);
        if (position == null) {
            return IsolatedHandles.nullHandle();
        }
        return createPositionInCompiler(IsolatedCompileClient.get().getCompiler(), IsolatedCompileClient.get().hand(position),
                        position.getLineNumber(), position.getOffsetStart(), position.getOffsetEnd(), position.getNodeId());
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void addTargetToDequeue0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> providerHandle,
                    ClientHandle<SubstrateCompilableTruffleAST> targetHandle) {
        final IsolatedCompileClient isolatedCompileClient = IsolatedCompileClient.get();
        TruffleMetaAccessProvider truffleMetaAccessProvider = isolatedCompileClient.unhand(providerHandle);
        truffleMetaAccessProvider.addTargetToDequeue(isolatedCompileClient.unhand(targetHandle));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void addInlinedTarget0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> providerHandle,
                    ClientHandle<SubstrateCompilableTruffleAST> targetHandle) {
        final IsolatedCompileClient isolatedCompileClient = IsolatedCompileClient.get();
        TruffleMetaAccessProvider truffleMetaAccessProvider = isolatedCompileClient.unhand(providerHandle);
        truffleMetaAccessProvider.addInlinedTarget(isolatedCompileClient.unhand(targetHandle));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void setCallCount0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> handle, int count) {
        TruffleMetaAccessProvider truffleMetaAccessProvider = IsolatedCompileClient.get().unhand(handle);
        truffleMetaAccessProvider.setCallCount(count);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void setInlinedCallCount0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> handle, int count) {
        TruffleMetaAccessProvider truffleMetaAccessProvider = IsolatedCompileClient.get().unhand(handle);
        truffleMetaAccessProvider.setInlinedCallCount(count);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<TruffleSourceLanguagePosition> createPositionInCompiler(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ClientHandle<TruffleSourceLanguagePosition> positionHandle, int lineNumber, int offsetStart, int offsetEnd, int nodeId) {

        return IsolatedCompileContext.get().hand(new IsolatedTruffleSourceLanguagePosition(positionHandle, lineNumber, offsetStart, offsetEnd, nodeId));
    }
}
