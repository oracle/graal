/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/**
 * In this tutorial we will explore what changed with the new {@link InteropLibrary} member APIs in
 * 24.2 step by step. We start with a minimal implementation that shows the most basic changes in
 * the member related APIs.
 */
@SuppressWarnings({"static-method"})
public class MinimalMemberExample extends AbstractPolyglotTest {

    @ExportLibrary(InteropLibrary.class)
    static final class ObjectWithMembers implements TruffleObject {

        /**
         * Nothing has changed for using the member trait.
         */
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        /**
         * This method must be implemented instead of {@link InteropLibrary#getMembers(Object)}
         * message. The old API required to return a list of strings to identify the members. The
         * new specification allows to return {@link InteropLibrary#isMember(Object) member} objects
         * instead. In this example we skip this part. Please see {@link AdvancedMemberExample} for
         * a an example with enumeration.
         */
        @ExportMessage
        Object getMemberObjects() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        /**
         * Most notable member related messages now take an {@link Object} instead of
         * {@link String}. The next change can be seen when looking at the signature for the member
         * related methods. They now take {@link Object} instead of {@link String}. Generally
         */
        @ExportMessage
        boolean isMemberReadable(Object member, //
                        @Shared @CachedLibrary(limit = "3") InteropLibrary memberLibrary) {
            /**
             * a Compared to the previous API we now need to check whether the member is a string
             * and then convert it using the interop APIs. This allows users of this API to directly
             * read members using TruffleString. An alternative implementation could use the
             * TruffleString API to to compare the string.
             */
            if (memberLibrary.isString(member)) {
                String name;
                try {
                    name = memberLibrary.asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "simpleField1" -> true;
                    case "simpleField2" -> true;
                    default -> false;
                };
            }
            return false;
        }

        @ExportMessage
        Object readMember(Object member, //
                        @Shared @CachedLibrary(limit = "3") InteropLibrary memberLibrary) throws UnknownMemberException {
            if (memberLibrary.isString(member)) {
                String name;
                try {
                    name = memberLibrary.asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "simpleField1" -> 41;
                    case "simpleField2" -> 42;
                    default -> throw UnknownMemberException.create(member);
                };
            }
            throw UnknownMemberException.create(member);
        }

    }

    @Test
    public void test() throws InteropException {
        InteropLibrary interop = InteropLibrary.getUncached();
        Object object = new ObjectWithMembers();
        assertTrue(interop.hasMembers(object));

        assertTrue(interop.isMemberReadable(object, (Object) "simpleField1"));
        assertEquals(41, interop.readMember(object, (Object) "simpleField1"));

        assertTrue(interop.isMemberReadable(object, (Object) "simpleField2"));
        assertEquals(42, interop.readMember(object, (Object) "simpleField2"));

        assertFalse(interop.isMemberReadable(object, (Object) "invalidField"));
    }

    /**
     * Now continue reading {@link ExtensiveMemberExample}.
     */

}
