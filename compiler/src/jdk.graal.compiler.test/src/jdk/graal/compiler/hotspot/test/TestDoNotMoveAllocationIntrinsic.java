/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.debug.TTY;

public class TestDoNotMoveAllocationIntrinsic extends GraalCompilerTest {

    static Object O;

    public static void snippet01() {
        O = GraalDirectives.ensureAllocatedHere(new Object());
    }

    @Test
    public void test01() {
        test("snippet01");
    }

    public static void snippet02() {
        O = GraalDirectives.ensureAllocatedHere(new Object[10]);
    }

    @Test
    public void test02() {
        test("snippet02");
    }

    public static void snippet02Local() {
        Object[] array = new Object[10];
        GraalDirectives.ensureAllocatedHere(array);
        O = array;
    }

    @Override
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        // Do not capture graphs for expected compilation failures.
        OptionValues newOptions = new OptionValues(options, DebugOptions.DumpOnError, false);
        return super.compile(installedCodeOwner, graph, compilationResult, compilationId, newOptions);
    }

    @Test
    public void test02Local() throws Exception {
        try (AutoCloseable _ = new TTY.Filter()) {
            try {
                compile("snippet02Local");
                Assert.fail("Compilation should fail because the parameter is not a freshly allocated object");
            } catch (Throwable t) {
                assert t.getMessage().contains("Can only use GraalDirectives.ensureAllocatedHere intrinsic if the parameter allocation is freshly allocated and not a local variable");
            }
        }
    }

    public static void snippet03() {
        Object[] array = new Object[10];
        GraalDirectives.sideEffect();
        GraalDirectives.ensureAllocatedHere(array);
        O = array;
    }

    @Test
    public void test03() throws Exception {
        try (AutoCloseable _ = new TTY.Filter()) {
            try {
                compile("snippet03");
                Assert.fail("Compilation should fail because there is code between the allocation and the actual usage of ensureAllocatedHere");
            } catch (Throwable t) {
                assert t.getMessage().contains("Can only use GraalDirectives.ensureAllocatedHere intrinsic if there ");
            }
        }
    }

    public static void snippet04(Object o) {
        GraalDirectives.ensureAllocatedHere(o);
    }

    @Test
    public void test04() throws Exception {
        try (AutoCloseable _ = new TTY.Filter()) {
            try {
                compile("snippet04");
                Assert.fail("Compilation should fail because ensureAllocatedHere is used with an input that is not an allocation.");
            } catch (Throwable t) {
                assert t.getMessage().contains("Can use GraalDirectives.ensureAllocatedHere only");
            }
        }
    }

    public static void snippet05() {
        O = GraalDirectives.ensureAllocatedHere(new Object[100][100]);
    }

    @Test
    public void test05() throws Exception {
        compile("snippet05");
    }

    public static void snippet06() {
        try {
            O = GraalDirectives.ensureAllocatedHere(new Object[100][100]);
        } catch (OutOfMemoryError e) {
            // do nothing
        }
    }

    @Test
    public void test06() throws Exception {
        compile("snippet06");
    }

    public static void snippet07() {
        O = GraalDirectives.ensureAllocatedHere(new Object());
    }

    @Test
    public void test07() {
        test("snippet07");
    }

}
