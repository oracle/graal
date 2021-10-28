/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

/**
 * This reproduces GR-32992. The problem there was unused state bits in fallback specializations so
 * computing the required state bits for fallback specializations more precisely fixed the problem.
 * There were several other problems with unused state bits when this example was run with state bit
 * width of 1 and 2 that were fixed at the same time.
 *
 * This example remains here to ensure that it compiles clean with the DSL.
 */
@SuppressWarnings("unused")
public class GR32992Test {

    static class T0 {
    }

    static class T1 {
    }

    static class T2 {
    }

    static class T3 {
    }

    static class T4 {
    }

    static class T5 {
    }

    static class T6 {
    }

    static class T7 {
    }

    static class T8 {
    }

    static class T9 {
    }

    static class T10 {
    }

    static class T11 {
    }

    static class T12 {
    }

    static class T13 {
    }

    static class T14 {
    }

    static class T15 {
    }

    @TypeSystem
    public static class GR32992TypeSystem {

        @ImplicitCast
        public static T11 c0(int v) {
            return null;
        }

        @ImplicitCast
        public static T11 c1(double v) {
            return null;
        }

        @ImplicitCast
        public static T11 c2(byte v) {
            return null;
        }

        @ImplicitCast
        public static T11 c3(String v) {
            return null;
        }

        @ImplicitCast
        public static T11 c4(T2 v) {
            return null;
        }

        public static T11 c5(T4 v) {
            return null;
        }

        @ImplicitCast
        public static T13 c7(int v) {
            return null;
        }

        @ImplicitCast
        public static T13 c8(double v) {
            return null;
        }

        @ImplicitCast
        public static T13 c9(byte v) {
            return null;
        }

        @ImplicitCast
        public static T13 c10(String v) {
            return null;
        }

        @ImplicitCast
        public static T13 c11(T2 v) {
            return null;
        }

        @ImplicitCast
        public static T13 c12(T4 v) {
            return null;
        }

        @ImplicitCast
        public static T7 c13(int v) {
            return null;
        }

        @ImplicitCast
        public static T5 c14(double v) {
            return null;
        }

        @ImplicitCast
        public static T6 c15(byte v) {
            return null;
        }

        @ImplicitCast
        public static T8 c16(String v) {
            return null;
        }

        @ImplicitCast
        public static T14 c17(T4 v) {
            return null;
        }

        @ImplicitCast
        public static T3 c18(T2 v) {
            return null;
        }

        @ImplicitCast
        public static T9 c19(int v) {
            return null;
        }

        @ImplicitCast
        public static T9 c20(double v) {
            return null;
        }

        @ImplicitCast
        public static T9 c21(byte v) {
            return null;
        }

        @ImplicitCast
        public static T9 c22(String v) {
            return null;
        }

        @ImplicitCast
        public static T9 c23(T4 v) {
            return null;
        }

        @ImplicitCast
        public static T9 c24(T2 v) {
            return null;
        }

        @ImplicitCast
        public static T1 c25(T15 v) {
            return null;
        }
    }

    @TypeSystemReference(GR32992TypeSystem.class)
    abstract static class GR32992BaseNode extends Node {

        public abstract Object executeDouble(double o);

        public abstract Object executeDouble(Object o);

        static boolean guard1(Object v) {
            return true;
        }

        static boolean guard2(Object v) {
            return true;
        }

        static boolean guard3(Object v) {
            return true;
        }

        @Specialization
        protected T0 s1(T0 operand) {
            return null;
        }

        @Specialization
        protected T1 s2(T1 missing) {
            return missing;
        }

        @Specialization
        protected double s3(int operand) {
            return 42;
        }

        @Specialization
        protected double s4(double operand) {
            return 42;
        }

        @Specialization(guards = "guard1(v)")
        protected double s5(T2 v) {
            return 42;
        }

        @Specialization
        protected double s6(T2 v) {
            return 42;
        }

        protected T3 s7(T2 c) {
            return null;
        }

        @Specialization
        protected double s8(byte v) {
            return 42;
        }

        @Specialization
        protected double s9(String v) {
            return 42;
        }

        @Specialization
        protected double s10(T4 v) {
            return 42;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s11(T9 v) {
            return null;
        }

        @Specialization(guards = {"guard2(v)"})
        protected T5 s12(T9 v) {
            return null;
        }

        @Specialization(guards = {"guard3(v)"})
        public T5 s13(T9 v) {
            return null;
        }

        @Specialization
        protected T5 s14(T5 v) {
            return v;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s15(T10 v) {
            return null;
        }

        @Specialization
        protected T5 s16(T10 v) {
            return null;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s17(T12 v) {
            return null;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s18(TruffleObject v) {
            return null;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s19(T6 v) {
            return null;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s20(T7 v) {
            return null;
        }

        @Specialization(guards = "guard1(v)")
        protected T5 s21(T8 v) {
            return null;
        }

        @Fallback
        protected Object f0(Object v) {
            return null;
        }
    }

}
