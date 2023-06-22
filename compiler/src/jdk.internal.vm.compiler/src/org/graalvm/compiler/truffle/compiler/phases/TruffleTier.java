/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.core.phases.BaseTier;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.PostPartialEvaluationSuite;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleTierContext;
import org.graalvm.compiler.truffle.compiler.phases.inlining.AgnosticInliningPhase;

public class TruffleTier extends BaseTier<TruffleTierContext> {

    @SuppressWarnings("this-escape")
    public TruffleTier(OptionValues options, PartialEvaluator partialEvaluator, InstrumentationSuite instrumentationSuite, PostPartialEvaluationSuite postPartialEvaluationSuite) {
        appendPhase(new AgnosticInliningPhase(partialEvaluator, postPartialEvaluationSuite));
        appendPhase(instrumentationSuite);
        appendPhase(new ReportPerformanceWarningsPhase());
        appendPhase(new VerifyFrameDoesNotEscapePhase());
        appendPhase(new NeverPartOfCompilationPhase());
        appendPhase(new MaterializeFramesPhase());
        appendPhase(new SetIdentityForValueTypesPhase());
        if (!TruffleCompilerOptions.InlineAcrossTruffleBoundary.getValue(options)) {
            appendPhase(new InliningAcrossTruffleBoundaryPhase());
        }
    }
}
