/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import static jdk.graal.compiler.vector.replacements.VectorIntrinsics.Options.Vectorization;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.config.ObjectLayout;
import com.oracle.svm.shared.option.HostedOptionValues;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.aarch64.VectorAArch64;
import jdk.graal.compiler.vector.architecture.amd64.VectorAMD64;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.riscv64.RISCV64;

public abstract class SubstrateVectorArchitectureFactory {

    /**
     * The single target vector architecture used at image build time. We build multiple
     * LoweringProvider instances during an image build (e.g., for analysis, for hosted
     * compilations, for runtime compilations). Each of these instances should share the same vector
     * architecture. Access this via {@link #getSingletonVectorArchitecture}.
     */
    private volatile VectorArchitecture vectorArchitecture = null;

    /**
     * Returns the singleton {@link VectorArchitecture} instance to be used at image build time. The
     * first time this is called, it builds an instance and returns it. On subsequent calls, ensures
     * that the given arguments are compatible with previous calls, and returns the previously built
     * singleton instance. A target without a vector architecture requires Graal vectorization to be
     * disabled. In that case, this method returns {@code null}.
     */
    protected VectorArchitecture getSingletonVectorArchitecture(Architecture arch, BarrierSet barrierSet) {
        VectorArchitecture newVectorArchitecture = createVectorArchitecture(arch, barrierSet);
        if (newVectorArchitecture == null) {
            return null;
        }
        if (vectorArchitecture == null) {
            synchronized (SubstrateVectorArchitectureFactory.class) {
                if (vectorArchitecture == null) {
                    vectorArchitecture = newVectorArchitecture;
                }
            }
        }
        VMError.guarantee(vectorArchitecture.equals(newVectorArchitecture), "must not build incompatible VectorArchitecture instances; old: %s, new: %s", vectorArchitecture, newVectorArchitecture);
        return vectorArchitecture;
    }

    private static VectorArchitecture createVectorArchitecture(Architecture arch, BarrierSet barrierSet) {
        ObjectLayout layout = ObjectLayout.singleton();
        /*
         * Graal's vector optimizations still need a vector architecture when SIMD is disabled.
         * The architecture then reports scalar widths for memory and arithmetic operations.
         */
        boolean enabled = !SubstrateOptions.useLLVMBackend();
        boolean objectVectorization = barrierSet.supportsObjectArrayRangeBarriers();
        if (arch instanceof AMD64 amd64) {
            return new VectorAMD64(amd64, enabled, layout.getReferenceSize(), true, layout.getAlignment(), objectVectorization);
        } else if (arch instanceof AArch64 aarch64) {
            return new VectorAArch64(aarch64, enabled, layout.getReferenceSize(), true, layout.getAlignment(), objectVectorization);
        } else if (arch instanceof RISCV64) {
            UserError.guarantee(!Vectorization.getValue(HostedOptionValues.singleton().get()),
                            "Graal vectorization is not supported for RISC-V. Disable it with %s.", SubstrateOptionsParser.commandArgument(Vectorization, "-"));
            return null;
        } else {
            throw VMError.shouldNotReachHere("Unsupported vector architecture: " + arch);
        }
    }
}
