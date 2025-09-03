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

package com.oracle.svm.hosted.webimage.wasm.phases;

import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.DisableFrameStateVerificationPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.RemoveMonitorPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.RemoveUnusedVPPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.WebImageHighTier;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.phases.RemoveInterceptJSInvokePhase;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

public class WebImageWasmLMHighTier extends WebImageHighTier {

    @SuppressWarnings("this-escape")
    public WebImageWasmLMHighTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create().copyWithCustomSimplification(new WasmSimplification());

        appendPhase(new RemoveInterceptJSInvokePhase());

        if (WebImageOptions.UsePEA.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, canonicalizer, options));
        }

        appendPhase(new DisableFrameStateVerificationPhase());

        appendPhase(new RemoveUnusedVPPhase());

        appendPhase(canonicalizer);

        appendPhase(new RemoveMonitorPhase());

        appendPhase(canonicalizer);

        appendPhase(new DeadStoreRemovalPhase());
        appendPhase(new RemoveUnwindPhase());

        appendPhase(canonicalizer);

        appendPhase(new HighTierLoweringPhase(canonicalizer));
    }

}
