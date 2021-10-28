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

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.runtime.OptimizedAssumption;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCodeInstallBridge;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedHandles;
import com.oracle.svm.graal.isolated.IsolatedObjectConstant;
import com.oracle.svm.truffle.api.SubstrateCompilableTruffleAST;
import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;
import com.oracle.truffle.api.utilities.TriState;

import jdk.vm.ci.meta.JavaConstant;

public final class IsolatedTruffleRuntimeSupport {
    public static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        @SuppressWarnings("unchecked")
        ClientHandle<OptimizedAssumption> assumptionHandle = (ClientHandle<OptimizedAssumption>) ((IsolatedObjectConstant) optimizedAssumptionConstant).getHandle();
        ClientHandle<Consumer<OptimizedAssumptionDependency>> consumerHandle = registerOptimizedAssumptionDependency0(IsolatedCompileContext.get().getClient(), assumptionHandle);
        if (consumerHandle.equal(IsolatedHandles.nullHandle())) {
            return null;
        }
        return codeInstallBridge -> {
            ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle = IsolatedHandles.nullHandle();
            if (codeInstallBridge != null) {
                installedCodeHandle = ((IsolatedCodeInstallBridge) codeInstallBridge).getSubstrateInstalledCodeHandle();
            }

            @SuppressWarnings("unchecked")
            ClientHandle<? extends OptimizedAssumptionDependency> dependencyAccessHandle = (ClientHandle<? extends OptimizedAssumptionDependency>) installedCodeHandle;

            notifyAssumption0(IsolatedCompileContext.get().getClient(), consumerHandle, dependencyAccessHandle);
        };
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<Consumer<OptimizedAssumptionDependency>> registerOptimizedAssumptionDependency0(
                    @SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<OptimizedAssumption> assumptionHandle) {

        OptimizedAssumption assumption = IsolatedCompileClient.get().unhand(assumptionHandle);
        Consumer<OptimizedAssumptionDependency> observer = assumption.registerDependency();
        return IsolatedCompileClient.get().hand(observer);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void notifyAssumption0(@SuppressWarnings("unused") ClientIsolateThread client,
                    ClientHandle<Consumer<OptimizedAssumptionDependency>> consumerHandle,
                    ClientHandle<? extends OptimizedAssumptionDependency> dependencyHandle) {

        OptimizedAssumptionDependency dependency = null;
        if (dependencyHandle.notEqual(IsolatedHandles.nullHandle())) {
            dependency = IsolatedCompileClient.get().unhand(dependencyHandle);
        }
        IsolatedCompileClient.get().unhand(consumerHandle).accept(dependency);
    }

    public static JavaConstant getCallTargetForCallNode(JavaConstant callNodeConstant) {
        @SuppressWarnings("unchecked")
        ClientHandle<OptimizedDirectCallNode> callNodeHandle = (ClientHandle<OptimizedDirectCallNode>) ((IsolatedObjectConstant) callNodeConstant).getHandle();
        return new IsolatedObjectConstant(getCallTargetForCallNode0(IsolatedCompileContext.get().getClient(), callNodeHandle), false);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<OptimizedCallTarget> getCallTargetForCallNode0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<OptimizedDirectCallNode> callNode) {

        OptimizedDirectCallNode node = IsolatedCompileClient.get().unhand(callNode);
        OptimizedCallTarget callTarget = node.getCallTarget();
        return IsolatedCompileClient.get().hand(callTarget);
    }

    public static CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        @SuppressWarnings("unchecked")
        ClientHandle<SubstrateCompilableTruffleAST> handle = (ClientHandle<SubstrateCompilableTruffleAST>) ((IsolatedObjectConstant) constant).getHandle();
        return new IsolatedCompilableTruffleAST(handle);
    }

    public static boolean tryLog(String loggerId, CompilableTruffleAST compilable, String message) {
        if (compilable instanceof IsolatedCompilableTruffleAST) {
            ClientHandle<String> id = IsolatedCompileContext.get().createStringInClient(loggerId);
            ClientHandle<SubstrateCompilableTruffleAST> handle = ((IsolatedCompilableTruffleAST) compilable).getHandle();
            ClientHandle<String> msg = IsolatedCompileContext.get().createStringInClient(message);
            log0(IsolatedCompileContext.get().getClient(), id, handle, msg);
            return true;
        }
        return false;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void log0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<String> id, ClientHandle<SubstrateCompilableTruffleAST> ast, ClientHandle<String> msg) {

        SubstrateTruffleRuntime runtime = (SubstrateTruffleRuntime) SubstrateTruffleRuntime.getRuntime();
        String loggerId = IsolatedCompileClient.get().unhand(id);
        OptimizedCallTarget callTarget = (OptimizedCallTarget) IsolatedCompileClient.get().unhand(ast);
        String message = IsolatedCompileClient.get().unhand(msg);
        runtime.log(loggerId, callTarget, message);
    }

    public static TriState tryIsSuppressedFailure(CompilableTruffleAST compilable, Supplier<String> serializedException) {
        if (compilable instanceof IsolatedCompilableTruffleAST) {
            ClientHandle<SubstrateCompilableTruffleAST> handle = ((IsolatedCompilableTruffleAST) compilable).getHandle();
            return isSuppressedFailure0(IsolatedCompileContext.get().getClient(), handle, IsolatedCompileContext.get().hand(serializedException)) ? TriState.TRUE : TriState.FALSE;
        }
        return TriState.UNDEFINED;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean isSuppressedFailure0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> ast,
                    CompilerHandle<Supplier<String>> serializedExceptionHandle) {
        Supplier<String> serializedException = () -> {
            ClientHandle<String> resultHandle = getReasonAndStackTrace0(IsolatedCompileClient.get().getCompiler(), serializedExceptionHandle);
            return IsolatedCompileClient.get().unhand(resultHandle);
        };
        SubstrateTruffleRuntime runtime = (SubstrateTruffleRuntime) SubstrateTruffleRuntime.getRuntime();
        return runtime.isSuppressedFailure(IsolatedCompileClient.get().unhand(ast), serializedException);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<String> getReasonAndStackTrace0(@SuppressWarnings("unused") CompilerIsolateThread compiler, CompilerHandle<Supplier<String>> reasonAndStackTraceHandle) {
        Supplier<String> supplier = IsolatedCompileContext.get().unhand(reasonAndStackTraceHandle);
        return IsolatedCompileContext.get().createStringInClient(supplier.get());
    }

    private IsolatedTruffleRuntimeSupport() {
    }
}
