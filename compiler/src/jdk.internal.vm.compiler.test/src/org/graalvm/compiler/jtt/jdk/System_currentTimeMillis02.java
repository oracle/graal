/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.jdk;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

/**
 * Checks that the time between 2 successive calls to {@link System#currentTimeMillis()} is less
 * than 100 milliseconds at least once in 5_000_000 attempts.
 */
public class System_currentTimeMillis02 extends JTTTest {

    public static boolean test() {
        for (int i = 0; i < 5_000_000; i++) {
            long elapsed = System.currentTimeMillis() - System.currentTimeMillis();
            if (elapsed < 100) {
                return true;
            }
        }
        if (!GraalDirectives.inCompiledCode()) {
            // We don't care about the result for the interpreter, C1 or C2
            return true;
        }
        return false;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
