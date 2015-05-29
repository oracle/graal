/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.bytecode;

import org.junit.*;

import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.jtt.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.jvmci.options.*;
import com.oracle.jvmci.options.OptionValue.OverrideScope;

/**
 * Tests the instanceof works, when casting an array of interface.
 */
public class BC_instanceof01 extends JTTTest {

    public interface IObject {

    }

    public interface IDerivedObject extends IObject {

    }

    private static class BaseClass {

    }

    private static class TestClass extends BaseClass implements IObject {
    }

    private static class DerivedTestClass extends BaseClass implements IDerivedObject {

    }

    static TestClass[] a1 = {new TestClass()};
    static DerivedTestClass[] a2 = {new DerivedTestClass()};

    public static BaseClass[] getBaseClassArray() {
        return a1;
    }

    public static BaseClass[] getDerivedBaseClassArray() {
        return a2;
    }

    public static boolean test() {
        return getBaseClassArray() instanceof IObject[];
    }

    public static int testConditionalElimination() {
        BaseClass[] result = getDerivedBaseClassArray();
        if (result instanceof IDerivedObject[]) {
            if (result instanceof IObject[]) {
                return 1;
            } else {
                return 2;
            }
        } else {
            return 3;
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

    @Override
    protected Suites getSuites() {
        try (OverrideScope scope = OptionValue.override(HighTier.Options.Inline, false)) {
            return super.getSuites();
        }
    }

    @Test
    public void run1() throws Throwable {
        runTest("testConditionalElimination");
    }
}
