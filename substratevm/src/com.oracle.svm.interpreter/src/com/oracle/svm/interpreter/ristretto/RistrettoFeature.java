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
package com.oracle.svm.interpreter.ristretto;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature;
import com.oracle.svm.interpreter.CremaFeature;
import com.oracle.svm.interpreter.InterpreterFeature;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPlugins;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.phases.util.Providers;

/**
 * Ristretto provides runtime Just-In-Time (JIT) compilation support for the Crema interpreter in
 * Native Image.
 *
 * Context: Native Image's closed-world model normally forbids loading new classes at runtime. The
 * Crema project adds dynamic class loading and a bytecode interpreter to execute Java bytecode at
 * Native Image runtime. To recover performance, Ristretto integrates with
 * {@link RuntimeCompilationFeature} so that hot interpreted methods can be compiled to machine code
 * at runtime.
 * 
 * @see CremaFeature
 * @see InterpreterFeature
 * @see RuntimeCompilationFeature
 * @see RistrettoGraphBuilderPlugins
 * @see RistrettoUtils
 * @see RistrettoDirectives
 */
@AutomaticallyRegisteredFeature
public final class RistrettoFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useRistretto();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(CremaFeature.class, RuntimeCompilationFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeSupport.getRuntimeSupport().addShutdownHook(RistrettoCompilationManager.getProfileSupportShutdownHook());
        RuntimeSupport.getRuntimeSupport().addStartupHook(RistrettoCompilationManager.getProfileSupportStartupHook());
    }

    /**
     * Preserves Ristretto directive types required at runtime.
     */
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RistrettoUtils.forcePreserveType(RistrettoDirectives.class);
    }

    /**
     * Registers Ristretto graph builder plugins that lower Crema interpreter operations and
     * runtime-compilation hooks.
     */
    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        registerRistrettoGraphBuilderPlugins(plugins);
    }

    // TODO GR-71480 - invocation plugins for Ristretto

    /**
     * Installs the Ristretto graph builder plugins into the given plugin set. The same plugins are
     * reused for hosted compilation so that generated stubs match runtime behavior.
     *
     * @param plugins graph builder plugin container to mutate
     */
    public static void registerRistrettoGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        // Also use them for hosted compilation.
        RistrettoGraphBuilderPlugins.setRuntimeGraphBuilderPlugins(plugins);
    }

    public static final class RistrettoEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.useRistretto();
        }

    }
}
