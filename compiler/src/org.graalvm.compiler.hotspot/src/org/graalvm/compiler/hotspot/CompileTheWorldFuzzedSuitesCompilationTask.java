/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.phases.fuzzing.FuzzedSuites;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.meta.HotSpotFuzzedSuitesProvider;

import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * {@link CompilationTask} used for CompileTheWorld when compiling a graph with {@link FuzzedSuites}
 * instead of the normal phase plan. This handles the dumping of the fuzzed phase plan in case of a
 * failure.
 */
public class CompileTheWorldFuzzedSuitesCompilationTask extends CompilationTask {

    public CompileTheWorldFuzzedSuitesCompilationTask(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler, HotSpotCompilationRequest request, boolean useProfilingInfo,
                    boolean installAsDefault) {
        super(jvmciRuntime, compiler, request, useProfilingInfo, installAsDefault);
    }

    public CompileTheWorldFuzzedSuitesCompilationTask(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler, HotSpotCompilationRequest request, boolean useProfilingInfo,
                    boolean shouldRetainLocalVariables,
                    boolean installAsDefault) {
        super(jvmciRuntime, compiler, request, useProfilingInfo, shouldRetainLocalVariables, installAsDefault);
    }

    private final class HotSpotFuzzedCompilationWrapper extends HotSpotCompilationWrapper {

        @Override
        protected HotSpotCompilationRequestResult handleFailure(DebugContext initialDebug, Throwable cause) {
            if (!(cause instanceof CancellationBailoutException)) {
                String dumpPath = initialDebug.getDumpPath("", false);

                HotSpotFuzzedSuitesProvider suitesProvider = (HotSpotFuzzedSuitesProvider) compiler.getGraalRuntime().getHostBackend().getProviders().getSuites();
                FuzzedSuites phasePlan = suitesProvider.getLastSuitesForThread(initialDebug.getOptions());
                phasePlan.saveFuzzedSuites(dumpPath);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (PrintStream ps = new PrintStream(baos)) {
                        ps.printf("CompileTheWorld : %s error compiling method: %s%n%n", cause.getClass(), this.toString());
                        ps.printf("Command to retry:%n");
                        ps.printf("mx gate --extra-vm-argument='-DCompileTheWorld.LoadPhasePlan=%s.phaseplan'" +
                                        " --extra-vm-argument='-DCompileTheWorld.MethodFilter=%s'" +
                                        " --extra-vm-argument='-Dgraal.CompilerConfiguration=%s' --tags ctw%n%n",
                                        dumpPath, getMethod().format("%H.%n"), compiler.getGraalRuntime().getCompilerConfigurationName());
                        ps.printf("Failure:%n%s%n%n", cause.toString());
                        ps.printf("%s%n%n", phasePlan.toString());
                        ps.printf("The stack trace:%n");
                        cause.printStackTrace(ps);
                    }
                    Files.write(Paths.get(dumpPath + "_failure.log"), baos.toByteArray());
                } catch (IOException e) {
                    GraalError.shouldNotReachHere(e, "Error saving log of failed phase plan to " + dumpPath + "_failure.log");
                }
            }
            return super.handleFailure(initialDebug, cause);
        }
    }

    @Override
    public HotSpotCompilationRequestResult runCompilation(DebugContext debug) {
        return runCompilation(debug, new HotSpotFuzzedCompilationWrapper());
    }

}
