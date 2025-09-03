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
package com.oracle.truffle.api.test.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

import org.junit.Test;

/**
 * Test of node inlinig into multiple messages.
 */
@SuppressWarnings("static-method")
public final class NodeInlineTest extends InteropLibraryBaseTest {

    @Test
    public void testInlined() {
        Hash h = new Hash();
        InteropLibrary library = createLibrary(InteropLibrary.class, h);
        library.isHashEntryReadable(h, 41L);
        library.isHashEntryModifiable(h, 42.42);
        library.isHashEntryInsertable(h, 43L);
        library.isHashEntryReadable(h, 43.43);
        library.isHashEntryModifiable(h, 40L);
        library.isHashEntryInsertable(h, 41.41);
    }

    @ExportLibrary(InteropLibrary.class)
    static class Hash implements TruffleObject {

        static final int LIMIT = 5;

        @ExportMessage
        final boolean hasHashEntries() {
            return true;
        }

        @ExportMessage
        final long getHashSize() {
            return 42L;
        }

        @ExportMessage
        static class IsHashEntryReadable {

            @Specialization(guards = {"compareNode.execute(node, key, cachedKey)"}, limit = "LIMIT")
            static boolean doCached(@SuppressWarnings("unused") Hash receiver, @SuppressWarnings("unused") Object key,
                            @SuppressWarnings("unused") @Bind Node node,
                            @SuppressWarnings("unused") @Cached("key") Object cachedKey,
                            @SuppressWarnings("unused") @Exclusive @Cached CompareInlineNode compareNode,
                            @Cached("doGeneric(receiver, key)") boolean cachedReadable) {
                return cachedReadable;
            }

            @Specialization(replaces = "doCached", guards = "!receiver.isNull()")
            static boolean doGeneric(@SuppressWarnings("unused") Hash receiver, Object key,
                            @Bind Node node,
                            @Shared("keyLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary keyLibrary,
                            @Exclusive @Cached InlinedBranchProfile seenLong,
                            @Exclusive @Cached InlinedBranchProfile seenDouble,
                            @Exclusive @Cached InlinedBranchProfile seenString) {
                if (key instanceof Long) {
                    seenLong.enter(node);
                    return ((Long) key) > 42L;
                } else if (key instanceof Double) {
                    seenDouble.enter(node);
                    return ((Double) key) < 42L;
                } else {
                    seenString.enter(node);
                    String name;
                    try {
                        name = keyLibrary.asString(key);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    return name.length() > 42;
                }
            }

            static boolean doGeneric(Hash receiver, Object key) {
                return doGeneric(receiver, key, null, InteropLibrary.getUncached(), InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached());
            }
        }

        @ExportMessage
        final Object readHashValue(@SuppressWarnings("unused") Object key) {
            return 42;
        }

        @ExportMessage
        static class IsHashEntryInsertable implements TruffleObject {

            @Specialization(guards = {"compareNode.execute(node, key, cachedKey)"}, limit = "LIMIT")
            static boolean doCached(@SuppressWarnings("unused") Hash receiver, @SuppressWarnings("unused") Object key,
                            @SuppressWarnings("unused") @Bind Node node,
                            @SuppressWarnings("unused") @Cached("key") Object cachedKey,
                            @SuppressWarnings("unused") @Exclusive @Cached CompareInlineNode compareNode,
                            @Cached("doGeneric(receiver, key)") boolean cachedInsertable) {
                return cachedInsertable;
            }

            @Specialization(replaces = "doCached", guards = "!receiver.isNull()")
            static boolean doGeneric(@SuppressWarnings("unused") Hash receiver, Object key,
                            @Bind Node node,
                            @Shared("keyLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary keyLibrary,
                            @Exclusive @Cached InlinedBranchProfile seenLong,
                            @Exclusive @Cached InlinedBranchProfile seenDouble,
                            @Exclusive @Cached InlinedBranchProfile seenString) {
                if (key instanceof Long) {
                    seenLong.enter(node);
                    return ((Long) key) < 42L;
                } else if (key instanceof Double) {
                    seenDouble.enter(node);
                    return ((Double) key) > 43L;
                } else {
                    seenString.enter(node);
                    String name;
                    try {
                        name = keyLibrary.asString(key);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    return name.length() < 42;
                }
            }

            static boolean doGeneric(Hash receiver, Object key) {
                return doGeneric(receiver, key, null, InteropLibrary.getUncached(), InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached());
            }
        }

        @ExportMessage
        static class IsHashEntryModifiable implements TruffleObject {

            @Specialization(guards = {"compareNode.execute(node, key, cachedKey)"}, limit = "LIMIT")
            static boolean doCached(@SuppressWarnings("unused") Hash receiver, @SuppressWarnings("unused") Object key,
                            @SuppressWarnings("unused") @Bind Node node,
                            @SuppressWarnings("unused") @Cached("key") Object cachedKey,
                            @SuppressWarnings("unused") @Exclusive @Cached CompareInlineNode compareNode,
                            @Cached("doGeneric(receiver, key)") boolean cachedModifiable) {
                return cachedModifiable;
            }

            @Specialization(replaces = "doCached", guards = "!receiver.isNull()")
            static boolean doGeneric(@SuppressWarnings("unused") Hash receiver, Object key,
                            @Bind Node node,
                            @Shared("keyLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary keyLibrary,
                            @Exclusive @Cached InlinedBranchProfile seenLong,
                            @Exclusive @Cached InlinedBranchProfile seenDouble,
                            @Exclusive @Cached InlinedBranchProfile seenString) {
                if (key instanceof Long) {
                    seenLong.enter(node);
                    return ((Long) key) == 42L;
                } else if (key instanceof Double) {
                    seenDouble.enter(node);
                    return ((Double) key) == 42.42;
                } else {
                    seenString.enter(node);
                    String name;
                    try {
                        name = keyLibrary.asString(key);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    return name.length() == 42;
                }
            }

            static boolean doGeneric(Hash receiver, Object key) {
                return doGeneric(receiver, key, null, InteropLibrary.getUncached(), InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached());
            }
        }

        @ExportMessage
        final void writeHashEntry(@SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
            return;
        }

        @ExportMessage
        final Object getHashEntriesIterator() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        final boolean isNull() {
            return false;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CompareInlineNode extends Node {

        public abstract boolean execute(Node node, Object o1, Object o2);

        @Specialization
        static boolean doLong(Long l1, Long l2) {
            return l1 % 42 == l2 % 42;
        }

        @Specialization
        static boolean doDouble(Double d1, Double d2) {
            return Math.abs(d1 - d2) < 0.42;
        }

        @Fallback
        static boolean doOther(Object obj, Object cachedObj,
                        @CachedLibrary(limit = "3") InteropLibrary objLibrary,
                        @CachedLibrary(limit = "3") InteropLibrary cachedLibrary,
                        @Cached(inline = false) TruffleString.EqualNode stringEquals) {
            if (!objLibrary.isString(obj)) {
                return false;
            }
            if (!cachedLibrary.isString(cachedObj)) {
                return false;
            }
            TruffleString a;
            try {
                a = objLibrary.asTruffleString(obj);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("incompatible object");
            }
            TruffleString b;
            try {
                b = cachedLibrary.asTruffleString(cachedObj);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("incompatible object");
            }
            return stringEquals.execute(a, b, TruffleString.Encoding.UTF_8);
        }

    }

}
