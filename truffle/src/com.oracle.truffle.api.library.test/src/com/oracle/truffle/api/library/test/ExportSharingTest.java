/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractLibraryTest;

public class ExportSharingTest extends AbstractLibraryTest {

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class ExportSharingLibrary extends Library {

        public String m0(Object receiver) {
            return "m0_default";
        }

        public String m1(Object receiver) {
            return "m1_default";
        }

        public String m2(Object receiver) {
            return "m2_default";
        }

        public String m3(Object receiver) {
            return "m3_default";
        }
    }

    @ExportLibrary(ExportSharingLibrary.class)
    @SuppressWarnings("static-method")
    static final class TestSingletonCached {

        @ExportMessage
        String m0() {
            return "m0";
        }

        @ExportMessage
        String m1() {
            return "m1";
        }

    }

    @Test
    public void testTrivialSingletons() {
        ExportSharingLibrary library1;
        ExportSharingLibrary library2;

        TestSingletonCached obj = new TestSingletonCached();

        library1 = getUncached(ExportSharingLibrary.class, obj);
        library2 = getUncached(ExportSharingLibrary.class, obj);
        assertSame(library1, library2);

        library1 = createCached(ExportSharingLibrary.class, obj);
        library2 = createCached(ExportSharingLibrary.class, obj);
        assertSame(library1, library2);
    }

    @GenerateUncached
    abstract static class TestCached1Node extends Node {

        abstract String execute();

        @Specialization(rewriteOn = ArithmeticException.class)
        static String s0() throws ArithmeticException {
            return "s0_cached";
        }

        @Specialization(replaces = "s0")
        static String s1() {
            // only reachable with uncached versions
            return "s1_uncached";
        }

    }

    @ExportLibrary(ExportSharingLibrary.class)
    static class TestSimpleCached {

        @ExportMessage
        String m0(@Cached TestCached1Node node) {
            return node.execute();
        }

        @ExportMessage
        String m1() {
            return "m1";
        }

    }

    @Test
    public void testCachedNodeAdoption() {
        TestSimpleCached obj = new TestSimpleCached();
        ExportSharingLibrary library1 = createCached(ExportSharingLibrary.class, obj);
        ExportSharingLibrary library2 = createCached(ExportSharingLibrary.class, obj);
        assertFalse(library1.getChildren().iterator().hasNext());
        assertFalse(library2.getChildren().iterator().hasNext());
        assertTrue(library1.accepts(obj));
        assertTrue(library2.accepts(obj));
        assertFalse(library1.accepts(""));
        assertFalse(library2.accepts(""));

        assertNotSame(library1, library2);
        assertNotEquals(library1, library2);

        library1.m0(obj);
        library2.m0(obj);

        Iterator<Node> node = library1.getChildren().iterator();
        assertTrue(node.hasNext());
        Node child = node.next();
        assertSame(library1, child.getParent());
        assertTrue(child instanceof TestCached1Node);

        node = library2.getChildren().iterator();
        assertTrue(node.hasNext());
        Node otherChild = node.next();
        assertSame(library2, otherChild.getParent());
        assertTrue(otherChild instanceof TestCached1Node);
    }

    @ExportLibrary(ExportSharingLibrary.class)
    static class TestSharedCached {

        @ExportMessage
        String m0(@Exclusive @Cached TestCached1Node node) {
            return node.execute();
        }

        @ExportMessage
        String m1(@Exclusive @Cached TestCached1Node node) {
            return node.execute();
        }

    }

    @ExportLibrary(ExportSharingLibrary.class)
    static class TestSharedCachedWithArgs {

        @ExportMessage
        String m0(@Exclusive @Cached TestCached1Node node) {
            return node.execute();
        }

        @ExportMessage
        String m1(@SuppressWarnings("unused") @Exclusive @Cached TestCached1Node node) {
            return "m1";
        }

    }

}
