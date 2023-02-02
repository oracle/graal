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

import java.util.function.Supplier;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCodeInstallBridge;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedObjectConstant;
import com.oracle.svm.graal.isolated.IsolatedObjectProxy;
import com.oracle.svm.graal.isolated.IsolatedSpeculationLog;
import com.oracle.svm.truffle.api.SubstrateCompilableTruffleAST;
import com.oracle.truffle.api.Assumption;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

final class IsolatedCompilableTruffleAST extends IsolatedObjectProxy<SubstrateCompilableTruffleAST> implements SubstrateCompilableTruffleAST {

    private String cachedName;
    private IsolatedSpeculationLog cachedSpeculationLog;

    IsolatedCompilableTruffleAST(ClientHandle<SubstrateCompilableTruffleAST> compilable) {
        super(compilable);
    }

    @Override
    public JavaConstant asJavaConstant() {
        return new IsolatedObjectConstant(handle, false);
    }

    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        if (cachedSpeculationLog == null) {
            ClientHandle<SpeculationLog> logHandle = getCompilationSpeculationLog0(IsolatedCompileContext.get().getClient(), handle);
            cachedSpeculationLog = new IsolatedSpeculationLog(logHandle);
        }
        return cachedSpeculationLog;
    }

    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean silent, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        onCompilationFailed0(IsolatedCompileContext.get().getClient(), handle, IsolatedCompileContext.get().hand(serializedException), silent, bailout, permanentBailout, graphTooBig);
    }

    @Override
    public String getName() {
        if (cachedName == null) {
            cachedName = IsolatedCompileContext.get().unhand(getName0(IsolatedCompileContext.get().getClient(), handle));
        }
        return cachedName;
    }

    @Override
    public boolean onInvalidate(Object source, CharSequence reason, boolean wasActive) {
        throw GraalError.shouldNotReachHere("Should not be reachable for SVM.");
    }

    @Override
    public int getNonTrivialNodeCount() {
        return getNonTrivialNodeCount0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public TruffleCallNode[] getCallNodes() {
        CompilerHandle<IsolatedTruffleCallNode[]> nodes = getCallNodes0(IsolatedCompileContext.get().getClient(), handle);
        return IsolatedCompileContext.get().unhand(nodes);
    }

    @Override
    public int getCallCount() {
        return getCallCount0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        final IsolatedCompileContext context = IsolatedCompileContext.get();
        return cancelCompilation0(context.getClient(), handle, context.createStringInClient(reason));
    }

    @Override
    public void dequeueInlined() {
        final IsolatedCompileContext context = IsolatedCompileContext.get();
        dequeueInlined0(context.getClient(), handle);
    }

    @Override
    public boolean isSameOrSplit(CompilableTruffleAST ast) {
        IsolatedCompilableTruffleAST other = (IsolatedCompilableTruffleAST) ast;
        return isSameOrSplit0(IsolatedCompileContext.get().getClient(), handle, other.handle);
    }

    @Override
    public int getKnownCallSiteCount() {
        return getKnownCallSiteCount0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public JavaConstant getNodeRewritingAssumptionConstant() {
        return new IsolatedObjectConstant(getNodeRewritingAssumption0(IsolatedCompileContext.get().getClient(), handle), false);
    }

    @Override
    public JavaConstant getValidRootAssumptionConstant() {
        return new IsolatedObjectConstant(getValidRootAssumption0(IsolatedCompileContext.get().getClient(), handle), false);
    }

    @Override
    public SubstrateInstalledCode createSubstrateInstalledCode() {
        throw VMError.shouldNotReachHere("Must not be called during isolated compilation");
    }

    @Override
    public InstalledCode createPreliminaryInstalledCode() {
        return new IsolatedCodeInstallBridge(handle);
    }

    @Override
    public boolean isTrivial() {
        return isTrivial0(IsolatedCompileContext.get().getClient(), handle);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<SpeculationLog> getCompilationSpeculationLog0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle) {
        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(compilableHandle);
        SpeculationLog log = compilable.getCompilationSpeculationLog();
        return IsolatedCompileClient.get().hand(log);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void onCompilationFailed0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle,
                    CompilerHandle<Supplier<String>> serializedExceptionHandle, boolean silent, boolean bailout, boolean permanentBailout, boolean graphTooBig) {

        Supplier<String> serializedException = () -> {
            ClientHandle<String> resultHandle = getReasonAndStackTrace0(IsolatedCompileClient.get().getCompiler(), serializedExceptionHandle);
            return IsolatedCompileClient.get().unhand(resultHandle);
        };
        IsolatedCompileClient.get().unhand(compilableHandle).onCompilationFailed(serializedException, silent, bailout, permanentBailout, graphTooBig);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<String> getReasonAndStackTrace0(@SuppressWarnings("unused") CompilerIsolateThread compiler, CompilerHandle<Supplier<String>> reasonAndStackTraceHandle) {

        Supplier<String> supplier = IsolatedCompileContext.get().unhand(reasonAndStackTraceHandle);
        return IsolatedCompileContext.get().createStringInClient(supplier.get());
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static CompilerHandle<String> getName0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle) {
        String name = IsolatedCompileClient.get().unhand(compilableHandle).getName();
        return IsolatedCompileClient.get().createStringInCompiler(name);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int getNonTrivialNodeCount0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle) {
        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(compilableHandle);
        return compilable.getNonTrivialNodeCount();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static CompilerHandle<IsolatedTruffleCallNode[]> getCallNodes0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle) {
        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(compilableHandle);
        TruffleCallNode[] nodes = compilable.getCallNodes();
        ClientHandle<?>[] nodeHandles = new ClientHandle<?>[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            nodeHandles[i] = IsolatedCompileClient.get().hand(nodes[i]);
        }
        try (PinnedObject pinnedNodeHandles = PinnedObject.create(nodeHandles)) {
            return getCallNodes1(IsolatedCompileClient.get().getCompiler(), pinnedNodeHandles.addressOfArrayElement(0), nodeHandles.length);
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static CompilerHandle<IsolatedTruffleCallNode[]> getCallNodes1(@SuppressWarnings("unused") CompilerIsolateThread compiler, WordPointer nodeHandleArray, int length) {

        IsolatedTruffleCallNode[] nodes = new IsolatedTruffleCallNode[length];
        for (int i = 0; i < nodes.length; i++) {
            ClientHandle<TruffleCallNode> handle = nodeHandleArray.read(i);
            nodes[i] = new IsolatedTruffleCallNode(handle);
        }
        return IsolatedCompileContext.get().hand(nodes);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int getCallCount0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle) {
        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(compilableHandle);
        return compilable.getCallCount();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean cancelCompilation0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle, ClientHandle<String> reasonHandle) {
        final IsolatedCompileClient isolatedCompileClient = IsolatedCompileClient.get();
        final SubstrateCompilableTruffleAST compilable = isolatedCompileClient.unhand(compilableHandle);
        final String reason = isolatedCompileClient.unhand(reasonHandle);
        return compilable.cancelCompilation(reason);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void dequeueInlined0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> handle) {
        final IsolatedCompileClient isolatedCompileClient = IsolatedCompileClient.get();
        final SubstrateCompilableTruffleAST compilable = isolatedCompileClient.unhand(handle);
        compilable.dequeueInlined();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean isSameOrSplit0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<SubstrateCompilableTruffleAST> compilableHandle, ClientHandle<SubstrateCompilableTruffleAST> otherHandle) {

        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(compilableHandle);
        SubstrateCompilableTruffleAST other = IsolatedCompileClient.get().unhand(otherHandle);
        return compilable.isSameOrSplit(other);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int getKnownCallSiteCount0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> compilableHandle) {
        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(compilableHandle);
        return compilable.getKnownCallSiteCount();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<Assumption> getNodeRewritingAssumption0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> handle) {
        CompilableTruffleAST ast = IsolatedCompileClient.get().unhand(handle);
        JavaConstant assumptionConstant = ast.getNodeRewritingAssumptionConstant();
        Assumption assumption = (Assumption) SubstrateObjectConstant.asObject(assumptionConstant);
        return IsolatedCompileClient.get().hand(assumption);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<Assumption> getValidRootAssumption0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> handle) {
        CompilableTruffleAST ast = IsolatedCompileClient.get().unhand(handle);
        JavaConstant assumptionConstant = ast.getValidRootAssumptionConstant();
        Assumption assumption = (Assumption) SubstrateObjectConstant.asObject(assumptionConstant);
        return IsolatedCompileClient.get().hand(assumption);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean isTrivial0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> handle) {
        SubstrateCompilableTruffleAST compilable = IsolatedCompileClient.get().unhand(handle);
        return compilable.isTrivial();
    }

}
