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
import java.util.*;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0.
 * Then canonicalization is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class LowerCheckCastTest extends GraphTest {

    static int warmup() {
        Object[] numbers = {76L, (short) 34};
        int result = 0;
        for (int i = 0; i < 20; i++) {
            Object num = numbers[i % numbers.length];
            result += result + asNumber(num).intValue();
        }
        return result;
    }

    private RiCompiledMethod compile(String name, Class[] hintClasses, boolean exact) {
        System.out.println("compiling: " + name + ", hints=" + Arrays.toString(hintClasses) + ", exact=" + exact);
        // Ensure that the method is fully resolved
        asNumber(0);

        Method method = getMethod(name);
        final StructuredGraph graph = parse(method);

        RiResolvedType[] hints = new RiResolvedType[hintClasses.length];
        for (int i = 0; i < hintClasses.length; i++) {
            hints[i] = runtime.getType(hintClasses[i]);
        }

        CheckCastNode ccn = graph.getNodes(CheckCastNode.class).first();
        assert ccn != null;
        CheckCastNode ccnNew = graph.add(new CheckCastNode(ccn.anchor(), ccn.targetClassInstruction(), ccn.targetClass(), ccn.object(), hints, exact));
        graph.replaceFloating(ccn, ccnNew);

        final RiResolvedMethod riMethod = runtime.getRiMethod(method);
        CiTargetMethod targetMethod = runtime.compile(riMethod, graph);
        return addMethod(riMethod, targetMethod);
    }

    //@Test
    public void test1() {
        Class[] hints = {};
        RiCompiledMethod compiledMethod = compile("asNumber", hints, false);
        Assert.assertEquals(Integer.valueOf(111), compiledMethod.executeVarargs(111));
    }

    //@Test
    public void test2() {
        Class[] hints = {Integer.class};
        RiCompiledMethod compiledMethod = compile("asNumber", hints, false);
        Assert.assertEquals(Integer.valueOf(111), compiledMethod.executeVarargs(111));
    }

    @Test
    public void test3() {
        Class[] hints = {Long.class, Short.class};
        RiCompiledMethod compiledMethod = compile("asNumber", hints, false);
        Assert.assertEquals(Integer.valueOf(111), compiledMethod.executeVarargs(111));
    }

    //@Test
    public void test4() {
        Class[] hints = {};
        RiCompiledMethod compiledMethod = compile("asString", hints, true);
        Assert.assertEquals("111", compiledMethod.executeVarargs("111"));
    }

    @Test
    public void test5() {
        Class[] hints = {String.class};
        RiCompiledMethod compiledMethod = compile("asString", hints, true);
        Assert.assertEquals("111", compiledMethod.executeVarargs("111"));
    }

    //@Test(expected = ClassCastException.class)
    public void test100() {
        Class[] hints = {};
        RiCompiledMethod compiledMethod = compile("asNumber", hints, false);
        compiledMethod.executeVarargs("number");
    }

    public static Number asNumber(Object o) {
        return (Number) o;
    }

    public static String asString(Object o) {
        return (String) o;
    }
}
