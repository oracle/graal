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
package com.oracle.svm.interpreter.ristretto.profile;

import java.util.concurrent.Callable;

import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;

public class RistrettoCompilationRequest implements Comparable<RistrettoCompilationRequest>, Callable<InstalledCode> {
    /**
     * Default priority for any graal top tier compilation.
     */
    public static final int DEFAULT_TOP_TIER_COMPILATION_PRIORITY = 100;

    /**
     * Default priority for OSR compilations. OSR requests come from already-hot loops, so they should
     * run ahead of ordinary invocation-triggered top-tier requests in the compilation queue.
     */
    public static final int DEFAULT_OSR_COMPILATION_PRIORITY = 50;

    /**
     * Ristretto method whose bytecodes or OSR entry graph will be compiled.
     */
    private final RistrettoMethod rMethod;

    /**
     * Queue ordering key; lower values are consumed first by the compilation manager.
     */
    private final int priority;

    /**
     * Entry BCI for this compilation.
     *
     * {@link RistrettoUtils#INVOCATION_ENTRY_BCI} denotes an ordinary invocation compile. Any other
     * value denotes an OSR compile that parses from that bytecode index and installs code in the
     * per-backedge OSR state for the same BCI.
     */
    private final int entryBCI;

    /**
     * OSR request id that owns this compilation callback, or
     * {@link RistrettoMethod#NO_OSR_COMPILATION_REQUEST} for invocation-entry compiles and tests that
     * only inspect queue ordering.
     */
    private final int osrCompilationRequestId;

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority) {
        this(rMethod, priority, RistrettoUtils.INVOCATION_ENTRY_BCI, RistrettoMethod.NO_OSR_COMPILATION_REQUEST);
    }

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority, int entryBCI) {
        this(rMethod, priority, entryBCI, RistrettoMethod.NO_OSR_COMPILATION_REQUEST);
    }

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority, int entryBCI, int osrCompilationRequestId) {
        this.rMethod = rMethod;
        this.priority = priority;
        this.entryBCI = entryBCI;
        this.osrCompilationRequestId = osrCompilationRequestId;
    }

    @Override
    public int compareTo(RistrettoCompilationRequest o) {
        return Integer.compare(priority, o.priority);
    }

    @Override
    public InstalledCode call() throws Exception {
        try {
            SubstrateInstalledCodeImpl code = compileAndInstall();
            if (code == null) {
                onCompilationFailure();
                return null;
            }
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Finished compiling %s%n", rMethod);

            /*
             * Installing a reference to installed code in ristretto method to have the same
             * lifecycle as InterpreterMethod->RistrettoMethod->code, so the root pointer is only
             * dropped when a class is unloaded and the interpreter jvmci objects are collected.
             */
            boolean installCode = RistrettoCompilationManager.TestingBackdoor.installCode();
            if (isOSR()) {
                rMethod.onOSRCompilationSuccess(entryBCI, osrCompilationRequestId, code, installCode);
            } else {
                rMethod.onCompilationSuccess(code, installCode);
            }
            return code;
        } catch (BailoutException e) {
            if (!e.isPermanent()) {
                onCompilationFailure();
                throw e;
            }
            onPermanentBailout();
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilation, "[Ristretto Compiler]Permanent bailout compiling %s: %s%n", this, e.getMessage());
            return null;
        } catch (Throwable t) {
            onCompilationFailure();
            throw t;
        }
    }

    protected SubstrateInstalledCodeImpl compileAndInstall() {
        return RistrettoUtils.compileAndInstall(rMethod, entryBCI);
    }

    @Override
    public String toString() {
        return "CompilationRequest <" + rMethod + ", priority=" + priority + ", entryBCI=" + entryBCI + ">";
    }

    public int getPriority() {
        return priority;
    }

    public RistrettoMethod getRMethod() {
        return rMethod;
    }

    public boolean isOSR() {
        return entryBCI != RistrettoUtils.INVOCATION_ENTRY_BCI;
    }

    /**
     * Records a Graal bailout that declared this request non-retryable.
     */
    private void onPermanentBailout() {
        if (isOSR()) {
            rMethod.onOSRPermanentCompilationFailure(entryBCI, osrCompilationRequestId);
        } else {
            rMethod.onInvocationEntryPermanentBailout();
        }
    }

    /**
     * Records a retryable compilation failure for this request.
     */
    private void onCompilationFailure() {
        if (isOSR()) {
            rMethod.onOSRCompilationFailure(entryBCI, osrCompilationRequestId);
        } else {
            rMethod.onCompilationFailure();
        }
    }
}
