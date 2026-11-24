/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.sl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLStrings;

public class SLInteropBytecodeIndexTest extends AbstractSLTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testMemberRead() {
        assertStorage("o.x", "", false, MemberObject::new, 42);
    }

    @Test
    public void testMemberWrite() {
        assertStorage("o.x = 42", "", true, MemberObject::new, 42);
    }

    @Test
    public void testArrayRead() {
        assertStorage("o[0]", "", false, ArrayObject::new, 42);
    }

    @Test
    public void testArrayWrite() {
        assertStorage("o[0] = 42", "", true, ArrayObject::new, 42);
    }

    @Test
    public void testForeignPropertyNameRead() {
        assertStorage("obj[o]", "obj = new(); obj[\"[foreign object]\"] = 42;", false, ForeignName::new, 42);
    }

    @Test
    public void testForeignPropertyNameWrite() {
        assertStorage("obj[o] = 42", "obj = new();", true, ForeignName::new, 42);
    }

    @Test
    public void testConcatenateForeignLeft() {
        assertStorage("o + \"!\"", "", false, ForeignName::new, "[foreign object]!");
    }

    @Test
    public void testConcatenateForeignRight() {
        assertStorage("\"!\" + o", "", false, ForeignName::new, "![foreign object]");
    }

    private void assertStorage(String expression, String setup, boolean write, Function<Runnable, Object> createObject, Object expected) {
        try (Context context = newContextBuilder("sl").build()) {
            String location = write || expected instanceof String ? expression : "return " + expression;
            String body = setup + (write ? expression + "; return 42;" : "return " + expression + ";");
            Value function = context.eval("sl", "function test(o) { " + body + " } function observe(check) { check(); } function main() { return test; }");
            BytecodeNode[] bytecode = new BytecodeNode[1];
            MaterializedFrame[] frame = new MaterializedFrame[1];
            int[] visits = {0};
            boolean[] throwing = {false};
            Runnable check = () -> {
                Boolean found = Truffle.getRuntime().iterateFrames(f -> {
                    RootNode root = ((RootCallTarget) f.getCallTarget()).getRootNode();
                    if (!"test".equals(root.getName())) {
                        return null;
                    }
                    visits[0]++;
                    assertEquals(mode.isBytecode(), root instanceof BytecodeRootNode);
                    if (root instanceof BytecodeRootNode bytecodeRoot) {
                        bytecode[0] = bytecodeRoot.getBytecodeNode();
                        frame[0] = f.getFrame(FrameAccess.MATERIALIZE).materialize();
                        int bci = bytecode[0].getBytecodeIndex(frame[0]);
                        assertTrue(bci >= 0);
                        assertEquals(location, bytecode[0].getSourceLocation(bci).getCharacters().toString());
                    }
                    return true;
                });
                assertEquals(Boolean.TRUE, found);
                if (throwing[0]) {
                    throw new InteropException();
                }
            };
            // Re-enter SL before inspecting the suspended property/conversion operation.
            Checker checker = new Checker(check);
            Object object = createObject.apply(() -> {
                Node caller = Truffle.getRuntime().iterateFrames(f -> {
                    RootNode root = ((RootCallTarget) f.getCallTarget()).getRootNode();
                    if (!"test".equals(root.getName())) {
                        return null;
                    }
                    return root instanceof BytecodeRootNode bytecodeRoot ? bytecodeRoot.getBytecodeNode() : ((SLRootNode) root).getBodyNode();
                });
                SLContext.get(null).getFunctionRegistry().getFunction(SLStrings.constant("observe")).getCallTarget().call(caller, checker);
            });
            for (int i = 0; i < 3; i++) {
                Value result = function.execute(object);
                if (expected instanceof String) {
                    assertEquals(expected, result.asString());
                } else {
                    assertEquals(expected, result.asInt());
                }
                if (mode.isBytecode()) {
                    // Explicit interop storage is retained just like @StoreBytecodeIndex.
                    int bci = bytecode[0].getBytecodeIndex(frame[0]);
                    assertTrue(bci >= 0);
                    assertEquals(location, bytecode[0].getSourceLocation(bci).getCharacters().toString());
                }
            }
            assertTrue(visits[0] >= 3);

            throwing[0] = true;
            try {
                function.execute(object);
                fail("Expected an interop exception");
            } catch (PolyglotException ex) {
                assertTrue(ex.isGuestException() || ex.isHostException());
                org.graalvm.polyglot.SourceSection source = null;
                for (PolyglotException.StackFrame stackFrame : ex.getPolyglotStackTrace()) {
                    if (stackFrame.isGuestFrame() && "test".equals(stackFrame.getRootName())) {
                        source = stackFrame.getSourceLocation();
                        break;
                    }
                }
                assertNotNull(source);
                if (mode.isBytecode()) {
                    assertEquals(location, source.getCharacters().toString());
                    // A throwing conversion must leave its index available to stack unwinding.
                    assertTrue(bytecode[0].getBytecodeIndex(frame[0]) >= 0);
                }
            }
        }
    }

    private static final class MemberObject implements ProxyObject {
        private final Runnable callback;

        MemberObject(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public Object getMember(String key) {
            callback.run();
            return 42;
        }

        @Override
        public Object getMemberKeys() {
            return new String[]{"x"};
        }

        @Override
        public boolean hasMember(String key) {
            return "x".equals(key);
        }

        @Override
        public void putMember(String key, Value value) {
            callback.run();
            assertEquals(42, value.asInt());
        }
    }

    private static final class ArrayObject implements ProxyArray {
        private final Runnable callback;

        ArrayObject(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public Object get(long index) {
            callback.run();
            return 42;
        }

        @Override
        public void set(long index, Value value) {
            callback.run();
            assertEquals(42, value.asInt());
        }

        @Override
        public long getSize() {
            return 1;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Checker implements TruffleObject {
        private final Runnable callback;

        Checker(Runnable callback) {
            this.callback = callback;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            callback.run();
            return SLNull.SINGLETON;
        }
    }

    @SuppressWarnings("serial")
    private static final class InteropException extends AbstractTruffleException {
        InteropException() {
            super("interop failure");
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ForeignName implements TruffleObject {
        private final Runnable callback;

        ForeignName(Runnable callback) {
            this.callback = callback;
        }

        @ExportMessage
        boolean isNull() {
            callback.run();
            return false;
        }
    }

}
