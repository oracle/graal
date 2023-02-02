/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.strings;

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public class TStringConstantFoldingTest extends PartialEvaluationTest {

    static final TruffleString a = TruffleString.fromByteArrayUncached(new byte[]{'a', 'b', 'c', 'd', 'e'}, UTF_8);
    static final TruffleString b = TruffleString.fromByteArrayUncached(new byte[]{'a', 'b', 'c', 'd', 'e'}, UTF_8);

    @Test
    public void testCodePointAtIndex() {
        assertConstant(new RootNode(null) {

            @Child TruffleString.CodePointAtIndexNode node = TruffleString.CodePointAtIndexNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(a, 0, UTF_32);
            }
        });
    }

    @Test
    public void testRegionEquals() {
        assertConstant(new RootNode(null) {

            @Child TruffleString.RegionEqualByteIndexNode node = TruffleString.RegionEqualByteIndexNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(a, 0, b, 0, a.byteLength(UTF_8), UTF_8);
            }
        });
    }

    @Test
    public void testEquals() {
        assertConstant(new RootNode(null) {

            @Child TruffleString.EqualNode node = TruffleString.EqualNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(a, b, UTF_8);
            }
        });
    }

    @Test
    public void testCompareTo() {
        assertConstant(new RootNode(null) {

            @Child TruffleString.CompareBytesNode node = TruffleString.CompareBytesNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(a, b, UTF_8);
            }
        });
    }

    @Test
    public void testIndexOf() {
        assertConstant(new RootNode(null) {

            @Child TruffleString.ByteIndexOfCodePointNode node = TruffleString.ByteIndexOfCodePointNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(a, 'b', 0, a.byteLength(UTF_8), UTF_8);
            }
        });
    }

    @Test
    public void testIndexOfSubstring() {
        assertConstant(new RootNode(null) {

            @Child TruffleString.ByteIndexOfStringNode node = TruffleString.ByteIndexOfStringNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(a, b, 0, a.byteLength(UTF_8), UTF_8) == 1;
            }
        });
    }

    private void assertConstant(RootNode root) {
        Assume.assumeTrue(TStringTest.isSupportedArchitecture(getArchitecture()) &&
                        Math.max(GraalOptions.ArrayRegionEqualsConstantLimit.getValue(getGraalOptions()), GraalOptions.StringIndexOfConstantLimit.getValue(getGraalOptions())) >= a.byteLength(UTF_8));

        StructuredGraph graph = partialEval(root);
        compile((OptimizedCallTarget) root.getCallTarget(), graph);
        removeFrameStates(graph);
        for (ReturnNode ret : graph.getNodes(ReturnNode.TYPE)) {
            Assert.assertTrue(ret.result().isConstant());
        }
    }
}
