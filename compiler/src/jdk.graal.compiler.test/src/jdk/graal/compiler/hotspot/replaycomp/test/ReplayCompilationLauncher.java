/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.test;

import java.io.PrintStream;

import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalScope;

import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationRunner;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport;
import jdk.graal.compiler.hotspot.test.LibGraalCompilationDriver;

/**
 * The first entry point for the replay compilation launcher. This class determines whether libgraal
 * is available and tries to invoke the launcher in libgraal. Otherwise, it invokes the launcher in
 * jargraal.
 */
public class ReplayCompilationLauncher {
    static {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.graal.compiler");
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.internal.vm.ci");
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("org.graalvm.truffle.runtime");
    }

    /**
     * Calls libgraal C entry point
     * {@code jdk.graal.compiler.libgraal.LibGraalEntryPoints#replayCompilation}, which in turn
     * calls {@link ReplayCompilationRunner#run(String[], PrintStream)}.
     */
    public static native int runInLibgraal(long isolateThread, long argBuffer);

    public static void main(String[] args) {
        if (LibGraal.isAvailable()) {
            try {
                LibGraal.registerNativeMethods(ReplayCompilationLauncher.class);
            } catch (Error error) {
                System.err.printf("The replay launcher entry point could not be linked. Build libgraal with -D%s=true as an extra image builder option to enable the launcher.%n",
                                ReplayCompilationSupport.ENABLE_REPLAY_LAUNCHER_PROP);
                throw error;
            }
            StringBuilder argString = new StringBuilder();
            for (String arg : args) {
                if (!argString.isEmpty()) {
                    argString.append('\n');
                }
                argString.append(arg);
            }
            try (var stringBuffer = new LibGraalCompilationDriver.LibGraalParams.UTF8CStringBuffer(argString.toString());
                            LibGraalScope scope = new LibGraalScope()) {
                int status = runInLibgraal(scope.getIsolateThreadAddress(), stringBuffer.getAddress());
                System.exit(status);
            }
        } else {
            ReplayCompilationRunner.run(args, System.out).exitVM();
        }
    }
}
