/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.DisableStateBitWidthModfication;
import com.oracle.truffle.api.dsl.test.ObjectSizeEstimate;
import com.oracle.truffle.api.dsl.test.examples.NodeInliningExample1_3Factory.Add4AbsNodeGen;
import com.oracle.truffle.api.nodes.Node;

/**
 * See the tutorial description <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/DSLNodeObjectInlining.md">here</a>.
 */
@DisableStateBitWidthModfication
public class NodeInliningExample1_3 {

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AbsNode extends Node {

        abstract long execute(Node node, long value);

        @Specialization(guards = "v >= 0")
        static long doInt(long v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static long doLong(long v) {
            return -v;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AddAbsNode extends Node {

        abstract long execute(Node node, long left, long right);

        @Specialization
        static long add(Node node, long left, long right,
                        @Cached AbsNode leftAbs,
                        @Cached AbsNode rightAbs) {
            return leftAbs.execute(node, left) + rightAbs.execute(node, right);
        }
        // ...
    }

    @GenerateCached(alwaysInlineCached = true)
    @SuppressWarnings("truffle") // ignore inlining candidate warning
    public abstract static class Add4AbsNode extends Node {

        abstract long execute(long v0, long v1, long v2, long v3);

        @Specialization
        long doInt(long v0, long v1, long v2, long v3,
                        @Cached AddAbsNode add0,
                        @Cached AddAbsNode add1,
                        @Cached AddAbsNode add2) {
            long v;
            v = add0.execute(this, v0, v1);
            v = add1.execute(this, v, v2);
            v = add2.execute(this, v, v3);
            return v;
        }

    }

    @Test
    public void test() {
        Add4AbsNode abs = Add4AbsNodeGen.create();
        abs.execute(1, -2, -4, 5);

        // 20 bytes down from 256 bytes when everything is inlined. success!
        assertEquals(20, ObjectSizeEstimate.forObject(abs).getCompressedTotalBytes());
    }

}
