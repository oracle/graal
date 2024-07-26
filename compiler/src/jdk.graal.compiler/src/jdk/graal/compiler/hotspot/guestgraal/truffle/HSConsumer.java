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
package jdk.graal.compiler.hotspot.guestgraal.truffle;

import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerAssumptionDependency;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.function.Consumer;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static jdk.graal.compiler.hotspot.guestgraal.truffle.BuildTime.getOrFail;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

final class HSConsumer extends HSIndirectHandle implements Consumer<OptimizedAssumptionDependency> {

    private static MethodHandle consumeOptimizedAssumptionDependency;

    static void initialize(Map<String, MethodHandle> upCallHandles) {
        consumeOptimizedAssumptionDependency = getOrFail(upCallHandles, ConsumeOptimizedAssumptionDependency);
    }

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
            installedCode = runtime().translate(dependency.getInstalledCode());
        }
        try {
            consumeOptimizedAssumptionDependency.invoke(hsHandle, compilableHsHandle, installedCode);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }
}
