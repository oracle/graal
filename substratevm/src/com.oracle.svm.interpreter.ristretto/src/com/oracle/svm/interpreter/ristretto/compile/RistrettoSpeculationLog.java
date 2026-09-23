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

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Ristretto-specific speculation log that keeps the durable method-level failed-speculation state.
 * Each compilation receives a separate {@link CompilationSpeculationLog} that delegates durable
 * failure state here while recording exactly the speculation reasons consumed by that compilation.
 * The per-compilation list lets the code installer reject only code whose own assumptions failed
 * between graph construction and installation, without rejecting later compilations because some
 * unrelated older speculation had already failed.
 * <p>
 * The durable superclass synchronizes collection and lookup of failed speculations; the
 * per-compilation wrapper uses this log's monitor when it records or checks assumptions so the
 * "record assumptions, collect feedback, check assumptions" sequence stays consistent. This lock is
 * taken by Ristretto compiler threads during compilation and install-time validation, not by the
 * normal application execution path.
 */
public final class RistrettoSpeculationLog extends SubstrateSpeculationLog {

    public CompilationSpeculationLog createCompilationLog() {
        return new CompilationSpeculationLog(this);
    }

    public static final class CompilationSpeculationLog implements SpeculationLog {
        private final RistrettoSpeculationLog methodLog;
        private final ArrayList<SpeculationLog.SpeculationReason> speculations = new ArrayList<>();

        private CompilationSpeculationLog(RistrettoSpeculationLog methodLog) {
            this.methodLog = methodLog;
        }

        /**
         * Collects pending deoptimization feedback and checks only the speculation reasons used by
         * this compilation.
         */
        public boolean hasFailedSpeculation() {
            synchronized (methodLog) {
                methodLog.collectFailedSpeculations();
                for (SpeculationLog.SpeculationReason reason : speculations) {
                    if (!methodLog.maySpeculate(reason)) {
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        public void collectFailedSpeculations() {
            methodLog.collectFailedSpeculations();
        }

        @Override
        public boolean maySpeculate(SpeculationReason reason) {
            return methodLog.maySpeculate(reason);
        }

        @Override
        public Speculation speculate(SpeculationReason reason) {
            synchronized (methodLog) {
                speculations.add(reason);
                return methodLog.speculate(reason);
            }
        }

        @Override
        public boolean hasSpeculations() {
            return methodLog.hasSpeculations();
        }

        @Override
        public Speculation lookupSpeculation(JavaConstant constant) {
            return methodLog.lookupSpeculation(constant);
        }
    }
}
