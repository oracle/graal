/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.directives.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.api.directives.GraalDirectives;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AllocationInstrumentationTest extends GraalCompilerTest {

    public AllocationInstrumentationTest() {
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(ClassA.class, "notInlinedMethod");
        method.setNotInlineable();
    }

    private static class ClassA {
        // This method should be marked as not inlineable
        void notInlinedMethod() {
        }
    }

    static boolean flag;

    public static void resetFlag() {
        flag = false;
    }

    public static void notEscapeSnippet() {
        @SuppressWarnings("unused")
        ClassA a = new ClassA(); // there is no further use of a

        GraalDirectives.instrumentationBegin(-5);
        flag = true;
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testNotEscape() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("notEscapeSnippet");
            executeExpected(method, null); // ensure the method is fully resolved
            resetFlag();
            // The allocation in the snippet does not escape and will be optimized away. We expect
            // the instrumentation is removed.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs();
                Assert.assertFalse("expected flag=false", flag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void mustEscapeSnippet() {
        ClassA a = new ClassA();

        GraalDirectives.instrumentationBegin(-5);
        flag = true;
        GraalDirectives.instrumentationEnd();

        a.notInlinedMethod();
    }

    @Test
    public void testMustEscape() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("mustEscapeSnippet");
            executeExpected(method, null); // ensure the method is fully resolved
            resetFlag();
            // The allocation in the snippet escapes. We expect the instrumentation preserves.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs();
                Assert.assertTrue("expected flag=true", flag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void partialEscapeSnippet(boolean condition) {
        ClassA a = new ClassA();

        GraalDirectives.instrumentationBegin(-5);
        flag = true;
        GraalDirectives.instrumentationEnd();

        if (condition) {
            a.notInlinedMethod();
        }
    }

    @Test
    public void testPartialEscape() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("partialEscapeSnippet");
            executeExpected(method, null, true); // ensure the method is fully resolved
            resetFlag();
            // The allocation in the snippet escapes in the then-clause, and will be relocated to
            // this branch. We expect the instrumentation follows and will only be effective when
            // the then-clause is taken.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(false);
                Assert.assertFalse("expected flag=false", flag);
                code.executeVarargs(true);
                Assert.assertTrue("expected flag=true", flag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

}
