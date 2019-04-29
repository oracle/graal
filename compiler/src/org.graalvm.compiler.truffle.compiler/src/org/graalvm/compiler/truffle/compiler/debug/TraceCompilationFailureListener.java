/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.debug;

import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationBailoutAsFailure;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationFailureAction;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;

/**
 * Traces Truffle compilation failures.
 */
public final class TraceCompilationFailureListener implements TruffleCompilerListener {

    public TraceCompilationFailureListener() {
    }

    /**
     * Determines if a failure is permanent.
     */
    public static boolean isPermanentFailure(boolean bailout, boolean permanentBailout) {
        return !bailout || permanentBailout;
    }

    @Override
    public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
    }

    @Override
    public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
    }

    @Override
    public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
    }

    @Override
    public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
        if (isPermanentFailure(bailout, permanentBailout) || bailoutActionIsPrintOrGreater()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("Reason", reason);
            TruffleCompilerRuntime.getRuntime().logEvent(0, "opt fail", compilable.toString(), properties);
        }
    }

    private static boolean bailoutActionIsPrintOrGreater() {
        OptionValues options = TruffleCompilerOptions.getOptions();
        return CompilationBailoutAsFailure.getValue(options) && CompilationFailureAction.getValue(options).ordinal() >= ExceptionAction.Print.ordinal();
    }

}
