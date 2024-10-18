/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.common;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings({"static-method", "unused"})
public class InteropMapObject implements TruffleObject {

    private final Class<? extends TruffleLanguage<?>> languageClass;
    private final Map<String, Object> map;

    public InteropMapObject(Class<? extends TruffleLanguage<?>> languageClass) {
        this.languageClass = languageClass;
        this.map = new HashMap<>();
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    static class ReadMember {

        @Specialization(guards = "interop.isString(member)")
        static Object read(InteropMapObject receiver, Object member,
                        @Cached.Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) throws UnsupportedMessageException {
            String name = interop.asString(member);
            return receiver.get(name);
        }
    }

    @ExportMessage
    static class WriteMember {

        @Specialization(guards = "interop.isString(member)")
        static void write(InteropMapObject receiver, Object member, Object value,
                        @Cached.Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) throws UnsupportedMessageException {
            String name = interop.asString(member);
            receiver.put(name, value);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public Object get(String member) {
        return map.get(member);
    }

    @CompilerDirectives.TruffleBoundary
    public void put(String key, Object value) {
        map.put(key, value);
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberRemovable")
    @ExportMessage(name = "isMemberModifiable")
    static class IsMemberExisting {

        @Specialization(guards = "interop.isString(member)")
        static boolean isExisting(InteropMapObject receiver, Object member,
                        @Cached.Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) {
            String name;
            try {
                name = interop.asString(member);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw CompilerDirectives.shouldNotReachHere();
            }
            return receiver.contains(name);
        }
    }

    @ExportMessage(name = "isMemberInsertable")
    static class IsNotMemberExisting {

        @Specialization(guards = "interop.isString(member)")
        static boolean isNotExisting(InteropMapObject receiver, Object member,
                        @Cached.Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) {
            String name;
            try {
                name = interop.asString(member);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw CompilerDirectives.shouldNotReachHere();
            }
            return !receiver.contains(name);
        }
    }

    @ExportMessage
    static class RemoveMember {

        @Specialization(guards = "interop.isString(member)")
        static void remove(InteropMapObject receiver, Object member,
                        @Cached.Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) throws UnsupportedMessageException {
            String name = interop.asString(member);
            receiver.remove(name);
        }
    }

    @CompilerDirectives.TruffleBoundary
    void remove(String key) {
        map.remove(key);
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    Object getMemberObjects() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @CompilerDirectives.TruffleBoundary
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @ExportMessage
    boolean hasLanguage() {
        return languageClass != null;
    }

    @ExportMessage
    public Class<? extends TruffleLanguage<?>> getLanguage() {
        return languageClass;
    }

    @ExportMessage
    Object toDisplayString(boolean allowSideEffects) {
        return mapToString(map);
    }

    @CompilerDirectives.TruffleBoundary
    private static String mapToString(Map<String, Object> m) {
        return m.toString();
    }
}
