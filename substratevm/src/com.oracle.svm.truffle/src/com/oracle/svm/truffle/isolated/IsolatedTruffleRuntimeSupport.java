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

import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.ImageHeapObjects;
import com.oracle.svm.graal.isolated.ImageHeapRef;
import com.oracle.svm.graal.isolated.IsolatedCodeInstallBridge;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedHandles;
import com.oracle.svm.graal.isolated.IsolatedObjectConstant;
import com.oracle.svm.truffle.api.SubstrateCompilableTruffleAST;
import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.runtime.OptimizedAssumption;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

import jdk.vm.ci.meta.JavaConstant;

public final class IsolatedTruffleRuntimeSupport {
    public static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        ClientHandle<Consumer<OptimizedAssumptionDependency>> consumerHandle;
        if (optimizedAssumptionConstant instanceof IsolatedObjectConstant) {
            @SuppressWarnings("unchecked")
            ClientHandle<OptimizedAssumption> assumptionHandle = (ClientHandle<OptimizedAssumption>) ((IsolatedObjectConstant) optimizedAssumptionConstant).getHandle();
            consumerHandle = registerOptimizedAssumptionDependency0(IsolatedCompileContext.get().getClient(), assumptionHandle);
        } else {
            /*
             * Assumptions can be image heap objects referenced by direct object constants in
             * encoded graphs, so translate those to the object in the client isolate. This is in
             * line with what we do elsewhere such as in IsolateAwareConstantReflectionProvider. As
             * in those cases, it is crucial that such assumption constants are accessed only via
             * the appropriate isolate-aware providers during compilation.
             */
            ImageHeapRef<OptimizedAssumption> assumptionRef = ImageHeapObjects.ref(SubstrateObjectConstant.asObject(OptimizedAssumption.class, optimizedAssumptionConstant));
            consumerHandle = registerImageHeapOptimizedAssumptionDependency0(IsolatedCompileContext.get().getClient(), assumptionRef);
        }
        if (consumerHandle.equal(IsolatedHandles.nullHandle())) {
            return null;
        }
        return new Consumer<>() {
            @Override
            public void accept(OptimizedAssumptionDependency codeInstallBridge) {
                ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle = IsolatedHandles.nullHandle();
                if (codeInstallBridge != null) {
                    installedCodeHandle = ((IsolatedCodeInstallBridge) codeInstallBridge).getSubstrateInstalledCodeHandle();
                }

                @SuppressWarnings("unchecked")
                ClientHandle<? extends OptimizedAssumptionDependency> dependencyAccessHandle = (ClientHandle<? extends OptimizedAssumptionDependency>) installedCodeHandle;

                notifyAssumption0(IsolatedCompileContext.get().getClient(), consumerHandle, dependencyAccessHandle);
            }
        };
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<Consumer<OptimizedAssumptionDependency>> registerOptimizedAssumptionDependency0(
                    @SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<OptimizedAssumption> assumptionHandle) {

        OptimizedAssumption assumption = IsolatedCompileClient.get().unhand(assumptionHandle);
        return registerOptimizedAssumptionDependency1(assumption);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<Consumer<OptimizedAssumptionDependency>> registerImageHeapOptimizedAssumptionDependency0(
                    @SuppressWarnings("unused") ClientIsolateThread client, ImageHeapRef<OptimizedAssumption> assumptionRef) {

        OptimizedAssumption assumption = ImageHeapObjects.deref(assumptionRef);
        return registerOptimizedAssumptionDependency1(assumption);
    }

    private static ClientHandle<Consumer<OptimizedAssumptionDependency>> registerOptimizedAssumptionDependency1(OptimizedAssumption assumption) {
        Consumer<OptimizedAssumptionDependency> observer = assumption.registerDependency();
        return IsolatedCompileClient.get().hand(observer);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
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

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<OptimizedCallTarget> getCallTargetForCallNode0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<OptimizedDirectCallNode> callNode) {

        OptimizedDirectCallNode node = IsolatedCompileClient.get().unhand(callNode);
        OptimizedCallTarget callTarget = node.getCallTarget();
        return IsolatedCompileClient.get().hand(callTarget);
    }

    public static TruffleCompilable asCompilableTruffleAST(JavaConstant constant) {
        @SuppressWarnings("unchecked")
        ClientHandle<SubstrateCompilableTruffleAST> handle = (ClientHandle<SubstrateCompilableTruffleAST>) ((IsolatedObjectConstant) constant).getHandle();
        return new IsolatedCompilableTruffleAST(handle);
    }

    public static boolean tryLog(String loggerId, TruffleCompilable compilable, String message) {
        if (compilable instanceof IsolatedCompilableTruffleAST) {
            ClientHandle<String> id = IsolatedCompileContext.get().createStringInClient(loggerId);
            ClientHandle<SubstrateCompilableTruffleAST> handle = ((IsolatedCompilableTruffleAST) compilable).getHandle();
            ClientHandle<String> msg = IsolatedCompileContext.get().createStringInClient(message);
            log0(IsolatedCompileContext.get().getClient(), id, handle, msg);
            return true;
        }
        return false;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void log0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<String> id, ClientHandle<SubstrateCompilableTruffleAST> ast, ClientHandle<String> msg) {

        SubstrateTruffleRuntime runtime = (SubstrateTruffleRuntime) SubstrateTruffleRuntime.getRuntime();
        String loggerId = IsolatedCompileClient.get().unhand(id);
        OptimizedCallTarget callTarget = (OptimizedCallTarget) IsolatedCompileClient.get().unhand(ast);
        String message = IsolatedCompileClient.get().unhand(msg);
        runtime.log(loggerId, callTarget, message);
    }

    public static TriState tryIsSuppressedFailure(TruffleCompilable compilable, Supplier<String> serializedException) {
        if (compilable instanceof IsolatedCompilableTruffleAST) {
            ClientHandle<SubstrateCompilableTruffleAST> handle = ((IsolatedCompilableTruffleAST) compilable).getHandle();
            return isSuppressedFailure0(IsolatedCompileContext.get().getClient(), handle, IsolatedCompileContext.get().hand(serializedException)) ? TriState.TRUE : TriState.FALSE;
        }
        return TriState.UNDEFINED;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean isSuppressedFailure0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<SubstrateCompilableTruffleAST> ast,
                    CompilerHandle<Supplier<String>> serializedExceptionHandle) {
        Supplier<String> serializedException = new IsolatedStringSupplier(serializedExceptionHandle);
        SubstrateTruffleRuntime runtime = (SubstrateTruffleRuntime) SubstrateTruffleRuntime.getRuntime();
        return runtime.isSuppressedFailure(IsolatedCompileClient.get().unhand(ast), serializedException);
    }

    private IsolatedTruffleRuntimeSupport() {
    }
}
