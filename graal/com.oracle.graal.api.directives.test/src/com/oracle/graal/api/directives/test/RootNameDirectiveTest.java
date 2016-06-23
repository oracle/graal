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

public class RootNameDirectiveTest extends GraalCompilerTest {

    public RootNameDirectiveTest() {
        HotSpotResolvedJavaMethod rootNameAtCalleeSnippet = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(RootNameDirectiveTest.class, "rootNameAtCalleeSnippet");
        rootNameAtCalleeSnippet.shouldBeInlined();

        HotSpotResolvedJavaMethod rootNameWithinInstrumentationSnippet = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(RootNameDirectiveTest.class, "rootNameWithinInstrumentationSnippet");
        rootNameWithinInstrumentationSnippet.shouldBeInlined();
    }

    private static String toString(ResolvedJavaMethod method) {
        return method.getDeclaringClass().toJavaName() + "." + method.getName() + method.getSignature().toMethodDescriptor();
    }

    public static String rootNameSnippet() {
        return GraalDirectives.rootName();
    }

    @Test
    public void testRootName() {
        ResolvedJavaMethod method = getResolvedJavaMethod("rootNameSnippet");
        executeExpected(method, null); // ensure the method is fully resolved
        // The target snippet is already the root method. We expect the name of the target snippet
        // is returned.
        InstalledCode code = getCode(method);
        try {
            Result result = new Result(code.executeVarargs(), null);
            assertEquals(new Result(toString(method), null), result);
        } catch (Throwable e) {
            Assert.fail("Unexpected exception: " + e);
        }
    }

    public static String rootNameAtCalleeSnippet() {
        return GraalDirectives.rootName();
    }

    public static String callerSnippet() {
        return rootNameAtCalleeSnippet();
    }

    @Test
    public void testRootNameAtCallee() {
        ResolvedJavaMethod method = getResolvedJavaMethod("callerSnippet");
        executeExpected(method, null); // ensure the method is fully resolved
        // The target snippet is the compilation root of rootNameAtCalleeSnippet() because the later
        // will be inlined. We expect the name of the target snippet is returned.
        InstalledCode code = getCode(method);
        try {
            Result result = new Result(code.executeVarargs(), null);
            assertEquals(new Result(toString(method), null), result);
        } catch (Throwable e) {
            Assert.fail("Unexpected exception: " + e);
        }
    }

    static String rootName1;
    static String rootName2;

    public static void rootNameWithinInstrumentationSnippet() {
        GraalDirectives.instrumentationBegin(0);
        rootName1 = GraalDirectives.rootName();
        GraalDirectives.instrumentationEnd();
    }

    public static void callerSnippet1() {
        rootNameWithinInstrumentationSnippet();

        GraalDirectives.instrumentationBegin(0);
        rootName2 = GraalDirectives.rootName();
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testRootNameWithinInstrumentationAtCallee() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("callerSnippet1");
            executeExpected(method, null); // ensure the method is fully resolved
            rootName1 = null;
            rootName2 = null;
            // We expect both rootName1 and rootName2 are set to the name of the target snippet.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs();
                Assert.assertEquals(toString(method), rootName1);
                Assert.assertEquals(rootName1, rootName2);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

}
