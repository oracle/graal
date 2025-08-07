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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.hotspot.test.TestNewInstanceWithException.MAX_HEAP_SPACE_ARG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.test.SubprocessUtil;

public class TestNewInstanceWithExceptionRegression extends SubprocessTest {

    @SuppressWarnings("unused")
    public void snippet01() {
        try {
            byte[] a = new byte[123];
            byte[] b = new byte[Integer.MAX_VALUE];
        } catch (OutOfMemoryError e) {
        }
    }

    public void runSubprocessTest(Runnable r, String... args) throws IOException, InterruptedException {
        List<String> newArgs = new ArrayList<>();
        Collections.addAll(newArgs, args);

        SubprocessUtil.Subprocess sp = launchSubprocess(() -> {
            r.run();
        }, newArgs.toArray(new String[0]));
        if (sp != null) {
            // in the sub process itself we cannot spawn another one
            for (String s : sp.output) {
                TTY.printf("%s%n", s);
            }
        }
    }

    @Test
    public void test01() throws IOException, InterruptedException {
        runSubprocessTest(() -> {
            try {
                test("snippet01");
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }, MAX_HEAP_SPACE_ARG);
    }

}
