/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.utilities.TriState;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HashTest extends AbstractPolyglotTest {

    @Test
    public void testTruffleObjectInteropMessages() throws InteropException {
        setupEnv();
        for (KeyFactory<?> factory : KeyFactory.ALL) {
            testInteropMessages(new Hash(), factory);
        }
    }

    @Test
    public void testHostObjectInteropMessages() throws InteropException {
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Accessor accessor = new Accessor();
        Value accessorValue = context.asValue(accessor);
        for (KeyFactory<?> factory : KeyFactory.ALL) {
            accessorValue.execute(context.asValue(new HashMap<>()));
            testInteropMessages(accessor.object, factory);
        }
    }

    @Test
    public void testPolyglotProxyInteropMessages() throws InteropException {
        setupEnv();
        Accessor accessor = new Accessor();
        Value accessorValue = context.asValue(accessor);
        for (KeyFactory<?> factory : KeyFactory.ALL) {
            accessorValue.execute(context.asValue(ProxyHashMap.fromMap(new HashMap<>())));
            testInteropMessages(accessor.object, factory);
        }
    }

    private static void testInteropMessages(Object hash, KeyFactory<?> keyFactory) throws InteropException {
        final int count = 100;
        final int inc = 2;
        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.hasHashEntries(hash));
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            Object value = i;
            assertNonExisting(hash, key, interop);
            interop.writeHashEntry(hash, key, value);
        }
        assertEquals((count / inc), interop.getHashSize(hash));
        for (int i = 0; i < count; i++) {
            Object key = keyFactory.create(i);
            if ((i & 1) == 0) {
                Object expectedValue = i;
                assertExisting(hash, key, interop);
                assertEquals(expectedValue, interop.readHashValue(hash, key));
            } else {
                assertNonExisting(hash, key, interop);
            }
        }
        Map<Object, Integer> expected = new HashMap<>();
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            int value = -1 * i;
            interop.writeHashEntry(hash, key, value);
            assertExisting(hash, key, interop);
            assertEquals(value, interop.readHashValue(hash, key));
            expected.put(key, value);
        }
        Object iterator = interop.getHashEntriesIterator(hash);
        assertTrue(interop.isIterator(iterator));
        Map<Object, Integer> expected2 = new HashMap<>();
        while (interop.hasIteratorNextElement(iterator)) {
            Object entry = interop.getIteratorNextElement(iterator);
            assertTrue(interop.isHashEntry(entry));
            Object key = interop.getHashEntryKey(entry);
            int value = (int) interop.getHashEntryValue(entry);
            int expectedValue = expected.remove(key);
            assertEquals(expectedValue, value);
            int newValue = -1 * value;
            interop.setHashEntryValue(entry, newValue);
            expected2.put(key, newValue);
        }
        assertTrue(expected.isEmpty());
        iterator = interop.getHashEntriesIterator(hash);
        while (interop.hasIteratorNextElement(iterator)) {
            Object entry = interop.getIteratorNextElement(iterator);
            Object key = interop.getHashEntryKey(entry);
            int value = (int) interop.getHashEntryValue(entry);
            int expectedValue = expected2.remove(key);
            assertEquals(expectedValue, value);
        }
        assertTrue(expected2.isEmpty());

        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            interop.removeHashEntry(hash, key);
            assertNonExisting(hash, key, interop);
        }
        assertEquals(0, interop.getHashSize(hash));
    }

    private static void assertNonExisting(Object hash, Object key, InteropLibrary interop) {
        assertFalse(interop.isHashEntryExisting(hash, key));
        assertFalse(interop.isHashEntryModifiable(hash, key));
        assertFalse(interop.isHashEntryRemovable(hash, key));
        assertTrue(interop.isHashEntryInsertable(hash, key));
        assertTrue(interop.isHashEntryWritable(hash, key));
    }

    private static void assertExisting(Object hash, Object key, InteropLibrary interop) {
        assertTrue(interop.isHashEntryExisting(hash, key));
        assertTrue(interop.isHashEntryModifiable(hash, key));
        assertTrue(interop.isHashEntryRemovable(hash, key));
        assertFalse(interop.isHashEntryInsertable(hash, key));
        assertTrue(interop.isHashEntryWritable(hash, key));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testValues() {
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        for (KeyFactory<?> factory : KeyFactory.ALL) {
            testValueImpl(context.asValue(new Hash()), (KeyFactory<Object>) factory, context);
            testValueImpl(context.asValue(new HashMap<>()), (KeyFactory<Object>) factory, context);
            testValueImpl(context.asValue(ProxyHashMap.fromMap(new HashMap<>())), (KeyFactory<Object>) factory, context);
        }
    }

    private static void testValueImpl(Value hash, KeyFactory<Object> keyFactory, Context context) {
        final int count = 100;
        final int inc = 2;
        assertTrue(hash.hasHashEntries());
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            Object value = i;
            assertFalse(hash.hasHashEntry(key));
            hash.putHashEntry(key, value);
        }
        assertEquals((count / inc), hash.getHashSize());
        for (int i = 0; i < count; i++) {
            Object key = keyFactory.create(i);
            if ((i & 1) == 0) {
                Object expectedValue = i;
                assertTrue(hash.hasHashEntry(key));
                assertEquals(expectedValue, hash.getHashValue(key).asInt());
            } else {
                assertFalse(hash.hasHashEntry(key));
            }
        }
        Map<Object, Integer> expected = new HashMap<>();
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            int value = -1 * i;
            hash.putHashEntry(key, value);
            assertTrue(hash.hasHashEntry(key));
            assertEquals(value, hash.getHashValue(key).asInt());
            expected.put(keyFactory.box(context, key), value);
        }
        Value iterator = hash.getHashEntriesIterator();
        assertTrue(iterator.isIterator());
        Map<Object, Integer> expected2 = new HashMap<>();
        while (iterator.hasIteratorNextElement()) {
            Value entry = iterator.getIteratorNextElement();
            assertTrue(entry.isHashEntry());
            Object key = keyFactory.unbox(entry.getHashEntryKey());
            int value = entry.getHashEntryValue().asInt();
            int expectedValue = expected.remove(key);
            assertEquals(expectedValue, value);
            int newValue = -1 * value;
            entry.setHashEntryValue(newValue);
            expected2.put(key, newValue);
        }
        assertTrue(expected.isEmpty());
        iterator = hash.getHashEntriesIterator();
        while (iterator.hasIteratorNextElement()) {
            Value entry = iterator.getIteratorNextElement();
            Object key = keyFactory.unbox(entry.getHashEntryKey());
            int value = entry.getHashEntryValue().asInt();
            int expectedValue = expected2.remove(key);
            assertEquals(expectedValue, value);
        }
        assertTrue(expected2.isEmpty());
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            hash.removeHashEntry(key);
            assertFalse(hash.hasHashEntry(key));
        }
        assertEquals(0, hash.getHashSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPolyglotMap() {
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        for (KeyFactory<?> factory : KeyFactory.ALL) {
            testPolyglotMapImpl(context.asValue(new Hash()), (KeyFactory<Object>) factory, context);
            testPolyglotMapImpl(context.asValue(ProxyHashMap.fromMap(new HashMap<>())), (KeyFactory<Object>) factory, context);
        }
    }

    @SuppressWarnings("unchecked")
    private static void testPolyglotMapImpl(Value hash, KeyFactory<Object> keyFactory, Context context) {
        final int count = 100;
        final int inc = 2;
        assertTrue(hash.hasHashEntries());
        Map<Object, Object> map = hash.as(Map.class);
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            Object value = i;
            assertFalse(map.containsKey(key));
            map.put(key, value);
            assertTrue(hash.hasHashEntry(key));
        }
        assertEquals((count / inc), map.size());
        for (int i = 0; i < count; i++) {
            Object key = keyFactory.create(i);
            if ((i & 1) == 0) {
                Object expectedValue = i;
                assertTrue(map.containsKey(key));
                assertEquals(expectedValue, map.get(key));
            } else {
                assertFalse(map.containsKey(key));
            }
        }
        Map<Object, Integer> expected = new HashMap<>();
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            int value = -1 * i;
            map.put(key, value);
            assertTrue(map.containsKey(key));
            assertTrue(hash.hasHashEntry(key));
            assertEquals(value, map.get(key));
            assertEquals(value, hash.getHashValue(key).asInt());
            expected.put(keyFactory.box(context, key), value);
        }
        Map<Object, Integer> expected2 = new HashMap<>();
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            Object key = entry.getKey();
            int value = (int) entry.getValue();
            int expectedValue = expected.remove(key);
            assertEquals(expectedValue, value);
            int newValue = -1 * value;
            entry.setValue(newValue);
            expected2.put(key, newValue);
        }
        assertTrue(expected.isEmpty());
        iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            Object key = entry.getKey();
            int value = (int) entry.getValue();
            int expectedValue = expected2.remove(key);
            assertEquals(expectedValue, value);
        }
        assertTrue(expected2.isEmpty());
        for (int i = 0; i < count; i += inc) {
            Object key = keyFactory.create(i);
            assertTrue(hash.hasHashEntry(key));
            assertNotNull(map.remove(key));
            assertFalse(hash.hasHashEntry(key));
        }
        assertEquals(0, map.size());
        assertEquals(0, hash.getHashSize());
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HashEntry implements TruffleObject {
        final int hashCode;
        final Object key;
        Object value;

        HashEntry(int hashCode, Object key, Object value) {
            this.hashCode = hashCode;
            this.key = key;
            this.value = value;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isHashEntry() {
            return true;
        }

        @ExportMessage
        Object getHashEntryKey() {
            return key;
        }

        @ExportMessage
        Object getHashEntryValue() {
            return value;
        }

        @ExportMessage
        void setHashEntryValue(Object newValue) {
            this.value = newValue;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HashEntriesIterator implements TruffleObject {

        private final HashEntry[] store;
        private final int size;
        private int emittedCount;
        private int index;

        HashEntriesIterator(HashEntry[] store, int size) {
            this.store = store;
            this.size = size;
            this.index = 0;
            this.emittedCount = 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement() {
            return emittedCount < size;
        }

        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException {
            while (index < store.length) {
                if (store[index] != null) {
                    emittedCount++;
                    return store[index++];
                } else {
                    index++;
                }
            }
            throw StopIterationException.create();
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class Hash implements TruffleObject {

        private static final int INITIAL_CAPACITY = 1 << 12;

        private int size;
        private HashEntry[] store;

        Hash() {
            this.size = 0;
            this.store = new HashEntry[INITIAL_CAPACITY];
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasHashEntries() {
            return true;
        }

        @ExportMessage
        long getHashSize() {
            return size;
        }

        @ExportMessage(name = "isHashValueReadable")
        @ExportMessage(name = "isHashEntryModifiable")
        @ExportMessage(name = "isHashEntryRemovable")
        boolean isHashEntryExisting(Object key) {
            return get(key) != -1;
        }

        @ExportMessage
        boolean isHashEntryInsertable(Object key) {
            return get(key) == -1;
        }

        @ExportMessage
        Object readHashValue(Object key) throws UnknownKeyException {
            int addr = get(key);
            if (addr == -1) {
                throw UnknownKeyException.create(key);
            } else {
                return store[addr].value;
            }
        }

        @ExportMessage
        void writeHashEntry(Object key, Object value) {
            int addr = get(key);
            if (addr != -1) {
                store[addr].value = value;
            } else {
                int hashCode = hashCode(key);
                HashEntry e = new HashEntry(hashCode, key, value);
                if (size >= (int) (store.length * 0.75f)) {
                    copyStore(store.length << 1);
                }
                insert(e);
            }
        }

        @ExportMessage
        void removeHashEntry(Object key) throws UnknownKeyException {
            int addr = get(key);
            if (addr == -1) {
                throw UnknownKeyException.create(key);
            } else {
                store[addr] = null;
                size--;
                copyStore(store.length);
            }
        }

        @ExportMessage
        Object getHashEntriesIterator() {
            return new HashEntriesIterator(store, size);
        }

        private int get(Object key) {
            int hash = hashCode(key);
            int addr = hash % store.length;
            int end = addr;
            do {
                HashEntry e = store[addr];
                if (e == null) {
                    return -1;
                } else if (e.hashCode == hash && equals(e.key, key)) {
                    return addr;
                }
                addr = (addr + 1) % store.length;
            } while (addr != end);
            return -1;
        }

        private void insert(HashEntry e) {
            int addr = e.hashCode % store.length;
            int end = addr;
            do {
                if (store[addr] == null) {
                    store[addr] = e;
                    size++;
                    return;
                }
                addr = (addr + 1) % store.length;
            } while (addr != end);
            throw new IllegalStateException("Full store.");
        }

        @TruffleBoundary
        private void copyStore(int newSize) {
            HashEntry[] oldStore = store;
            store = new HashEntry[newSize];
            size = 0;
            for (HashEntry e : oldStore) {
                if (e != null) {
                    insert(e);
                }
            }
        }

        @TruffleBoundary
        private static boolean equals(Object o1, Object o2) {
            return Objects.equals(o1, o2);
        }

        @TruffleBoundary
        private static int hashCode(Object obj) {
            return Objects.hashCode(obj);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Accessor implements TruffleObject {

        Object object;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object... arguments) {
            object = arguments[0];
            return object;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Key implements TruffleObject {

        private final int value;

        Key(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return value == key.value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @ExportMessage
        TriState isIdenticalOrUndefined(Object other) {
            if (other == this) {
                return TriState.TRUE;
            } else if (other != null && other.getClass() == Key.class) {
                return value == ((Key) other).value ? TriState.TRUE : TriState.FALSE;
            } else {
                return TriState.UNDEFINED;
            }
        }

        @ExportMessage
        int identityHashCode() {
            return value;
        }
    }

    interface KeyFactory<K> {

        KeyFactory<Integer> INT_KEY = new KeyFactory<Integer>() {
            @Override
            public Integer create(int value) {
                return value;
            }

            @Override
            public Integer unbox(Value value) {
                return value.asInt();
            }

            @Override
            public String toString() {
                return "KeyFactory<Integer>";
            }
        };

        KeyFactory<String> STRING_KEY = new KeyFactory<String>() {
            @Override
            public String create(int value) {
                return String.valueOf(value);
            }

            @Override
            public String unbox(Value value) {
                return value.asString();
            }

            @Override
            public String toString() {
                return "KeyFactory<String>";
            }
        };

        KeyFactory<TruffleObject> TRUFFLE_OBJECT_KEY = new KeyFactory<TruffleObject>() {
            @Override
            public TruffleObject create(int value) {
                return new Key(value);
            }

            @Override
            public Object box(Context context, TruffleObject key) {
                return context.asValue(key);
            }

            @Override
            public String toString() {
                return "KeyFactory<TruffleObject>";
            }
        };

        Collection<KeyFactory<?>> ALL = Collections.unmodifiableList(
                        Arrays.asList(INT_KEY, STRING_KEY, TRUFFLE_OBJECT_KEY));

        K create(int value);

        default Object box(@SuppressWarnings("unused") Context context, K key) {
            return key;
        }

        default Object unbox(Value value) {
            return value;
        }
    }
}
