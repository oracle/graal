/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import jdk.graal.compiler.truffle.AbstractTruffleCompilationSupport;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.truffle.TruffleSupport;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

/**
 * Represents a truffle compilation bundling compilable and task into a single object. Also installs
 * the TTY filter to forward log messages to the truffle runtime.
 */
public final class SubstrateTruffleCompilationSupport extends AbstractTruffleCompilationSupport {

    private String compilerName;

    SubstrateTruffleCompilationSupport() {
    }

    @Override
    public void registerRuntime(TruffleCompilerRuntime runtime) {
        throw CompilerDirectives.shouldNotReachHere("Should not be called. Not necessary to be called on SVM.");
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public TruffleCompiler createCompiler(TruffleCompilerRuntime runtime) {
        return TruffleSupport.singleton().createTruffleCompiler((SubstrateTruffleRuntime) runtime);
    }

    @Override
    public String getCompilerConfigurationName(TruffleCompilerRuntime runtime) {
        return compilerName;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void preinitialize() {
        this.compilerName = GraalConfiguration.runtimeInstance().getCompilerConfigurationName();
    }

}
