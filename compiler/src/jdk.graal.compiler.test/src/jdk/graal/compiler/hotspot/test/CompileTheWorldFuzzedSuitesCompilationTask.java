/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import jdk.graal.compiler.core.common.CancellationBailoutException;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.phases.fuzzing.FuzzedSuites;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.meta.HotSpotFuzzedSuitesProvider;
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

    private final class HotSpotFuzzedCompilationWrapper extends HotSpotCompilationWrapper {

        private static boolean shouldIgnoreFailure(Throwable cause) {
            if (cause instanceof CancellationBailoutException) {
                /* Cancelled compilations are not failed compilations. */
                return true;
            }
            if (cause instanceof PermanentBailoutException bailout) {
                /* Some bailouts related to node intrinsics can be ignored in the context of CTW. */
                return CompileTheWorld.shouldIgnoreFailure(bailout.getMessage());
            }
            return false;
        }

        @Override
        protected HotSpotCompilationRequestResult handleFailure(DebugContext initialDebug, Throwable cause) {
            if (!shouldIgnoreFailure(cause)) {

                String dumpPath = initialDebug.getDumpPath("", false, false);

                HotSpotFuzzedSuitesProvider suitesProvider = (HotSpotFuzzedSuitesProvider) compiler.getGraalRuntime().getHostBackend().getProviders().getSuites();
                FuzzedSuites phasePlan = suitesProvider.getLastSuitesForThread(initialDebug.getOptions());
                phasePlan.saveFuzzedSuites(dumpPath);
                try {
                    final String logFileName = dumpPath + "_failure.log";
                    TTY.println("CompileTheWorld : %s error compiling method: %s", cause.getClass(), this.toString());
                    TTY.println("CompileTheWorld : Retry from seed with: -D%s=%s", HotSpotFuzzedSuitesProvider.SEED_SYSTEM_PROPERTY, suitesProvider.getLastSeed().get());
                    TTY.println("CompileTheWorld : See the file %s for details.", logFileName);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (PrintStream ps = new PrintStream(baos)) {
                        ps.printf("CompileTheWorld : %s error compiling method: %s%n%n", cause.getClass(), this.toString());
                        ps.printf("Command to retry:%n");
                        ps.printf("mx gate --extra-vm-argument='-DCompileTheWorld.LoadPhasePlan=%s.phaseplan'" +
                                        " --extra-vm-argument='-DCompileTheWorld.MethodFilter=%s'" +
                                        " --extra-vm-argument='-Djdk.graal.CompilerConfiguration=%s' --tags ctw%n%n",
                                        dumpPath, getMethod().format("%H.%n"), compiler.getGraalRuntime().getCompilerConfigurationName());
                        ps.printf("Failure:%n%s%n%n", cause.toString());
                        ps.printf("%s%n%n", phasePlan.toString());
                        ps.printf("The stack trace:%n");
                        cause.printStackTrace(ps);
                    }
                    Files.write(Paths.get(logFileName), baos.toByteArray());
                } catch (IOException e) {
                    GraalError.shouldNotReachHere(e, "Error saving log of failed phase plan to " + dumpPath + "_failure.log"); // ExcludeFromJacocoGeneratedReport
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
