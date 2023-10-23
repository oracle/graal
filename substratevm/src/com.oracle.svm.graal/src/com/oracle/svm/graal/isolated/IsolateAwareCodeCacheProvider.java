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
package com.oracle.svm.graal.isolated;

import jdk.graal.compiler.code.CompilationResult;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.SubstrateCompiledCode;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.meta.SubstrateCodeCacheProvider;
import com.oracle.svm.graal.meta.SubstrateMethod;

import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public final class IsolateAwareCodeCacheProvider extends SubstrateCodeCacheProvider {
    public IsolateAwareCodeCacheProvider(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode predefinedInstalledCode, SpeculationLog log, boolean isDefault) {
        if (!SubstrateOptions.shouldCompileInIsolates()) {
            return super.installCode(method, compiledCode, predefinedInstalledCode, log, isDefault);
        }

        VMError.guarantee(!isDefault);

        IsolatedCodeInstallBridge installBridge = (IsolatedCodeInstallBridge) predefinedInstalledCode;
        ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle = installBridge.getSubstrateInstalledCodeFactoryHandle();

        CompilationResult result = ((SubstrateCompiledCode) compiledCode).getCompilationResult();
        ClientHandle<SubstrateInstalledCode> installedCode;
        if (method instanceof IsolatedCompilationMethod<?>) {
            IsolatedCompilationMethod<?> compilerMethod = (IsolatedCompilationMethod<?>) method;
            ClientHandle<? extends SharedRuntimeMethod> clientMethodHandle = compilerMethod.getMirror();
            assert !clientMethodHandle.equal(IsolatedHandles.nullHandle()) : "Client method must not be null";
            installedCode = IsolatedRuntimeCodeInstaller.installInClientIsolate(compilerMethod, clientMethodHandle, result, installedCodeFactoryHandle);
        } else {
            ImageHeapRef<SubstrateMethod> methodRef = ImageHeapObjects.ref((SubstrateMethod) method);
            installedCode = IsolatedRuntimeCodeInstaller.installInClientIsolate(methodRef, result, installedCodeFactoryHandle);
        }

        installBridge.setSubstrateInstalledCodeHandle(installedCode);
        return installBridge;
    }
}
