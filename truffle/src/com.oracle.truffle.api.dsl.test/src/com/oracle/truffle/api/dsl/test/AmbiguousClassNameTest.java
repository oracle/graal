/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.AmbiguousClassNameTestFactory.InnerClassReferenceNodeGen;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.nodes.Node;

public class AmbiguousClassNameTest {

    /*
     * Tests that ambiguous inner class simple names use more qualified names for the generated
     * code. Also tests that type checks and casts are not mixed up if dynamic value type simple
     * names are ambiguous.
     */
    @Test
    public void testInnerClassReferences() {
        InnerClassReferenceNode node = InnerClassReferenceNodeGen.create();

        AmbiguousClassNameTest.OtherObject expected = new AmbiguousClassNameTest.OtherObject();
        Object actual = node.execute(expected);
        Assert.assertSame(expected, actual);

        RopeNodes.OtherObject expected2 = new RopeNodes.OtherObject();
        actual = node.execute(expected2);
        Assert.assertSame(expected2, actual);
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class InnerClassReferenceNode extends Node {

        public abstract Object execute(Object arg);

        @Specialization
        RopeNodes.OtherObject s1(RopeNodes.OtherObject a,
                        @SuppressWarnings("unused") @Cached("new()") RopeNodes.GetBytesNode getBytes,
                        @SuppressWarnings("unused") @Cached("createGetBytesNode()") AmbiguousClassNameTest.GetBytesNode otherGetBytes) {
            return a;
        }

        @Specialization
        AmbiguousClassNameTest.OtherObject s2(AmbiguousClassNameTest.OtherObject a) {
            return a;
        }

        public static AmbiguousClassNameTest.GetBytesNode createGetBytesNode() {
            return new AmbiguousClassNameTest.GetBytesNode();
        }
    }

    static class GetBytesNode extends Node {

    }

    static class OtherObject {

    }

}

class RopeNodes {

    static class OtherObject {

    }

    public static class GetBytesNode extends Node {

    }

}
