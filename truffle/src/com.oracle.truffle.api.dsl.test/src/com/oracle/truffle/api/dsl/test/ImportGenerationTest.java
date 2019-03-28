/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ImportGenerationTestFactory.ImportTestNodeGen;
import com.oracle.truffle.api.dsl.test.otherPackage.OtherPackageGroup;
import com.oracle.truffle.api.dsl.test.otherPackage.OtherPackageNode;
import com.oracle.truffle.api.nodes.Node;

/*
 * Tests that import generation generates valid code for all combinations of referring to nodes.
 */
public class ImportGenerationTest {

    @Test
    public void testNode() {
        assertEquals(42, ImportTestNodeGen.create().execute(42));
        assertEquals(42, ImportTestNodeGen.getUncached().execute(42));
    }

    @SuppressWarnings("unused")
    @GenerateUncached
    public abstract static class ImportTestNode extends Node {

        public abstract Object execute(Object arg);

        @Specialization
        int doDefault(int arg,
                        @Cached OtherPackageNode node1,
                        @Cached OtherPackageNode.InnerNode node2,
                        @Cached(allowUncached = true) OtherPackageNode.OtherPackageGroup node3,
                        @Cached OtherPackageNode.OtherPackageGroup.InnerNode node4,
                        @Cached(allowUncached = true) OtherPackageNode.OtherPackageGroup.InnerGroup node5,
                        @Cached OtherPackageNode.OtherPackageGroup.InnerGroup.InnerNode node6,
                        @Cached(allowUncached = true) ThisPackageGroup node7,
                        @Cached ThisPackageGroup.InnerNode node8,
                        @Cached ThisPackageGroup.InnerNode.InnerInnerNode node9,
                        @Cached(allowUncached = true) ThisPackageGroup.InnerNode.InnerGroup node10,
                        @Cached ThisPackageGroup.InnerNode.InnerGroup.InnerInnerNode node11,
                        @Cached ThisPackageGroup.InnerGroup.InnerNode node12,
                        @Cached(allowUncached = true) OtherPackageGroup node13,
                        @Cached OtherPackageGroup.InnerNode node14,
                        @Cached(allowUncached = true) OtherPackageGroup.InnerGroup node15,
                        @Cached OtherPackageGroup.InnerGroup.InnerNode node16) {
            assertNotNull(node1);
            assertNotNull(node2);
            assertNotNull(node3);
            assertNotNull(node4);
            assertNotNull(node5);
            assertNotNull(node6);
            assertNotNull(node7);
            assertNotNull(node8);
            assertNotNull(node9);
            assertNotNull(node10);
            assertNotNull(node11);
            assertNotNull(node12);
            assertNotNull(node13);
            assertNotNull(node14);
            assertNotNull(node15);
            assertNotNull(node16);
            return arg;
        }

    }

    public static class ThisPackageGroup {
        public static ThisPackageGroup create() {
            return new ThisPackageGroup();
        }

        public static class InnerGroup {

            @GenerateUncached
            public abstract static class InnerNode extends Node {

                public abstract Object execute(Object arg);

                @Specialization
                int doDefault(int arg) {
                    return arg;
                }

            }

            public static InnerGroup create() {
                return new InnerGroup();
            }
        }

        @GenerateUncached
        public abstract static class InnerNode extends Node {

            public abstract Object execute(Object arg);

            @Specialization
            int doDefault(int arg) {
                return arg;
            }

            @GenerateUncached
            public abstract static class InnerInnerNode extends Node {

                public abstract Object execute(Object arg);

                @Specialization
                int doDefault(int arg) {
                    return arg;
                }

            }

            public static class InnerGroup {
                @GenerateUncached
                public abstract static class InnerInnerNode extends Node {

                    public abstract Object execute(Object arg);

                    @Specialization
                    int doDefault(int arg) {
                        return arg;
                    }
                }

                public static InnerGroup create() {
                    return new InnerGroup();
                }
            }
        }
    }

}
