/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ReplacesThreadSafetyTestFactory.ReplaceAcrossStateBitsetsNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class ReplacesThreadSafetyTest extends AbstractPolyglotTest {

    @SuppressWarnings({"unused", "truffle-inlining"})
    @Introspectable
    abstract static class ReplaceAcrossStateBitsetsNode extends Node {

        abstract int execute(int v);

        @Specialization(guards = "a == 0")
        int s0(int a) {
            return 0;
        }

        @Specialization(guards = "a == 1")
        int s1(int a) {
            return 1;
        }

        @Specialization(guards = "a == 2")
        int s2(int a) {
            return 2;
        }

        @Specialization(guards = "a == 3")
        int s3(int a) {
            return 3;
        }

        @Specialization(guards = "a == 4")
        int s4(int a) {
            return 4;
        }

        @Specialization(guards = "a == 5")
        int s5(int a) {
            return 5;
        }

        @Specialization(guards = "a == 6")
        int s6(int a) {
            return 6;
        }

        @Specialization(guards = "a ==7")
        int s7(int a) {
            return 7;
        }

        @Specialization(guards = "a == 8")
        int s8(int a) {
            return 8;
        }

        @Specialization(guards = "a == 9")
        int s9(int a) {
            return 9;
        }

        @Specialization(guards = "a == 10")
        int s10(int a) {
            return 10;
        }

        @Specialization(guards = "a == 11")
        int s11(int a) {
            return 11;
        }

        @Specialization(guards = "a == 12")
        int s12(int a) {
            return 12;
        }

        @Specialization(guards = "a == 13")
        int s13(int a) {
            return 13;
        }

        @Specialization(guards = "a == 14")
        int s14(int a) {
            return 14;
        }

        @Specialization(guards = "a == 15")
        int s15(int a) {
            return 15;
        }

        @Specialization(guards = "a == 16")
        int s16(int a) {
            return 16;
        }

        @Specialization(guards = "a == 17")
        int s17(int a) {
            return 17;
        }

        @Specialization(guards = "a == 18")
        int s18(int a) {
            return 18;
        }

        @Specialization(guards = "a == 19")
        int s19(int a) {
            return 19;
        }

        @Specialization(guards = "a == 20")
        int s20(int a) {
            return 20;
        }

        @Specialization(guards = "a == 21")
        int s21(int a) {
            return 21;
        }

        @Specialization(guards = "a == 22")
        int s22(int a) {
            return 22;
        }

        @Specialization(guards = "a == 23")
        int s23(int a) {
            return 23;
        }

        @Specialization(guards = "a == 24")
        int s24(int a) {
            return 24;
        }

        @Specialization(guards = "a == 25")
        int s25(int a) {
            return 25;
        }

        @Specialization(guards = "a == 26")
        int s26(int a) {
            return 26;
        }

        @Specialization(guards = "a == 27")
        int s27(int a) {
            return 27;
        }

        @Specialization(guards = "a == 28")
        int s28(int a) {
            return 28;
        }

        @Specialization(guards = "a == 29")
        int s29(int a) {
            return 29;
        }

        @Specialization(guards = "a == 30")
        int s30(int a) {
            return 30;
        }

        @Specialization(guards = "a == 31")
        int s31(int a) {
            return 31;
        }

        @Specialization(guards = "a <= 32", replaces = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", //
                        "s10", "s11", "s12", "s13", "s14", "s15", "s16", "s17", "s18", "s19", //
                        "s20", "s21", "s22", "s23", "s24", "s25", "s26", "s27", "s28", "s29", //
                        "s30", "s31",
        })
        int s32(int a) {
            return 32;
        }

        @Specialization(guards = "a <= 33", replaces = "s32")
        int s33(int a) {
            return 33;
        }
    }

    @Test
    public void testReplacesConsistent() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            List<ReplaceAcrossStateBitsetsNode> nodes = assertInParallel(ReplaceAcrossStateBitsetsNodeGen::create, (node, threadIndex, objectIndex) -> {
                node.execute(threadIndex);
            });

            // verify that we ultimately end up in consistent states in all the nodes

            for (ReplaceAcrossStateBitsetsNode node : nodes) {
                List<SpecializationInfo> infos = Introspection.getSpecializations(node);
                SpecializationInfo s32 = infos.get(32);
                SpecializationInfo s33 = infos.get(33);
                if (s32.isActive()) {
                    for (int j = 0; j < 32; j++) {
                        assertFalse(String.valueOf(j), infos.get(j).isActive());
                    }
                }
                if (s33.isActive()) {
                    for (int j = 0; j < 33; j++) {
                        assertFalse(String.valueOf(j), infos.get(j).isActive());
                    }
                }
            }
        }

    }

    static final int NODES = 100;
    static final int THREADS = 33;

    private <T extends Node> List<T> assertInParallel(Supplier<T> nodeFactory, ParallelObjectConsumer<T> assertions) throws InterruptedException {
        final int threads = THREADS;
        final int threadPools = 1;
        final int iterations = 1;
        /*
         * We create multiple nodes and run the assertions in a loop to avoid implicit
         * synchronization through the synchronization primitives when running the assertions just
         * for a single node.
         */
        final int nodesCount = NODES;
        return assertNodeInParallel(nodeFactory, assertions, threadPools, threads, iterations, nodesCount);
    }

}
