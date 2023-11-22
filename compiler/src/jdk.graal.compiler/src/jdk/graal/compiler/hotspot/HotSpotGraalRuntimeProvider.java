/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import java.io.PrintStream;
import java.util.Map;

import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.CompilationWrapper.ExceptionAction;
import jdk.graal.compiler.core.Instrumentation;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.DiagnosticsOutputDirectory;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetCounter.Group;
import jdk.graal.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.code.TargetDescription;

//JaCoCo Exclude

/**
 * Configuration information for the HotSpot Graal runtime.
 */
public interface HotSpotGraalRuntimeProvider extends GraalRuntime, RuntimeProvider, Group.Factory {

    default TargetDescription getTarget() {
        return getHostBackend().getTarget();
    }

    HotSpotProviders getHostProviders();

    @Override
    default String getName() {
        return getClass().getSimpleName();
    }

    HotSpotGraalRuntime.HotSpotGC getGarbageCollector();

    @Override
    HotSpotBackend getHostBackend();

    GraalHotSpotVMConfig getVMConfig();

    /**
     * Opens a debug context for compiling {@code compilable}. The {@link DebugContext#close()}
     * method should be called on the returned object once the compilation is finished.
     *
     * @param compilationOptions the options used to configure the compilation debug context
     * @param compilationId a system wide unique compilation id
     * @param compilable the input to the compilation
     * @param logStream the log stream to use in this context
     */
    DebugContext openDebugContext(OptionValues compilationOptions, CompilationIdentifier compilationId, Object compilable, Iterable<DebugHandlersFactory> factories, PrintStream logStream);

    /**
     * Gets the option values associated with this runtime.
     */
    OptionValues getOptions();

    /**
     * Determines if the VM is currently bootstrapping the JVMCI compiler.
     */
    boolean isBootstrapping();

    /**
     * This runtime has been requested to shutdown.
     */
    boolean isShutdown();

    /**
     * Gets a directory into which diagnostics such crash reports and dumps should be written.
     */
    DiagnosticsOutputDirectory getOutputDirectory();

    /**
     * Gets the map used to count compilation problems at each {@link ExceptionAction} level. All
     * updates and queries to the map should be synchronized.
     */
    Map<ExceptionAction, Integer> getCompilationProblemsPerAction();

    /**
     * Returns the instance holding the instrumentation data structures.
     */
    Instrumentation getInstrumentation();
}
