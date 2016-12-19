/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.core.test.debug;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugConfigScope;
import org.graalvm.compiler.debug.DebugEnvironment;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.junit.Test;

/**
 *
 * Tests to verify that the usage of method metrics does not generate compile time overhead through
 * eager evaluation of arguments.
 */
public class FileDumpingTest extends GraalCompilerTest {

    public void testSnippet() {
    }

    public void testSnippet2() {
    }

    @SuppressWarnings("try")
    @Test
    public void testDump() {
        try (OverrideScope s = OptionValue.override(GraalDebugConfig.Options.PrintIdealGraphFile, true,
                        GraalDebugConfig.Options.DumpingErrorsAreFatal, true,
                        GraalDebugConfig.Options.Dump, "")) {
            try (DebugConfigScope scope = DebugEnvironment.initializeScope(TTY.out)) {
                test("testSnippet");
                try (DebugConfigScope scope2 = DebugEnvironment.initializeScope(TTY.out)) {
                    test("testSnippet2");
                }
            }
        }
    }
}
