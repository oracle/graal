/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.test.SpecializationSlowPathOnlyModeTestFactory.SpecializationTestingTestNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationSlowPathOnlyModeTestFactory.SpecializationTestingWithCachedTestNodeGen;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

public class SpecializationSlowPathOnlyModeTest {

    @AlwaysGenerateOnlySlowPath
    abstract static class SpecializationTestingTestNode extends Node {

        abstract String execute(int arg);

        @Specialization(guards = "arg == 0")
        public String s0(@SuppressWarnings("unused") int arg) {
            return "fast";
        }

        @Specialization(replaces = "s0")
        public String s1(@SuppressWarnings("unused") int arg) {
            return "generic";
        }
    }

    @AlwaysGenerateOnlySlowPath
    abstract static class SpecializationTestingWithCachedTestNode extends Node {

        abstract boolean execute(int arg);

        @Specialization(guards = "arg == 0")
        @SuppressWarnings("unused")
        public boolean s0(int arg,
                        @Shared("argLib") @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Shared("branch") @Cached BranchProfile profile) {
            return false;
        }

        @Specialization(replaces = "s0")
        @SuppressWarnings("unused")
        public boolean s1(int arg,
                        @Shared("argLib") @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Shared("branch") @Cached BranchProfile profile) {
            profile.enter();
            return argLib.fitsInByte(arg);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @AlwaysGenerateOnlySlowPath
    static class SpecializationTestingTruffleObj implements TruffleObject {
        @ExportMessage
        public boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        public long getArraySize() {
            return 0L;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        public void removeArrayElement(long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @SuppressWarnings("unused")
        public boolean isArrayElementReadable(long index) {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        public boolean isArrayElementModifiable(long index) {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        public boolean isArrayElementInsertable(long index) {
            return false;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        public boolean isArrayElementRemovable(long index,
                        @Shared("branch1") @Cached BranchProfile branch1) {
            branch1.enter();
            return true;
        }

        @ExportMessage
        @AlwaysGenerateOnlySlowPath
        static class ReadArrayElement {
            @Specialization(guards = "index == 0")
            @SuppressWarnings("unused")
            static Object doCached(SpecializationTestingTruffleObj receiver, long index,
                            @Shared("branch1") @Cached BranchProfile branch1,
                            @Shared("branch2") @Cached BranchProfile branch2,
                            @Shared("branch3") @Cached BranchProfile branch3) {
                return "fast";
            }

            @Specialization(replaces = "doCached")
            @SuppressWarnings("unused")
            static Object doGeneric(SpecializationTestingTruffleObj receiver, long index,
                            @Shared("branch2") @Cached BranchProfile branch2) {
                branch2.enter();
                return "generic";
            }
        }

        @ExportMessage
        @AlwaysGenerateOnlySlowPath
        static class WriteArrayElement {
            @Specialization
            @SuppressWarnings("unused")
            static void doIt(SpecializationTestingTruffleObj receiver, long index, Object value,
                            @Shared("branch3") @Cached BranchProfile branch3) {
                branch3.enter();
            }
        }
    }

    @Test
    public void testNoFastPathIsExecuted() {
        SpecializationTestingTestNode node = SpecializationTestingTestNodeGen.create();
        assertEquals("generic", node.execute(0));
        assertEquals("generic", node.execute(42));
    }

    @Test
    public void testNoFastPathIsExecutedWithCached() {
        SpecializationTestingWithCachedTestNode node = SpecializationTestingWithCachedTestNodeGen.create();
        assertTrue(node.execute(0));
        assertTrue(node.execute(42));
    }

    @Test
    public void testNoFastPathIsExecutedWithLibrary() throws InvalidArrayIndexException, UnsupportedMessageException, UnsupportedTypeException {
        SpecializationTestingTruffleObj obj = new SpecializationTestingTruffleObj();
        InteropLibrary lib = InteropLibrary.getUncached(obj);

        assertTrue(lib.isArrayElementRemovable(obj, 1));
        assertEquals("generic", lib.readArrayElement(obj, 0));
        lib.writeArrayElement(obj, 0, "dummy");
    }
}
