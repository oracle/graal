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

import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.runtime.JVMCIRuntime;

public final class DummyEspressoGraalJVMCICompiler implements GraalJVMCICompiler {
    private final EspressoGraalRuntime runtime;

    private DummyEspressoGraalJVMCICompiler(JVMCIRuntime jvmciRuntime) {
        runtime = new EspressoGraalRuntime(jvmciRuntime);
    }

    // used by the VM
    public static DummyEspressoGraalJVMCICompiler create(JVMCIRuntime jvmciRuntime) {
        return new DummyEspressoGraalJVMCICompiler(jvmciRuntime);
    }

    @Override
    public GraalRuntime getGraalRuntime() {
        return runtime;
    }

    @Override
    public CompilationRequestResult compileMethod(CompilationRequest request) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean isGCSupported(int gcIdentifier) {
        throw GraalError.unimplementedOverride();
    }
}
