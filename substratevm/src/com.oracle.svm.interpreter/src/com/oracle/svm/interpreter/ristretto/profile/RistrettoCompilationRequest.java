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
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.InstalledCode;

public class RistrettoCompilationRequest implements Comparable<RistrettoCompilationRequest>, Callable<InstalledCode> {
    /**
     * Default priority for any graal top tier compilation.
     */
    public static final int DEFAULT_TOP_TIER_COMPILATION_PRIORITY = 100;

    private final RistrettoMethod rMethod;
    private final int priority;

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority) {
        this.rMethod = rMethod;
        this.priority = priority;
    }

    @Override
    public int compareTo(RistrettoCompilationRequest o) {
        return Integer.compare(priority, o.priority);
    }

    @Override
    public InstalledCode call() throws Exception {
        SubstrateInstalledCodeImpl code = RistrettoUtils.compileAndInstall(rMethod);
        RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Finished compiling %s%n", rMethod);

        /*
         * Installing a reference to installed code in ristretto method to have the same lifecycle
         * as InterpreterMethod->RistrettoMethod->code, so the root pointer is only dropped when a
         * class is unloaded and the interpreter jvmci objects are collected
         *
         * We can never compile the same method concurrently and race on the update of this pointer
         * because of the synchronization enforced by the rMethod.compilationState state machine.
         *
         * The rMethod.installedCode is used by the interpreter to access any installed code and
         * call it instead of interpreter frames.
         */
        if (RistrettoCompilationManager.TestingBackdoor.installCode()) {
            rMethod.installedCode = code;
        }

        if (!RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(rMethod, RistrettoConstants.COMPILE_STATE_SUBMITTED,
                        RistrettoConstants.COMPILE_STATE_COMPILED)) {
            throw GraalError.shouldNotReachHere(
                            String.format("Only a single compile of this method should ever reach the compile queue, it cannot be that we reach here with a different state but did %s",
                                            RistrettoCompileStateMachine.toString(RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(rMethod))));
        }
        return code;
    }

    @Override
    public String toString() {
        return "CompilationRequest <" + rMethod + ", priority=" + priority + ">";
    }

    public int getPriority() {
        return priority;
    }

    public RistrettoMethod getRMethod() {
        return rMethod;
    }
}
