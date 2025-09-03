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

package com.oracle.svm.hosted.webimage.codegen.phase;

import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.StackifierReconstructionPhase;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

import jdk.graal.compiler.core.phases.BaseTier;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.LowTierContext;

public class WebImageLowTier extends BaseTier<LowTierContext> {

    @SuppressWarnings("this-escape")
    public WebImageLowTier(OptionValues options) {
        appendPhase(new ExpandUnwindPhase());
        appendPhase(new ExpandReturnPhase());

        appendPhase(new RemoveProxyNodePhase());
        appendPhase(new RemoveUnusedExitsPhase());

        /*
         * The phase OutlineRuntimeChecksPhase needs to be after the normalization above to detect
         * all patterns.
         *
         * It has to be before the stackifier algorithm because it removes if-nodes.
         */
        if (WebImageOptions.OutlineRuntimeChecks.getValue(options)) {
            appendPhase(new OutlineRuntimeChecksPhase());
        }

        appendPhase(createStackifierPhase());

        if (WebImageOptions.DebugOptions.VerificationPhases.getValue(options)) {
            appendPhase(new ReconstructionVerificationPhase());
        }
    }

    protected StackifierReconstructionPhase createStackifierPhase() {
        return new StackifierReconstructionPhase();
    }
}
