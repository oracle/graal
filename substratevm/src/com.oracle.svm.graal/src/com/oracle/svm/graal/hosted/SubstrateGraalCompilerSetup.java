/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted;

import java.util.function.Function;

import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.graal.isolated.IsolateAwareMetaAccess;
import com.oracle.svm.graal.isolated.IsolateAwareProviders;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;
import com.oracle.svm.graal.meta.SubstrateRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;

import jdk.vm.ci.meta.ConstantReflectionProvider;

public class SubstrateGraalCompilerSetup {

    protected final SubstrateMetaAccess sMetaAccess;

    public SubstrateGraalCompilerSetup() {
        if (SubstrateOptions.supportCompileInIsolates()) {
            sMetaAccess = new IsolateAwareMetaAccess();
        } else {
            sMetaAccess = new SubstrateMetaAccess();
        }
    }

    public SubstrateProviders getSubstrateProviders(AnalysisMetaAccess aMetaAccess) {
        if (SubstrateOptions.supportCompileInIsolates()) {
            assert sMetaAccess instanceof IsolateAwareMetaAccess;
            return new IsolateAwareProviders(aMetaAccess, (IsolateAwareMetaAccess) sMetaAccess);
        } else {
            return new SubstrateProviders(aMetaAccess, sMetaAccess);
        }
    }

    public SharedRuntimeConfigurationBuilder createRuntimeConfigurationBuilder(OptionValues options, SVMHost hostVM, AnalysisUniverse aUniverse, UniverseMetaAccess metaAccess,
                    ConstantReflectionProvider originalReflectionProvider, Function<Providers, SubstrateBackend> backendProvider,
                    NativeLibraries nativeLibraries, ClassInitializationSupport classInitializationSupport, LoopsDataProvider loopsDataProvider,
                    SubstratePlatformConfigurationProvider platformConfig) {
        return new SubstrateRuntimeConfigurationBuilder(options, hostVM, aUniverse, metaAccess, originalReflectionProvider, backendProvider, nativeLibraries, classInitializationSupport,
                        loopsDataProvider, platformConfig);
    }
}
