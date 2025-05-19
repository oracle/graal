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

import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.vm.ci.code.Architecture;

public abstract class SubstrateVectorArchitectureFactory {

    /**
     * The single target vector architecture used at image build time. We build multiple
     * LoweringProvider instances during an image build (e.g., for analysis, for hosted
     * compilations, for runtime compilations). Each of these instances should share the same vector
     * architecture. Access this via {@link #getSingletonVectorArchitecture}.
     */
    private volatile VectorArchitecture vectorArchitecture = null;

    @FunctionalInterface
    public interface VectorArchitectureFactory<VectorArch extends VectorArchitecture, Arch extends Architecture> {
        VectorArch create(Arch arch, boolean vectorArchEnabled, int referenceSize, boolean haveCompressedReferences, int alignment);
    }

    /**
     * Returns the singleton {@link VectorArchitecture} instance to be used at image build time. The
     * first time this is called, it builds an instance and returns it. On subsequent calls, ensures
     * that the given arguments are compatible with previous calls, and returns the previously built
     * singleton instance.
     */
    protected <VectorArch extends VectorArchitecture, Arch extends Architecture> VectorArchitecture getSingletonVectorArchitecture(VectorArchitectureFactory<VectorArch, Arch> factory,
                    Arch arch, boolean vectorArchEnabled, int referenceSize, boolean haveCompressedReferences, int alignment) {
        VectorArch newVectorArchitecture = factory.create(arch, vectorArchEnabled, referenceSize, haveCompressedReferences, alignment);
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
}
