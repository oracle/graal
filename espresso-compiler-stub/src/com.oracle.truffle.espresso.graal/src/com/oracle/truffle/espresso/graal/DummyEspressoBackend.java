/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.graal;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.SuitesProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class DummyEspressoBackend extends Backend {
    DummyEspressoBackend(Providers providers) {
        super(providers);
    }

    @Override
    public SuitesProvider getSuites() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo, Object stub) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        throw GraalError.unimplementedOverride();
    }
}
