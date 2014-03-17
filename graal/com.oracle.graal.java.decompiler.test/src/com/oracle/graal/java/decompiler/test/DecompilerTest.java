/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.decompiler.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.java.decompiler.test.example.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.runtime.*;

public class DecompilerTest {

    public static void doTest(String name) {
        try {
            DebugEnvironment.initialize(System.out);
            MetaAccessProvider metaAccess = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getMetaAccess();
            Method method = Example.class.getDeclaredMethod(name, new Class[]{int.class, int.class});
            final ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(method);
            TestUtil.compileMethod(javaMethod);
        } catch (NoSuchMethodException e) {
            Assert.fail();
        } catch (SecurityException e) {
            Assert.fail();
        }
    }

    @Before
    public void init() {
        GraalOptions.DecompileAfterPhase.setValue("");
        GraalDebugConfig.Dump.setValue("");
    }

    @Test
    public void test01() {
        doTest("loop7");
    }

    @Test
    public void test02() {
        doTest("loop6");
    }

    @Test
    public void test03() {
        doTest("loop5");
    }

    @Test
    public void test04() {
        doTest("loop4");
    }

    @Test
    public void test05() {
        doTest("loop3");
    }

    @Test
    public void test06() {
        doTest("loop2");
    }

    @Test
    public void test07() {
        doTest("loop");
    }

    @Test
    public void test08() {
        doTest("if0");
    }

    @Test
    public void test09() {
        doTest("if1");
    }

    @Test
    public void test10() {
        doTest("if2");
    }

    @Test
    public void test11() {
        doTest("if3");
    }

    @Test
    public void test12() {
        doTest("if4");
    }
}
