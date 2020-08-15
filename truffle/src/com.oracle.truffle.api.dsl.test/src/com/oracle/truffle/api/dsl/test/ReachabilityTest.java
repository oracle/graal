/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.Abstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.BExtendsAbstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.Node;

public class ReachabilityTest {

    static class Reachability1 extends ValueNode {
        @Specialization
        int do2() {
            return 2;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2().")
        @Specialization
        int do1() {
            return 2;
        }
    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType1 extends ValueNode {
        @Specialization
        int do2(int a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2(int).")
        @Specialization
        int do1(int a) {
            return a;
        }
    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType2 extends ValueNode {
        @Specialization
        BExtendsAbstract do2(BExtendsAbstract a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2(BExtendsAbstract).")
        @Specialization
        BExtendsAbstract do1(BExtendsAbstract a) {
            return a;
        }
    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType3 extends ValueNode {
        @Specialization
        Abstract do2(Abstract a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2(Abstract).")
        @Specialization
        BExtendsAbstract do1(BExtendsAbstract a) {
            return a;
        }
    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType4 extends ValueNode {

        @Specialization
        BExtendsAbstract do2(BExtendsAbstract a) {
            return a;
        }

        @Specialization
        Abstract do1(Abstract a) {
            return a;
        }

    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType5 extends ValueNode {

        @Specialization
        double do2(double a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2(double).")
        @Specialization
        int do1(int a) {
            return a;
        }

    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType6 extends ValueNode {

        @Specialization
        BigInteger do2(BigInteger a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2(BigInteger).")
        @Specialization
        int do1(int a) {
            return a;
        }

    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType7 extends ValueNode {

        @Specialization
        int do2(int a) {
            return a;
        }

        @Specialization
        BigInteger do1(BigInteger a) {
            return a;
        }

    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType8 extends ValueNode {

        @Specialization
        int do2(int a) {
            return a;
        }

        @Specialization
        Object do1(Object a) {
            return a;
        }

    }

    @NodeChildren({@NodeChild("a")})
    static class ReachabilityType9 extends ValueNode {

        @Specialization
        Object do2(Object a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2(Object).")
        @Specialization
        int do1(int a) {
            return a;
        }
    }

    static class ReachabilityGuard1 extends ValueNode {

        boolean foo() {
            return false;
        }

        @Specialization(guards = "foo()")
        int do2() {
            return 1;
        }

        @Specialization
        int do1() {
            return 2;
        }

    }

    static class ReachabilityGuard2 extends ValueNode {

        boolean foo() {
            return false;
        }

        @Specialization
        int do2() {
            return 2;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2().")
        @Specialization(guards = "foo()")
        int do1() {
            return 1;
        }

    }

    static class ReachabilityGuard3 extends ValueNode {

        boolean foo() {
            return false;
        }

        @Specialization(guards = "foo()")
        int do2() {
            return 1;
        }

        @Specialization
        int do1() {
            return 2;
        }

    }

    static class ReachabilityGuard4 extends ValueNode {

        boolean foo() {
            return false;
        }

        @Specialization(guards = "foo()")
        int do2() {
            return 1;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2().")
        @Specialization(guards = "foo()")
        int do1() {
            return 2;
        }

    }

    static class ReachabilityThrowable1 extends ValueNode {

        @Specialization(rewriteOn = RuntimeException.class)
        int do2() throws RuntimeException {
            return 1;
        }

        @Specialization
        int do1() {
            return 2;
        }

    }

    static class ReachabilityThrowable2 extends ValueNode {

        @Specialization
        int do2() {
            return 1;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by do2().")
        @Specialization(rewriteOn = RuntimeException.class)
        int do1() throws RuntimeException {
            return 2;
        }

    }

    abstract static class ReachabilityUncached extends Node {
        abstract int execute();

        int foo() {
            return 0;
        }
    }

    @GenerateUncached
    abstract static class ReachabilityUncached1 extends ReachabilityUncached {
        @Specialization
        int doCached(@Cached("foo()") int cached) {
            return cached;
        }

        @Specialization(replaces = "doCached")
        int doUncached() {
            return 1;
        }
    }

    @GenerateUncached
    abstract static class ReachabilityUncached2 extends ReachabilityUncached {
        @Specialization
        int doCached(@Cached("foo()") int cached) {
            return cached;
        }

        @Specialization(replaces = "doCached")
        int doUncached1() {
            return 1;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by doUncached1().")
        @Specialization
        int doUncached2() {
            return 2;
        }
    }

    @GenerateUncached
    abstract static class ReachabilityUncached3 extends ReachabilityUncached {
        @Specialization
        int doCached1(@Cached("foo()") int cached) {
            return cached;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by doCached1(int).")
        @Specialization(replaces = "doCached1")
        int doCached2(@Cached("foo()") int cached) {
            return cached + 1;
        }

        @Specialization(replaces = "doCached2")
        int doUncached() {
            return 1;
        }
    }
}
