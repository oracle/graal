/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * Tests different patterns for applying (or omitting) speculative store checks.
 */
public class SpeculativeStoreChecksTest extends GraalCompilerTest {
    public interface A {
    }

    public class B implements A {
    }

    public class C implements A {
    }

    public static class TestClass<T extends A> {
        @SuppressWarnings("unchecked") public T[] arr = (T[]) new A[10];

        public Object[] arr2 = new Object[10];

        public void store(T t, int i) {
            arr[i] = t;
        }

        public void storeLoop(T t, int to) {
            int k = 0;
            for (int i = 0; GraalDirectives.injectIterationCount(2, i < to); i++) {
                arr[i] = t;
                if (arr.length == to + 1) {
                    break;
                }
                preventExitMerge();
            }
            arr[k] = t;
        }

        public void storeWithPhi1(T t, boolean b) {
            int k = 0;
            Object[] arrPhi = b ? arr : arr2;
            GraalDirectives.controlFlowAnchor();
            arrPhi[k] = t;
        }

        public void storeWithPhi2(T t, boolean b) {
            int k = 0;
            Object[] arrPhi = b ? arr : new A[10];
            GraalDirectives.controlFlowAnchor();
            arrPhi[k] = t;
        }

        public void recursivePhis(T t, int to) {
            Object[] arrPhi = arr;

            for (int i = 0; GraalDirectives.injectIterationCount(2, i < to); i++) {
                /*
                 * The if-phi is an input of the loop-phi and vice-versa, creating a recursion which
                 * needs to be treated by the SpeculativeStoreChecksPhase.
                 */
                if (shouldSwitch()) {
                    arrPhi = arrPhi == arr ? arr2 : arr;
                }
                GraalDirectives.controlFlowAnchor();
                arrPhi[i] = t;
            }
        }

        @BytecodeParserNeverInline
        private static boolean shouldSwitch() {
            return true;
        }

        @BytecodeParserNeverInline
        private static void preventExitMerge() {
        }
    }

    @Test
    public void testInterfaceArray() throws InvalidInstalledCodeException {
        var meth = getResolvedJavaMethod(TestClass.class, "store", A.class, int.class);
        TestClass<B> o = new TestClass<>();
        var code = getCode(meth);
        code.executeVarargs(o, new B(), 3);
        assert code.isValid();
    }

    @Test
    public void testInterfaceArrayWithProxy() throws InvalidInstalledCodeException {
        var meth = getResolvedJavaMethod(TestClass.class, "storeLoop", A.class, int.class);
        TestClass<B> o = new TestClass<>();
        OptionValues opts = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false);
        var code = getCode(meth, opts);
        code.executeVarargs(o, new B(), 1);
        assert code.isValid();
    }

    @Test
    public void testStoreWithPhi1() throws InvalidInstalledCodeException {
        var meth = getResolvedJavaMethod(TestClass.class, "storeWithPhi1", A.class, boolean.class);
        TestClass<B> o = new TestClass<>();
        var code = getCode(meth);
        code.executeVarargs(o, new B(), true);
        assert code.isValid();
    }

    @Test
    public void testStoreWithPhi2() throws InvalidInstalledCodeException {
        var meth = getResolvedJavaMethod(TestClass.class, "storeWithPhi2", A.class, boolean.class);
        TestClass<B> o = new TestClass<>();
        var code = getCode(meth);
        code.executeVarargs(o, new B(), true);
        assert code.isValid();
    }

    @Test
    public void testRecursivePhis() throws InvalidInstalledCodeException {
        var meth = getResolvedJavaMethod(TestClass.class, "recursivePhis", A.class, int.class);
        TestClass<B> o = new TestClass<>();
        OptionValues opts = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false);
        var code = getCode(meth, opts);
        code.executeVarargs(o, new B(), 1);
        assert code.isValid();
    }
}
