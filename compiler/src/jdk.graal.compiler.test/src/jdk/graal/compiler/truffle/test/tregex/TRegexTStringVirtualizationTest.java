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
package jdk.graal.compiler.truffle.test.tregex;

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.truffle.test.PartialEvaluationTest;
import jdk.graal.compiler.truffle.test.tregex.TRegexTStringVirtualizationTestFactory.MatchBooleanManagedNodeGen;
import jdk.graal.compiler.truffle.test.tregex.TRegexTStringVirtualizationTestFactory.MatchBooleanNativeNodeGen;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

import sun.misc.Unsafe;

@SuppressWarnings("truffle-inlining")
public class TRegexTStringVirtualizationTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        setupContext(Context.newBuilder().allowNativeAccess(true).build());
        getContext().initialize(TRegexCompilerTestDummyLanguage.ID);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PointerObject implements TruffleObject {

        private static final long byteBufferAddressOffset;

        static {
            Field addressField;
            try {
                addressField = Buffer.class.getDeclaredField("address");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("exception while trying to get Buffer.address via reflection:", e);
            }
            byteBufferAddressOffset = getObjectFieldOffset(addressField);
        }

        private final ByteBuffer buffer;

        PointerObject(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public static PointerObject create(byte[] array) {
            return new PointerObject(createByteBuffer(array));
        }

        private static ByteBuffer createByteBuffer(byte[] array) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(array.length);
            UNSAFE.copyMemory(array, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, getBufferAddress(buffer), array.length);
            return buffer;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean isPointer() {
            return true;
        }

        @ExportMessage
        public long asPointer() {
            return getBufferAddress(buffer);
        }

        private static long getBufferAddress(ByteBuffer buffer) {
            return UNSAFE.getLong(buffer, byteBufferAddressOffset);
        }
    }

    public abstract static class MatchBooleanManagedNode extends Node {

        abstract boolean execute(Object compiledRegex, byte[] array, int inputLen, int from, TruffleString.Encoding encoding);

        @Specialization(limit = "1")
        protected boolean match(Object compiledRegex, byte[] array, int inputLen, int from, TruffleString.Encoding encoding,
                        @CachedLibrary("compiledRegex") InteropLibrary tregexEngine,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            try {
                TruffleString input = fromByteArrayNode.execute(array, 0, inputLen, encoding, false);
                return (boolean) tregexEngine.invokeMember(compiledRegex, "execBoolean", input, from);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public abstract static class MatchBooleanNativeNode extends Node {

        abstract boolean execute(Object compiledRegex, ByteBuffer buffer, int inputLen, int from, TruffleString.Encoding encoding);

        @Specialization(limit = "1")
        protected boolean match(Object compiledRegex, ByteBuffer buffer, int inputLen, int from, TruffleString.Encoding encoding,
                        @CachedLibrary("compiledRegex") InteropLibrary tregexEngine,
                        @Cached TruffleString.FromNativePointerNode fromNativePointerNode) {
            try {
                TruffleString input = fromNativePointerNode.execute(new PointerObject(buffer), 0, inputLen, encoding, false);
                return (boolean) tregexEngine.invokeMember(compiledRegex, "execBoolean", input, from);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Test
    public void testMatchBooleanManaged() {
        Object compiledRegex = compileRegex("/[abc]/", "");
        assertNoAllocations(new RootNode(null) {

            @Child MatchBooleanManagedNode node = MatchBooleanManagedNodeGen.create();

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                return node.execute(args[0], (byte[]) args[1], (int) args[2], (int) args[3], UTF_8);
            }
        }, compiledRegex, new byte[]{'a', 'b', 'c', 'd'}, 4, 1);
    }

    @Test
    public void testMatchBooleanNative() {
        Object[] compiledRegexes = {
                        compileRegex("/[abc]/", ""),
                        compileRegex("/[xyz]/", ""),
                        compileRegex("/[uvw]/", ""),
                        compileRegex("/[e-f](x)|(b)*/", ""),
        };
        RootNode root = new RootNode(null) {

            @Child MatchBooleanNativeNode node = MatchBooleanNativeNodeGen.create();

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                return node.execute(args[0], (ByteBuffer) args[1], (int) args[2], (int) args[3], UTF_8);
            }
        };
        for (Object compiledRegex : compiledRegexes) {
            root.getCallTarget().call(compiledRegex, PointerObject.createByteBuffer(new byte[]{'a', 'b', 'c', 'd'}), 4, 0);
        }
        assertNoAllocations(root, compiledRegexes[0], PointerObject.createByteBuffer(new byte[]{'a', 'b', 'c', 'd'}), 4, 1);
    }

    private static Object compileRegex(String pattern, String flags) {
        String regexStr = "GenerateDFAImmediately=true" + '/' + pattern + '/' + flags;
        Source source = Source.newBuilder("regex", regexStr, regexStr).mimeType("application/tregex").internal(true).build();
        return TRegexCompilerTestDummyLanguage.DummyLanguageContext.get(null).getEnv().parseInternal(source).call();
    }

    private void assertNoAllocations(RootNode root, Object... args) {
        StructuredGraph graph = partialEval(root, args);
        Assert.assertEquals(0, graph.getNodes().filter(CommitAllocationNode.class).count());
    }
}
