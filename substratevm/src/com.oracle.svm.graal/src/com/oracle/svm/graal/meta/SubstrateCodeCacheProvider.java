/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.SuppressFBWarnings;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.SubstrateCompiledCode;
import com.oracle.svm.core.graal.meta.SharedCodeCacheProvider;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public class SubstrateCodeCacheProvider extends SharedCodeCacheProvider {

    protected SubstrateCodeCacheProvider(TargetDescription target, RegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    @Override
    @SuppressFBWarnings(value = {"BC_UNCONFIRMED_CAST"}, justification = "We know what we are doing.")
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode predefinedInstalledCode, SpeculationLog log, boolean isDefault) {
        VMError.guarantee(!isDefault);

        SubstrateInstalledCode substrateInstalledCode;
        if (predefinedInstalledCode instanceof SubstrateInstalledCode.Access) {
            substrateInstalledCode = ((SubstrateInstalledCode.Access) predefinedInstalledCode).getSubstrateInstalledCode();
        } else {
            substrateInstalledCode = (SubstrateInstalledCode) predefinedInstalledCode;
        }
        CompilationResult compResult = ((SubstrateCompiledCode) compiledCode).getCompilationResult();
        RuntimeCodeInstaller.install((SharedRuntimeMethod) method, compResult, substrateInstalledCode);
        return predefinedInstalledCode;
    }
}
