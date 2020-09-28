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
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
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
import com.oracle.truffle.api.Assumption;

import jdk.vm.ci.meta.JavaConstant;

abstract class IsolatedTruffleInlining<T extends TruffleInliningPlan> extends IsolatedObjectProxy<T> implements TruffleInliningPlan {
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
    public Decision findDecision(JavaConstant callNodeConstant) {
        ClientHandle<?> callNodeConstantHandle = ((IsolatedObjectConstant) callNodeConstant).getHandle();
        ClientHandle<Decision> decision = findDecision0(IsolatedCompileContext.get().getClient(), handle, callNodeConstantHandle);
        return decision.notEqual(IsolatedHandles.nullHandle()) ? new IsolatedDecision(decision) : null;
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
    public void dequeueTargets() {
        dequeueTargets0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public void setCallCount(int count) {
        setCallCount0(IsolatedCompileContext.get().getClient(), handle, count);
    }

    @Override
    public int countCalls() {
        return countCalls0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public void setInlinedCallCount(int count) {
        setInlinedCallCount0(IsolatedCompileContext.get().getClient(), handle, count);
    }

    @Override
    public int countInlinedCalls() {
        return countInlinedCalls0(IsolatedCompileContext.get().getClient(), handle);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<TruffleCallNode> findCallNode0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleInliningPlan> inliningHandle, ClientHandle<?> callNodeConstantHandle) {

        TruffleInliningPlan inlining = IsolatedCompileClient.get().unhand(inliningHandle);
        JavaConstant callNodeConstant = SubstrateObjectConstant.forObject(IsolatedCompileClient.get().unhand(callNodeConstantHandle));
        TruffleCallNode callNode = inlining.findCallNode(callNodeConstant);
        return IsolatedCompileClient.get().hand(callNode);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<Decision> findDecision0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleInliningPlan> inliningHandle, ClientHandle<?> callNodeConstantHandle) {

        TruffleInliningPlan inlining = IsolatedCompileClient.get().unhand(inliningHandle);
        JavaConstant callNodeConstant = SubstrateObjectConstant.forObject(IsolatedCompileClient.get().unhand(callNodeConstantHandle));
        Decision decision = inlining.findDecision(callNodeConstant);
        return IsolatedCompileClient.get().hand(decision);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<TruffleSourceLanguagePosition> getPosition0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleInliningPlan> inliningHandle, ClientHandle<?> callNodeConstantHandle) {

        TruffleInliningPlan inlining = IsolatedCompileClient.get().unhand(inliningHandle);
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
    private static void dequeueTargets0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> providerHandle) {
        TruffleMetaAccessProvider truffleMetaAccessProvider = IsolatedCompileClient.get().unhand(providerHandle);
        truffleMetaAccessProvider.dequeueTargets();
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
    private static int countCalls0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> handle) {
        TruffleMetaAccessProvider truffleMetaAccessProvider = IsolatedCompileClient.get().unhand(handle);
        return truffleMetaAccessProvider.countCalls();
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
    private static int countInlinedCalls0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<? extends TruffleMetaAccessProvider> handle) {
        TruffleMetaAccessProvider truffleMetaAccessProvider = IsolatedCompileClient.get().unhand(handle);
        return truffleMetaAccessProvider.countInlinedCalls();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<TruffleSourceLanguagePosition> createPositionInCompiler(@SuppressWarnings("unused") CompilerIsolateThread compiler,
                    ClientHandle<TruffleSourceLanguagePosition> positionHandle, int lineNumber, int offsetStart, int offsetEnd, int nodeId) {

        return IsolatedCompileContext.get().hand(new IsolatedTruffleSourceLanguagePosition(positionHandle, lineNumber, offsetStart, offsetEnd, nodeId));
    }
}

final class IsolatedTruffleInliningPlan extends IsolatedTruffleInlining<TruffleInliningPlan> {
    IsolatedTruffleInliningPlan(ClientHandle<TruffleInliningPlan> handle) {
        super(handle);
    }
}

final class IsolatedDecision extends IsolatedTruffleInlining<TruffleInliningPlan.Decision> implements TruffleInliningPlan.Decision {
    IsolatedDecision(ClientHandle<Decision> handle) {
        super(handle);
    }

    @Override
    public boolean shouldInline() {
        return shouldInline0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean isTargetStable() {
        return isTargetStable0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public String getTargetName() {
        CompilerHandle<String> nameHandle = getTargetName0(IsolatedCompileContext.get().getClient(), handle);
        return IsolatedCompileContext.get().unhand(nameHandle);
    }

    @Override
    public JavaConstant getNodeRewritingAssumption() {
        ClientHandle<Assumption> assumptionHandle = getNodeRewritingAssumption0(IsolatedCompileContext.get().getClient(), handle);
        return new IsolatedObjectConstant(assumptionHandle, false);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean shouldInline0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<Decision> decisionHandle) {
        return IsolatedCompileClient.get().unhand(decisionHandle).shouldInline();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean isTargetStable0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<Decision> decisionHandle) {
        return IsolatedCompileClient.get().unhand(decisionHandle).shouldInline();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<String> getTargetName0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<Decision> decisionHandle) {
        String name = IsolatedCompileClient.get().unhand(decisionHandle).getTargetName();
        return IsolatedCompileClient.get().createStringInCompiler(name);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<Assumption> getNodeRewritingAssumption0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<Decision> decisionHandle) {
        Decision decision = IsolatedCompileClient.get().unhand(decisionHandle);
        JavaConstant assumptionConstant = decision.getNodeRewritingAssumption();
        Assumption assumption = KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(assumptionConstant), Assumption.class);
        return IsolatedCompileClient.get().hand(assumption);
    }
}
