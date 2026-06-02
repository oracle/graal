/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import java.util.ArrayList;

import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.SpeculationLog;

/**
 * Ristretto-specific speculation log that keeps the durable method-level failed-speculation state
 * from {@link SubstrateSpeculationLog} and, while one compilation is active, also records exactly
 * the speculation reasons consumed by that compilation. The active-compilation list lets the code
 * installer reject only code whose own assumptions failed between graph construction and
 * installation, without rejecting later compilations because some unrelated older speculation had
 * already failed.
 * <p>
 * The methods that read or mutate {@link #currentCompilationSpeculations} are synchronized because
 * a compiler thread can be recording the assumptions for one compile while deoptimization feedback
 * is concurrently appended to the superclass log. The superclass already synchronizes collection and
 * lookup of failed speculations; using the same monitor here keeps the "record assumptions, collect
 * feedback, check assumptions" sequence consistent. This lock is taken by Ristretto compiler
 * threads during compilation and install-time validation, not by the normal application execution
 * path.
 */
public final class RistrettoSpeculationLog extends SubstrateSpeculationLog {
    private ArrayList<SpeculationLog.SpeculationReason> currentCompilationSpeculations;

    public synchronized void beginCompilationSpeculationRecording() {
        VMError.guarantee(currentCompilationSpeculations == null, "must not nest Ristretto speculation recording");
        currentCompilationSpeculations = new ArrayList<>();
    }

    public synchronized void endCompilationSpeculationRecording() {
        currentCompilationSpeculations = null;
    }

    /**
     * Collects pending deoptimization feedback and checks only the speculation reasons used by the
     * currently recorded compilation.
     */
    public synchronized boolean hasFailedCurrentCompilationSpeculation() {
        collectFailedSpeculations();
        if (currentCompilationSpeculations == null) {
            return false;
        }
        for (SpeculationLog.SpeculationReason reason : currentCompilationSpeculations) {
            if (!maySpeculate(reason)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized SpeculationLog.Speculation speculate(SpeculationLog.SpeculationReason reason) {
        if (currentCompilationSpeculations != null) {
            currentCompilationSpeculations.add(reason);
        }
        return super.speculate(reason);
    }
}
