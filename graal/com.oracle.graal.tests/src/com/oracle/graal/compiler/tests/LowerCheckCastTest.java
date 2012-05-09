/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.tests;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiTypeProfile.*;

public class LowerCheckCastTest extends GraphTest {

    static {
        // Ensure that the methods to be compiled and executed are fully resolved
        asNumber(0);
        asString("0");
        asNumberExt(0);
        asStringExt("0");
    }

    private RiCompiledMethod compile(String name, RiTypeProfile profile) {
        //System.out.println("compiling: " + name + ", hints=" + Arrays.toString(hintClasses) + ", exact=" + exact);

        Method method = getMethod(name);
        final StructuredGraph graph = parse(method);

        CheckCastNode ccn = graph.getNodes(CheckCastNode.class).first();
        assert ccn != null;
        CheckCastNode ccnNew = graph.add(new CheckCastNode(ccn.anchor(), ccn.targetClassInstruction(), ccn.targetClass(), ccn.object(), profile));
        graph.replaceFloating(ccn, ccnNew);

        final RiResolvedMethod riMethod = runtime.getRiMethod(method);
        CiTargetMethod targetMethod = runtime.compile(riMethod, graph);
        return addMethod(riMethod, targetMethod);
    }

    private RiTypeProfile profile(Class... types) {
        if (types.length == 0) {
            return null;
        }
        ProfiledType[] ptypes = new ProfiledType[types.length];
        for (int i = 0; i < types.length; i++) {
            ptypes[i] = new ProfiledType(runtime.getType(types[i]), 1.0D / types.length);
        }
        return new RiTypeProfile(0.0D, ptypes);
    }

    private void test(String name, RiTypeProfile profile, Object expected, Object... args) {
        RiCompiledMethod compiledMethod = compile(name, profile);
        Assert.assertEquals(expected, compiledMethod.executeVarargs(args));
    }

    @Test
    public void test1() {
        test("asNumber",    profile(),                        111, 111);
        test("asNumber",    profile(Integer.class),           111, 111);
        test("asNumber",    profile(Long.class, Short.class), 111, 111);
        test("asNumberExt", profile(),                        121, 111);
        test("asNumberExt", profile(Integer.class),           121, 111);
        test("asNumberExt", profile(Long.class, Short.class), 121, 111);
    }

    @Test
    public void test2() {
        test("asString",    profile(),             "111", "111");
        test("asString",    profile(String.class), "111", "111");
        test("asString",    profile(String.class), "111", "111");

        test("asStringExt", profile(),             "#111", "111");
        test("asStringExt", profile(String.class), "#111", "111");
        test("asStringExt", profile(String.class), "#111", "111");
    }

    @Test(expected = ClassCastException.class)
    public void test3() {
        test("asNumber", profile(), 111, "111");
    }

    @Test(expected = ClassCastException.class)
    public void test4() {
        test("asString", profile(String.class), "111", 111);
    }

    @Test(expected = ClassCastException.class)
    public void test5() {
        test("asNumberExt", profile(), 111, "111");
    }

    @Test(expected = ClassCastException.class)
    public void test6() {
        test("asStringExt", profile(String.class), "111", 111);
    }

    public static Number asNumber(Object o) {
        return (Number) o;
    }

    public static String asString(Object o) {
        return (String) o;
    }

    public static Number asNumberExt(Object o) {
        Number n = (Number) o;
        return n.intValue() + 10;
    }

    public static String asStringExt(Object o) {
        String s = (String) o;
        return "#" + s;
    }
}
