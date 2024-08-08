/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.graal.compiler.truffle.test.nodes.AbstractTestNode;
import jdk.graal.compiler.truffle.test.nodes.RootTestNode;

public class ByteArraySupportPartialEvaluationTest extends PartialEvaluationTest {

    static final ByteArraySupport BYTES = ByteArraySupport.littleEndian();

    static class GetShortNode extends AbstractTestNode {
        @CompilationFinal(dimensions = 1) byte[] bytes;
        final int offset;

        GetShortNode(String hex, int offset) {
            this.bytes = hexToBytes(hex);
            this.offset = offset;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return BYTES.getShort(bytes, offset);
        }
    }

    static class GetShortUnalignedNode extends AbstractTestNode {
        @CompilationFinal(dimensions = 1) byte[] bytes;
        final int offset;

        GetShortUnalignedNode(String hex, int offset) {
            this.bytes = hexToBytes(hex);
            this.offset = offset;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return BYTES.getShortUnaligned(bytes, offset);
        }
    }

    static class GetIntNode extends AbstractTestNode {
        @CompilationFinal(dimensions = 1) byte[] bytes;
        final int offset;

        GetIntNode(String hex, int offset) {
            this.bytes = hexToBytes(hex);
            this.offset = offset;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return BYTES.getInt(bytes, offset);
        }
    }

    static class GetIntUnalignedNode extends AbstractTestNode {
        @CompilationFinal(dimensions = 1) byte[] bytes;
        final int offset;

        GetIntUnalignedNode(String hex, int offset) {
            this.bytes = hexToBytes(hex);
            this.offset = offset;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return BYTES.getIntUnaligned(bytes, offset);
        }
    }

    static class GetLongNode extends LongNode {
        @CompilationFinal(dimensions = 1) byte[] bytes;
        final int offset;

        GetLongNode(String hex, int offset) {
            this.bytes = hexToBytes(hex);
            this.offset = offset;
        }

        @Override
        public long execute(VirtualFrame frame) {
            return BYTES.getLong(bytes, offset);
        }
    }

    static class GetLongUnalignedNode extends LongNode {
        @CompilationFinal(dimensions = 1) byte[] bytes;
        final int offset;

        GetLongUnalignedNode(String hex, int offset) {
            this.bytes = hexToBytes(hex);
            this.offset = offset;
        }

        @Override
        public long execute(VirtualFrame frame) {
            return BYTES.getLongUnaligned(bytes, offset);
        }
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static RootNode constShortRootNode(short result) {
        return new RootTestNode("constInt", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                return result;
            }
        });
    }

    private static RootNode constIntRootNode(int result) {
        return new RootTestNode("constInt", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                return result;
            }
        });
    }

    private abstract static class LongNode extends Node {
        public abstract long execute(VirtualFrame frame);
    }

    private static class LongRootNode extends RootNode {
        @Child LongNode body;

        protected LongRootNode(LongNode body) {
            super(null);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return body.execute(frame);
        }

    }

    private static RootNode constLongRootNode(long result) {
        return new LongRootNode(new LongNode() {
            @Override
            public long execute(VirtualFrame frame) {
                return result;
            }
        });
    }

    @Test
    public void testGetShort() {
        assertPartialEvalEquals(constShortRootNode((short) 0xab89), new RootTestNode("getShort", new GetShortNode("89abcdef", 0)));
        assertPartialEvalEquals(constShortRootNode((short) 0xab89), new RootTestNode("getShort", new GetShortNode("000089abcdef", 2)));
    }

    @Test
    public void testGetShortUnaligned() {
        assertPartialEvalEquals(constShortRootNode((short) 0xab89), new RootTestNode("getShortUnaligned", new GetShortUnalignedNode("89abcdef", 0)));
        assertPartialEvalEquals(constShortRootNode((short) 0xab89), new RootTestNode("getShortUnaligned", new GetShortUnalignedNode("000089abcdef", 2)));
        assertPartialEvalEquals(constShortRootNode((short) 0xab89), new RootTestNode("getShortUnaligned", new GetShortUnalignedNode("0089abcdef", 1)));
    }

    @Test
    public void testGetInt() {
        assertPartialEvalEquals(constIntRootNode(0xefcdab89), new RootTestNode("getInt", new GetIntNode("89abcdef", 0)));
        assertPartialEvalEquals(constIntRootNode(0xefcdab89), new RootTestNode("getInt", new GetIntNode("0000000089abcdef", 4)));
    }

    @Test
    public void testGetIntUnaligned() {
        assertPartialEvalEquals(constIntRootNode(0xefcdab89), new RootTestNode("getIntUnaligned", new GetIntUnalignedNode("89abcdef", 0)));
        assertPartialEvalEquals(constIntRootNode(0xefcdab89), new RootTestNode("getIntUnaligned", new GetIntUnalignedNode("0000000089abcdef", 4)));
        assertPartialEvalEquals(constIntRootNode(0xefcdab89), new RootTestNode("getIntUnaligned", new GetIntUnalignedNode("000089abcdef", 2)));
    }

    @Test
    public void testGetLong() {
        assertPartialEvalEquals(constLongRootNode(0x1122334455667788L), new LongRootNode(new GetLongNode("8877665544332211", 0)));
        assertPartialEvalEquals(constLongRootNode(0x1122334455667788L), new LongRootNode(new GetLongNode("00000000000000008877665544332211", 8)));
    }

    @Test
    public void testGetLongUnaligned() {
        assertPartialEvalEquals(constLongRootNode(0x1122334455667788L), new LongRootNode(new GetLongUnalignedNode("8877665544332211", 0)));
        assertPartialEvalEquals(constLongRootNode(0x1122334455667788L), new LongRootNode(new GetLongUnalignedNode("00000000000000008877665544332211", 8)));
        assertPartialEvalEquals(constLongRootNode(0x1122334455667788L), new LongRootNode(new GetLongUnalignedNode("008877665544332211", 1)));
    }
}
