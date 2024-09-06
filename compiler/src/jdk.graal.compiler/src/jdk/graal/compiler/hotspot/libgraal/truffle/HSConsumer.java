/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerAssumptionDependency;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

import java.lang.invoke.MethodHandle;
import java.util.function.Consumer;

import static jdk.graal.compiler.hotspot.libgraal.truffle.BuildTime.getHostMethodHandleOrFail;

final class HSConsumer extends HSIndirectHandle implements Consumer<OptimizedAssumptionDependency> {

    private static final Handles HANDLES = new Handles();

    HSConsumer(Object hsHandle) {
        super(hsHandle);
    }

    @Override
    public void accept(OptimizedAssumptionDependency optimizedDependency) {
        TruffleCompilerAssumptionDependency dependency = (TruffleCompilerAssumptionDependency) optimizedDependency;
        Object compilableHsHandle;
        long installedCode;
        if (dependency == null) {
            compilableHsHandle = null;
            installedCode = 0;
        } else {
            TruffleCompilable ast = dependency.getCompilable();
            if (ast == null) {
                /*
                 * Compilable may be null if the compilation was triggered by a libgraal host
                 * compilation.
                 */
                compilableHsHandle = null;
            } else {
                compilableHsHandle = ((HSTruffleCompilable) dependency.getCompilable()).hsHandle;
            }
            installedCode = HotSpotJVMCIRuntime.runtime().translate(dependency.getInstalledCode());
        }
        try {
            HANDLES.consumeOptimizedAssumptionDependency.invoke(hsHandle, compilableHsHandle, installedCode);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    private static final class Handles {
        final MethodHandle consumeOptimizedAssumptionDependency = getHostMethodHandleOrFail(Id.ConsumeOptimizedAssumptionDependency);
    }
}
