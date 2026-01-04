/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc.phases;

import com.oracle.svm.hosted.webimage.codegen.phase.ReconstructionVerificationPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.WebImageLowTier;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.StackifierReconstructionPhase;
import com.oracle.svm.hosted.webimage.wasm.phases.UnorderedIsTruePhase;
import com.oracle.svm.hosted.webimage.wasm.phases.ValueDropPhase;
import com.oracle.svm.hosted.webimage.wasm.phases.WasmStackifierReconstructionPhase;
import com.oracle.svm.hosted.webimage.wasm.phases.WasmSwitchPhase;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ExpandLogicPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;

public class WebImageWasmGCLowTier extends WebImageLowTier {

    @SuppressWarnings("this-escape")
    public WebImageWasmGCLowTier(OptionValues options) {
        super(options);

        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        /*
         * Prepend phases in reverse order because they need to be prepended to all the stackifier
         * phases
         */

        prependPhase(new ValueDropPhase());

        prependPhase(new UnorderedIsTruePhase(canonicalizer));

        prependPhase(new WasmGCSingleThreadedAtomicsPhase());

        prependPhase(new ExpandLogicPhase(canonicalizer));

        prependPhase(new LowTierLoweringPhase(canonicalizer));

        prependPhase(new WasmSwitchPhase(canonicalizer));

        // TODO GR-59392 temporarily disable this because it fails some tests.
        removePhase(ReconstructionVerificationPhase.class);
    }

    @Override
    protected StackifierReconstructionPhase createStackifierPhase() {
        return new WasmStackifierReconstructionPhase();
    }
}
