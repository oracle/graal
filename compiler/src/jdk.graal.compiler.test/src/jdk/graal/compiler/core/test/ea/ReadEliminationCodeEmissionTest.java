/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class ReadEliminationCodeEmissionTest extends SubprocessTest {

    static class A {
        final int x;

        A(int x) {
            this.x = x;
        }
    }

    static volatile int accross;

    static int S;

    public static int accessVolatile1Snippet(A a) {
        int load1 = a.x;
        S = accross;
        int load2 = a.x;
        return load1 + load2;
    }

    @Test
    public void accessVolatile1() {
        runTest(currentUnitTestName(), new A(12));
    }

    public void runTest(String baseName, Object... args) {
        String snippetName = baseName + "Snippet";
        String methodSpec = getClass().getName() + "::" + snippetName;
        Method m = getMethod(snippetName);

        Runnable run = () -> {
            // Force compilation with HotSpot
            for (int i = 0; i < 100000; i++) {
                try {
                    Object[] finalArgs = applyArgSuppliers(args);
                    m.invoke(null, finalArgs);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            if (args.length != 0) {
                Object[] nullArgs = args.clone();
                for (int i = 0; i < nullArgs.length; i++) {
                    nullArgs[i] = null;
                }
                test(snippetName, nullArgs);
            }
            // Now generate JVMCI code
            InstalledCode code = getCode(getResolvedJavaMethod(snippetName));
            for (int i = 0; i < 100000; i++) {
                try {
                    Object[] finalArgs = applyArgSuppliers(args);
                    code.executeVarargs(finalArgs);
                    if (i % 1000 == 0) {
                        System.gc();
                    }
                } catch (InvalidInstalledCodeException e) {
                    throw new RuntimeException(e);
                }
            }

        };

        GraalHotSpotVMConfig config = ((HotSpotBackend) getBackend()).getRuntime().getVMConfig();
        SubprocessUtil.Subprocess subprocess = null;
        String logName = null;
        String[] vmArgs = new String[0];
        boolean print = Boolean.getBoolean("debug." + this.getClass().getName() + ".print");
        if (print) {
            logName = config.gc.name() + "_" + baseName + ".log";
            vmArgs = new String[]{"-XX:CompileCommand=print," + methodSpec,
                            "-XX:CompileCommand=dontinline," + methodSpec,
                            "-XX:+UnlockDiagnosticVMOptions",
                            "-XX:-DisplayVMOutput",
                            "-XX:-TieredCompilation",
                            "-XX:+LogVMOutput",
                            "-XX:+PreserveFramePointer",
                            "-Xbatch",
                            "-XX:LogFile=" + logName,
                            "-Dgraal.Dump=:5"};
        }
        try {
            subprocess = launchSubprocess(run, vmArgs);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
        if (subprocess != null && logName != null) {
            System.out.println("HotSpot output saved in " + logName);
        }
    }
}
