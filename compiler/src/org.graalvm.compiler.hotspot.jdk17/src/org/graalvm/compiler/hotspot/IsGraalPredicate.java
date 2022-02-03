/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.hotspot.HotSpotJVMCICompilerFactory.CompilationLevelAdjustment.None;

import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.hotspot.HotSpotJVMCICompilerFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * Determines if a given class is a JVMCI or Graal class for the purpose of
 * {@link HotSpotGraalCompilerFactory.Options#CompileGraalWithC1Only}.
 */
class IsGraalPredicate extends IsGraalPredicateBase {
    /**
     * Module containing {@link HotSpotJVMCICompilerFactory}.
     */
    private final Module jvmciModule;

    /**
     * Module containing {@link HotSpotGraalCompilerFactory}.
     */
    private final Module graalModule;

    /**
     * Module containing the {@linkplain CompilerConfigurationFactory#selectFactory selected}
     * configuration.
     */
    private Module compilerConfigurationModule;

    IsGraalPredicate() {
        jvmciModule = HotSpotJVMCICompilerFactory.class.getModule();
        graalModule = HotSpotGraalCompilerFactory.class.getModule();
    }

    @Override
    void onCompilerConfigurationFactorySelection(HotSpotJVMCIRuntime runtime, CompilerConfigurationFactory factory) {
        compilerConfigurationModule = factory.getClass().getModule();
        runtime.excludeFromJVMCICompilation(jvmciModule, graalModule, compilerConfigurationModule);
    }

    @Override
    boolean apply(Class<?> declaringClass) {
        throw GraalError.shouldNotReachHere();
    }

    @Override
    HotSpotJVMCICompilerFactory.CompilationLevelAdjustment getCompilationLevelAdjustment() {
        return None;
    }

}
