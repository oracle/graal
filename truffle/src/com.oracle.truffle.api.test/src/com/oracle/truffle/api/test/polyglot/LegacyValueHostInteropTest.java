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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.Data;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.TypeLiteral;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"deprecation", "truffle-abstract-export"})
public class LegacyValueHostInteropTest extends AbstractPolyglotTest {

    private static final TypeLiteral<Map<String, String>> MAP_STRING_STRING = new TypeLiteral<>() {
    };

    @Before
    public void initObjects() {
        setupEnv();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRemoveMap() {
        int size = 15;
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            char c = (char) ('a' + i);
            map.put(new String(new char[]{c}), new String(new char[]{c, c}));
        }
        Map<String, String> jmap = context.asValue(new LegacyRemoveKeysObject(map)).as(MAP_STRING_STRING);
        assertEquals(size, jmap.size());
        String value = jmap.remove("a");
        assertEquals("aa", value);
        assertEquals(size - 1, jmap.size());
        boolean success = jmap.remove("b", "c");
        assertFalse(success);
        assertEquals(size - 1, jmap.size());
        success = jmap.remove("b", "bb");
        assertTrue(success);
        assertEquals(size - 2, jmap.size());
        // Set
        Set<String> keySet = jmap.keySet();
        success = keySet.remove("c");
        assertTrue(success);
        assertEquals(size - 3, jmap.size());
        success = keySet.remove("xx");
        assertFalse(success);
        assertEquals(size - 3, jmap.size());
        assertEquals(size - 3, keySet.size());
        // Set Iterator
        Iterator<String> siter = keySet.iterator();
        try {
            siter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals("d", siter.next());
        siter.remove();
        assertEquals(size - 4, jmap.size());
        try {
            siter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals("e", siter.next());
        siter.remove();
        assertEquals(size - 5, jmap.size());
        // Entry Set
        Set<Map.Entry<String, String>> entrySet = jmap.entrySet();
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("f", "ff"));
        assertTrue(success);
        assertEquals(size - 6, jmap.size());
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("g", "xx"));
        assertFalse(success);
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("xx", "gg"));
        assertFalse(success);
        assertEquals(size - 6, jmap.size());
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("g", "gg"));
        assertTrue(success);
        assertEquals(size - 7, jmap.size());
        assertEquals(size - 7, entrySet.size());
        // Entry Set Iterator
        Iterator<Map.Entry<String, String>> esiter = entrySet.iterator();
        try {
            esiter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        Map.Entry<String, String> nextEntry = esiter.next();
        assertEquals("h", nextEntry.getKey());
        assertEquals("hh", nextEntry.getValue());
        esiter.remove();
        assertEquals(size - 8, jmap.size());
        // Values
        Collection<String> values = jmap.values();
        success = values.remove("ii");
        assertTrue(success);
        assertEquals(size - 9, jmap.size());
        success = values.remove("xxx");
        assertFalse(success);
        assertEquals(size - 9, jmap.size());
        // Values Iterator
        Iterator<String> viter = values.iterator();
        try {
            viter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals("jj", viter.next());
        viter.remove();
        assertEquals(size - 10, jmap.size());
        assertEquals(size - 10, values.size());

        Data data = new Data();
        data.map = data;
        Map<String, Object> dmap = context.asValue(data).as(Map.class);
        try {
            dmap.remove("x");
            fail("Remove of object fields is not supported.");
        } catch (UnsupportedOperationException e) {
            // O.K.
        }
        assertTrue("Remove of object fields is not supported.", dmap.containsKey("x"));
    }

    @ExportLibrary(InteropLibrary.class)
    static class ListArray implements TruffleObject {

        private final List<String> array;

        ListArray(List<String> array) {
            this.array = array;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final Object readArrayElement(long index) {
            return array.get((int) index);
        }

        @ExportMessage
        @TruffleBoundary
        final void removeArrayElement(long index) {
            array.remove((int) index);
        }

        @ExportMessage
        @TruffleBoundary
        final long getArraySize() {
            return array.size();
        }

        @ExportMessage(name = "isArrayElementReadable")
        @ExportMessage(name = "isArrayElementRemovable")
        @TruffleBoundary
        final boolean isArrayElementExisting(long index) {
            return index < array.size() && index >= 0;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static final class LegacyRemoveKeysObject implements TruffleObject {
        private final Map<String, ?> keys;

        LegacyRemoveKeysObject(Map<String, ?> keys) {
            this.keys = keys;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            List<String> list = new ValueHostInteropTest.CustomList(LegacyRemoveKeysObject.this.keys.keySet());
            return new ListArray(list);
        }

        @ExportMessage(name = "isMemberReadable")
        @ExportMessage(name = "isMemberRemovable")
        @CompilerDirectives.TruffleBoundary
        boolean isMemberExisting(String member) {
            return keys.containsKey(member);
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            if (!keys.containsKey(member)) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(member);
            }
            return keys.get(member);
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        void removeMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            if (!keys.containsKey(member)) {
                throw UnknownIdentifierException.create(member);
            }
            keys.remove(member);
        }

    }

}
