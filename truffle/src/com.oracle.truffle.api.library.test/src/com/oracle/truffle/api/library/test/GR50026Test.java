/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.test.AbstractLibraryTest;

/*
 * Test for GR-50026 where cached library singletons were generated when an instance was needed.
 */
@SuppressWarnings({"unused", "static-method"})
public class GR50026Test extends AbstractLibraryTest {

    @Test
    public void testBindNode() {
        var o = new TestBindNode();
        LibraryFactory<GR50026TestLibrary> f = LibraryFactory.resolve(GR50026TestLibrary.class);
        GR50026TestLibrary lib0 = f.create(o);
        GR50026TestLibrary lib1 = f.create(o);

        assertNotSame(lib0, lib1);
        assertTrue(lib0.isAdoptable());
        assertTrue(lib1.isAdoptable());
    }

    @Test
    public void testNodeWithLibrary() {
        var o = new TestNodeWithLibrary();
        LibraryFactory<GR50026TestLibrary> f = LibraryFactory.resolve(GR50026TestLibrary.class);
        GR50026TestLibrary lib0 = f.create(o);
        GR50026TestLibrary lib1 = f.create(o);

        assertNotSame(lib0, lib1);
        assertTrue(lib0.isAdoptable());
        assertTrue(lib1.isAdoptable());
    }

    @Test
    public void testStateObject() {
        var o = new TestStateObject();
        LibraryFactory<GR50026TestLibrary> f = LibraryFactory.resolve(GR50026TestLibrary.class);
        GR50026TestLibrary lib0 = f.create(o);
        GR50026TestLibrary lib1 = f.create(o);

        assertNotSame(lib0, lib1);
        assertTrue(lib0.isAdoptable());
        assertTrue(lib1.isAdoptable());
    }

    @Test
    public void testSingletonObject() {
        var o = new TestSingletonObject();
        LibraryFactory<GR50026TestLibrary> f = LibraryFactory.resolve(GR50026TestLibrary.class);
        GR50026TestLibrary lib0 = f.create(o);
        GR50026TestLibrary lib1 = f.create(o);

        assertSame(lib0, lib1);
        assertFalse(lib0.isAdoptable());
        assertFalse(lib1.isAdoptable());
    }

    // no singleton cached node
    @ExportLibrary(GR50026TestLibrary.class)
    public static final class TestBindNode {

        @ExportMessage
        String m0(@Bind Node node) {
            Assert.assertTrue(node.isAdoptable());
            return "m0";
        }
    }

    // no singleton cached node
    @ExportLibrary(GR50026TestLibrary.class)
    public static final class TestNodeWithLibrary {

        @ExportMessage
        String m0(@CachedLibrary("this") GR50026TestLibrary node) {
            Assert.assertTrue(node.isAdoptable());
            return "m0";
        }
    }

    // no singleton cached node
    @ExportLibrary(GR50026TestLibrary.class)
    public static final class TestStateObject {

        @ExportMessage
        /*
         * Suppress only for testing. If this warning ever becomes an error this test probably needs
         * to be rewritten. The intention is to test that no singleton is created for the cached
         * version if inlined nodes are used.
         */
        @SuppressWarnings("truffle")
        String m0(@Cached BranchProfile node) {
            return "m0";
        }
    }

    // this should still produce a singleton cached node.
    @ExportLibrary(GR50026TestLibrary.class)
    public static final class TestSingletonObject {

        @ExportMessage
        @SuppressWarnings("truffle")
        String m0() {
            return "m0";
        }
    }

    @GenerateLibrary
    abstract static class GR50026TestLibrary extends Library {

        public abstract String m0(Object receiver);

    }

}
