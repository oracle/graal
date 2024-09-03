/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.test.UncachedEncapsulatedNodeTestFactory.DisabledTestNodeGen;
import com.oracle.truffle.api.library.test.UncachedEncapsulatedNodeTestFactory.Test1NodeGen;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractLibraryTest;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing", "static-method"})
public class UncachedEncapsulatedNodeTest extends AbstractLibraryTest {

    @GenerateLibrary
    public abstract static class EncapsulatedNodeLibrary extends Library {

        public abstract Object m0(Object receiver);

    }

    @ExportLibrary(EncapsulatedNodeLibrary.class)
    static final class TestObject {

        @ExportMessage
        boolean accepts(@Cached("this") TestObject cached) {
            return this == cached;
        }

        @ExportMessage
        Object m0() {
            return EncapsulatingNodeReference.getCurrent().get();
        }

    }

    @Test
    public void testCachedDispatch() {
        EncapsulatedNodeLibrary lib = createCachedDispatch(EncapsulatedNodeLibrary.class, 2);

        assertNull(lib.m0(new TestObject()));
        assertNull(lib.m0(new TestObject()));
        assertSame(lib, lib.m0(new TestObject()));
        assertSame(lib, lib.m0(new TestObject()));
    }

    abstract static class Test1Node extends Node {

        abstract Object execute(Object arg);

        @Specialization(limit = "2")
        Object doLibrary(Object arg,
                        @CachedLibrary("arg") EncapsulatedNodeLibrary library) {
            return library.m0(arg);
        }

    }

    @Test
    public void testDSLNode() {
        Test1Node node = adoptNode(Test1NodeGen.create()).get();
        assertNull(node.execute(new TestObject()));
        assertNull(node.execute(new TestObject()));
        assertSame(node, node.execute(new TestObject()));
        assertSame(node, node.execute(new TestObject()));
    }

    @GenerateLibrary(pushEncapsulatingNode = false)
    public abstract static class DisabledEncapsulatedNodeLibrary extends Library {

        public abstract Object m0(Object receiver);

    }

    @ExportLibrary(DisabledEncapsulatedNodeLibrary.class)
    static final class DisabledTestObject {

        @ExportMessage
        boolean accepts(@Cached("this") DisabledTestObject cached) {
            return this == cached;
        }

        @ExportMessage
        Object m0() {
            return EncapsulatingNodeReference.getCurrent().get();
        }

    }

    @Test
    public void testDisabledCachedDispatch() {
        DisabledEncapsulatedNodeLibrary lib = createCachedDispatch(DisabledEncapsulatedNodeLibrary.class, 2);

        assertNull(lib.m0(new DisabledTestObject()));
        assertNull(lib.m0(new DisabledTestObject()));
        assertNull(lib.m0(new DisabledTestObject()));
        assertNull(lib.m0(new DisabledTestObject()));
    }

    abstract static class DisabledTestNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(limit = "2")
        Object doLibrary(Object arg,
                        @CachedLibrary("arg") DisabledEncapsulatedNodeLibrary library) {
            return library.m0(arg);
        }

    }

    @Test
    public void testDisabledDSLNode() {
        DisabledTestNode node = adoptNode(DisabledTestNodeGen.create()).get();
        assertNull(node.execute(new DisabledTestObject()));
        assertNull(node.execute(new DisabledTestObject()));
        assertNull(node.execute(new DisabledTestObject()));
        assertNull(node.execute(new DisabledTestObject()));
    }

}
