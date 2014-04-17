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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
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
        if (isValid()) {
            reinstallCallMethodShortcut();
        }
        return interpreterCall(args);
    }

    private static void reinstallCallMethodShortcut() {
        if (TraceTruffleCompilation.getValue()) {
            OUT.println("[truffle] reinstall OptimizedCallTarget.call code with frame prolog shortcut.");
        }
        HotSpotTruffleRuntime.installOptimizedCallTargetCallMethod();
    }

    @Override
    public void invalidate() {
        runtime().getCompilerToVM().invalidateInstalledCode(this);
    }

    @Override
    public Object executeVarargs(Object... args) throws InvalidInstalledCodeException {
        return runtime().getCompilerToVM().executeCompiledMethodVarargs(args, this);
    }

    @Override
    protected void invalidate(Node oldNode, Node newNode, CharSequence reason) {
        if (isValid()) {
            CompilerAsserts.neverPartOfCompilation();
            invalidate();
            compilationProfile.reportInvalidated();
            logOptimizedInvalidated(this, oldNode, newNode, reason);
        }
        cancelInstalledTask(oldNode, newNode, reason);
    }

    private void cancelInstalledTask(Node oldNode, Node newNode, CharSequence reason) {
        Future<InstalledCode> task = this.installedCodeTask;
        if (task != null) {
            task.cancel(true);
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
            if (isValid()) {
                try {
                    return executeVarargs(new Object[]{this, args});
                } catch (InvalidInstalledCodeException ex) {
                }
            }
        }
        return executeHelper(args);
    }

    private boolean isCompiling() {
        Future<InstalledCode> codeTask = this.installedCodeTask;
        if (codeTask != null) {
            if (codeTask.isCancelled() || codeTask.isDone()) {
                // System.out.println("done or cancelled => set null " + codeTask.isCancelled());
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

    @Override
    public void exceptionWhileCompiling(Throwable t) {
        compilationEnabled = false;
        logOptimizingFailed(this, t.getMessage());
        if (t instanceof BailoutException) {
            // Bailout => move on.
        } else {
            if (TruffleCompilationExceptionsAreFatal.getValue()) {
                t.printStackTrace(OUT);
                System.exit(-1);
            }
        }
    }

    private void receiveInstalledCode() {
        try {
            // Force task completion.
            installedCodeTask.get();
        } catch (InterruptedException | ExecutionException e) {
            exceptionWhileCompiling(e.getCause());
        }
    }

}
