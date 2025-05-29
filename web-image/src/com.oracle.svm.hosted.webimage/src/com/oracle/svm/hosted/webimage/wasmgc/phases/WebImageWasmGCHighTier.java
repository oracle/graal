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

import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.DisableFrameStateVerificationPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.RemoveAllocateWithExceptionPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.RemoveMonitorPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.RemoveUnusedVPPhase;
import com.oracle.svm.hosted.webimage.codegen.phase.WebImageHighTier;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.phases.MaterializeAllocationsPhase;
import com.oracle.svm.hosted.webimage.phases.RemoveInterceptJSInvokePhase;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

public class WebImageWasmGCHighTier extends WebImageHighTier {

    @SuppressWarnings("this-escape")
    public WebImageWasmGCHighTier(OptionValues options) {
        appendPhase(new RemoveInterceptJSInvokePhase());

        appendPhase(CanonicalizerPhase.create());

        if (WebImageOptions.UsePEA.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, CanonicalizerPhase.create(), options));
        }

        appendPhase(new MaterializeAllocationsPhase());

        appendPhase(new DisableFrameStateVerificationPhase());

        appendPhase(new RemoveUnusedVPPhase());

        appendPhase(CanonicalizerPhase.create());

        appendPhase(new RemoveMonitorPhase());

        appendPhase(CanonicalizerPhase.create());

        appendPhase(new DeadStoreRemovalPhase());
        appendPhase(new RemoveUnwindPhase());
        appendPhase(new RemoveAllocateWithExceptionPhase());

        appendPhase(CanonicalizerPhase.create());

        appendPhase(new HighTierLoweringPhase(CanonicalizerPhase.create()));
    }

}
