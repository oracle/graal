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

import com.oracle.truffle.api.CompilerDirectives;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractLibraryTest;

@SuppressWarnings({"unused", "static-method"})
public class NodeAdoptionTest extends AbstractLibraryTest {

    @GenerateLibrary
    abstract static class NodeAdoptionLibrary extends Library {
        public String m0(Object receiver) {
            return "default";
        }
    }

    @ExportLibrary(NodeAdoptionLibrary.class)
    static final class NodeAdoptionObject {

        @ExportMessage
        static class M0 {
            @Specialization(guards = "innerNode.execute(receiver)")
            @CompilerDirectives.TruffleBoundary
            static String doM0(NodeAdoptionObject receiver,
                            @Cached(allowUncached = true) InnerNode innerNode,
                            @Cached(value = "0", uncached = "1") int cached) {
                if (cached == 0) {
                    assertNotNull(innerNode.getRootNode());
                    return "cached";
                } else {
                    return "uncached";
                }
            }
        }
    }

    abstract static class InnerNode extends Node {

        abstract boolean execute(Object argument);

        @Specialization
        @CompilerDirectives.TruffleBoundary
        boolean s0(Object argument) {
            assertNotNull(this.getRootNode());
            return true;
        }

    }

    @Test
    public void testDefault() {
        Object o = new Object();

        // defaults are cached as singleton and don't have a parent.
        NodeAdoptionLibrary cached = LibraryFactory.resolve(NodeAdoptionLibrary.class).create(o);
        assertEquals("default", cached.m0(o));

        NodeAdoptionLibrary dispatched = LibraryFactory.resolve(NodeAdoptionLibrary.class).createDispatched(3);
        assertEquals("default", dispatched.m0(o));
    }

    @Test
    public void testExports() {
        NodeAdoptionObject o = new NodeAdoptionObject();

        NodeAdoptionLibrary cached = LibraryFactory.resolve(NodeAdoptionLibrary.class).create(new NodeAdoptionObject());
        assertAssertionError(() -> cached.m0(o));
        adoptNode(cached);
        assertEquals("cached", cached.m0(o));

        NodeAdoptionLibrary dispatched = LibraryFactory.resolve(NodeAdoptionLibrary.class).createDispatched(3);
        assertAssertionError(() -> dispatched.m0(o));
        adoptNode(dispatched);
        assertEquals("cached", dispatched.m0(o));
    }

}
