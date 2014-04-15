/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.hotspot;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;
import static com.oracle.graal.truffle.OptimizedCallTargetLog.*;

import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Call target for running truffle on a standard VM (and not in SubstrateVM).
 */
public final class HotSpotOptimizedCallTarget extends OptimizedCallTarget {

    protected final TruffleCompiler compiler;
    private Future<InstalledCode> installedCodeTask;
    private SpeculationLog speculationLog = new HotSpotSpeculationLog();

    HotSpotOptimizedCallTarget(RootNode rootNode, TruffleCompiler compiler, int invokeCounter, int compilationThreshold, boolean compilationEnabled) {
        super(rootNode, invokeCounter, compilationThreshold, compilationEnabled, TruffleUseTimeForCompilationDecision.getValue() ? new TimedCompilationPolicy() : new DefaultCompilationPolicy());
        this.compiler = compiler;
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    public boolean isOptimized() {
        return installedCode != null || installedCodeTask != null;
    }

    @Override
    public Object call(Object... args) {
        return callBoundary(args);
    }

    @TruffleCallBoundary
    private Object callBoundary(Object[] args) {
        if (CompilerDirectives.inInterpreter()) {
            return compiledCallFallback(args);
        } else {
            // We come here from compiled code (i.e., we have been inlined).
            return executeHelper(args);
        }
    }

    private Object compiledCallFallback(Object[] args) {
        InstalledCode currentInstalledCode = installedCode;
        if (currentInstalledCode.isValid()) {
            reinstallCallMethodShortcut();
        } else {
            return compiledCodeInvalidated(args);
        }
        return interpreterCall(args);
    }

    private static void reinstallCallMethodShortcut() {
        if (TraceTruffleCompilation.getValue()) {
            OUT.println("[truffle] reinstall OptimizedCallTarget.call code with frame prolog shortcut.");
        }
        HotSpotTruffleRuntime.installOptimizedCallTargetCallMethod();
    }

    private Object compiledCodeInvalidated(Object[] args) {
        invalidate(null, null, "Compiled code invalidated");
        return call(args);
    }

    @Override
    protected void invalidate(Node oldNode, Node newNode, CharSequence reason) {
        InstalledCode m = this.installedCode;
        if (m != null) {
            CompilerAsserts.neverPartOfCompilation();
            installedCode = null;
            compilationProfile.reportInvalidated();
            logOptimizedInvalidated(this, oldNode, newNode, reason);
        }
        cancelInstalledTask(oldNode, newNode, reason);
    }

    private void cancelInstalledTask(Node oldNode, Node newNode, CharSequence reason) {
        Future<InstalledCode> task = this.installedCodeTask;
        if (task != null) {
            task.cancel(true);
            this.installedCodeTask = null;
            logOptimizingUnqueued(this, oldNode, newNode, reason);
            compilationProfile.reportInvalidated();
        }
    }

    private Object interpreterCall(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        compilationProfile.reportInterpreterCall();
        if (TruffleCallTargetProfiling.getValue()) {
            callCount++;
        }

        if (compilationEnabled && compilationPolicy.shouldCompile(compilationProfile)) {
            compile();
            if (installedCode.isValid()) {
                try {
                    return installedCode.executeVarargs(new Object[]{this, args});
                } catch (InvalidInstalledCodeException ex) {
                    return compiledCodeInvalidated(args);
                }
            }
        }
        return executeHelper(args);
    }

    private boolean isCompiling() {
        Future<InstalledCode> codeTask = this.installedCodeTask;
        if (codeTask != null) {
            if (codeTask.isCancelled()) {
                installedCodeTask = null;
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void compile() {
        if (!isCompiling()) {
            performInlining();
            logOptimizingQueued(this);
            this.installedCodeTask = compiler.compile(this);
            if (!TruffleBackgroundCompilation.getValue()) {
                receiveInstalledCode();
            }
        }
    }

    private InstalledCode receiveInstalledCode() {
        try {
            return installedCodeTask.get();
        } catch (InterruptedException | ExecutionException e) {
            compilationEnabled = false;
            logOptimizingFailed(this, e.getMessage());
            if (e.getCause() instanceof BailoutException) {
                // Bailout => move on.
            } else {
                if (TraceTruffleCompilationExceptions.getValue()) {
                    e.printStackTrace(OUT);
                }
                if (TruffleCompilationExceptionsAreFatal.getValue()) {
                    System.exit(-1);
                }
            }
            return null;
        }
    }

}
