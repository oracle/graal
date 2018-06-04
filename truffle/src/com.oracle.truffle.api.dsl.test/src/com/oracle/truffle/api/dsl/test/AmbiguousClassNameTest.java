/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
