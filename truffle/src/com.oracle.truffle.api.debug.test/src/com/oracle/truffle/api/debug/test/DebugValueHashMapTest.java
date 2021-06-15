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
package com.oracle.truffle.api.debug.test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.test.TestHashObject.HashElement;

/**
 * Test of hash map methods of DebugValue.
 */
public class DebugValueHashMapTest extends AbstractDebugTest {

    @Test
    public void testNoHash() throws Throwable {
        checkDebugValueOf(1, value -> {
            assertFalse(value.hasHashEntries());
            assertFails(() -> value.getHashSize());
            assertFails(() -> value.getHashEntriesIterator());
            assertFails(() -> value.getHashKeysIterator());
            assertFails(() -> value.getHashValuesIterator());
            assertFails(() -> value.getHashValue(value));
            assertFails(() -> value.getHashValueOrDefault(value, value));
            assertFails(() -> value.putHashEntry(value, value));
            assertFails(() -> value.removeHashEntry(value));
            assertFalse(value.isHashEntryExisting(value));
            assertFalse(value.isHashEntryInsertable(value));
            assertFalse(value.isHashEntryModifiable(value));
            assertFalse(value.isHashEntryReadable(value));
            assertFalse(value.isHashEntryRemovable(value));
            assertFalse(value.isHashEntryWritable(value));
        });
    }

    @Test
    public void testEmptyHash() throws Throwable {
        Object hash = new TestHashObject(Collections.emptyMap(), false);
        checkDebugValueOf(hash, value -> {
            assertTrue(value.hasHashEntries());
            assertEquals(0, value.getHashSize());
            assertEquals(null, value.getHashValue(value));
            assertEquals(value, value.getHashValueOrDefault(value, value));
            assertFails(() -> value.putHashEntry(value, value));
            assertFalse(value.removeHashEntry(value));
            DebugValue iterator = value.getHashEntriesIterator();
            DebugValueIteratorTest.checkIntIterator(iterator, Collections.emptyList());
            iterator = value.getHashKeysIterator();
            DebugValueIteratorTest.checkIntIterator(iterator, Collections.emptyList());
            iterator = value.getHashValuesIterator();
            DebugValueIteratorTest.checkIntIterator(iterator, Collections.emptyList());
            assertFalse(value.isHashEntryExisting(value));
            assertFalse(value.isHashEntryInsertable(value));
            assertFalse(value.isHashEntryModifiable(value));
            assertFalse(value.isHashEntryReadable(value));
            assertFalse(value.isHashEntryRemovable(value));
            assertFalse(value.isHashEntryWritable(value));
        });
    }

    @Test
    public void testHashInsertable() throws Throwable {
        Map<HashElement, Object> map = TestHashObject.createTestMap();
        Object hash = new TestHashObject(map, true);
        checkDebugValueOf(hash, value -> {
            assertTrue(value.hasHashEntries());
            assertEquals(8, value.getHashSize());
            DebuggerSession s = value.getSession();
            DebugValue v10 = s.createPrimitiveValue(10, null);
            checkHashEntry(value, v10, true, true, true, true, false, true);

            // Insert a new entry <42, 420>
            DebugValue v42 = s.createPrimitiveValue(42, null);
            checkHashEntry(value, v42, false, false, true, false, true, false);
            value.putHashEntry(v42, s.createPrimitiveValue(420, null));
            checkHashEntry(value, v42, true, true, true, true, false, true);
            assertEquals(9, value.getHashSize());

            // Remove an existing entry
            DebugValue v20 = s.createPrimitiveValue(20, null);
            assertTrue(value.removeHashEntry(v20));
            assertEquals(8, value.getHashSize());
            DebugValue iterator = value.getHashKeysIterator();
            DebugValueIteratorTest.checkIntIterator(iterator, Arrays.asList(10, 30, 40, 50, 60, 70, 80, 42));
        });
    }

    @Test
    public void testHashRWEntries() throws Throwable {
        Map<HashElement, Object> map = TestHashObject.createTestMap();
        Object hash = new TestHashObject(map, false);
        checkDebugValueOf(hash, value -> {
            assertEquals(8, value.getHashSize());
            DebuggerSession s = value.getSession();
            DebugValue v10 = s.createPrimitiveValue(10, null);
            DebugValue v20 = s.createPrimitiveValue(20, null);
            DebugValue v30 = s.createPrimitiveValue(30, null);
            DebugValue v40 = s.createPrimitiveValue(40, null);
            DebugValue v50 = s.createPrimitiveValue(50, null);
            DebugValue v60 = s.createPrimitiveValue(60, null);
            DebugValue v70 = s.createPrimitiveValue(70, null);
            DebugValue v80 = s.createPrimitiveValue(80, null);
            checkHashEntry(value, v10, true, true, true, true, false, true);
            checkHashEntry(value, v20, true, true, false, false, false, true);
            checkHashEntry(value, v30, true, true, true, true, false, false);
            checkHashEntry(value, v40, true, true, false, false, false, false);
            checkHashEntry(value, v50, true, false, true, true, false, true);
            checkHashEntry(value, v60, true, false, false, false, false, true);
            checkHashEntry(value, v70, true, false, true, true, false, false);
            checkHashEntry(value, v80, false, false, false, false, false, false);

            assertEquals(100, value.getHashValue(v10).asInt());
            assertEquals(400, value.getHashValue(v40).asInt());
            assertEquals(400, value.getHashValueOrDefault(v40, v10).asInt());
            assertEquals(null, value.getHashValue(v50));
            assertEquals(10, value.getHashValueOrDefault(v50, v10).asInt());

            DebugValue v42 = s.createPrimitiveValue(42, null);
            assertEquals(null, value.getHashValue(v42));
            assertEquals(v42, value.getHashValueOrDefault(value, v42));

            DebugValue iterator = value.getHashKeysIterator();
            DebugValueIteratorTest.checkIntIterator(iterator, Arrays.asList(10, 20, 30, 40, 50, 60, 70, 80));
            iterator = value.getHashValuesIterator();
            DebugValueIteratorTest.checkIntIterator(iterator, Arrays.asList(100, 200, 300, 400), true);
            // Entries iterator:
            iterator = value.getHashEntriesIterator();
            checkIntEntriesIterator(iterator, map.entrySet());
        });
    }

    private static void checkHashEntry(DebugValue hash, DebugValue key, boolean existing, boolean readable, boolean writable, boolean modifiable, boolean insertable, boolean removable) {
        assertEquals("existing", existing, hash.isHashEntryExisting(key));
        assertEquals("readable", readable, hash.isHashEntryReadable(key));
        assertEquals("writable", writable, hash.isHashEntryWritable(key));
        assertEquals("modifiable", modifiable, hash.isHashEntryModifiable(key));
        assertEquals("insertable", insertable, hash.isHashEntryInsertable(key));
        assertEquals("removable", removable, hash.isHashEntryRemovable(key));
    }

    static void checkIntEntriesIterator(DebugValue iterator, Collection<Map.Entry<HashElement, Object>> entries) {
        assertTrue(iterator.isIterator());
        assertFalse(iterator.hasIterator()); // it's not iterable
        try {
            iterator.getIterator();
            fail();
        } catch (DebugException ex) {
            // O.K.
        }
        for (Map.Entry<HashElement, Object> entry : entries) {
            assertTrue(iterator.hasIteratorNextElement());
            DebugValue entryArray = iterator.getIteratorNextElement();
            assertTrue(entryArray.isArray());
            List<DebugValue> array = entryArray.getArray();
            assertEquals(2, array.size());
            DebugValue key = array.get(0);
            DebugValue value = array.get(1);

            assertTrue(key.isReadable());
            assertEquals(entry.getKey().isRemovable(), key.isWritable());
            assertEquals(entry.getKey().getKey(), key.asInt());

            assertEquals(entry.getKey().isReadable(), value.isReadable());
            assertEquals(entry.getKey().isWritable(), value.isWritable());
            if (entry.getKey().isReadable()) {
                assertEquals(entry.getValue(), value.asInt());
            } else {
                try {
                    value.asInt();
                    fail();
                } catch (UnsupportedOperationException ex) {
                    // O.K.
                }
            }
        }
        assertFalse(iterator.hasIteratorNextElement());
        try {
            iterator.getIteratorNextElement();
            fail();
        } catch (NoSuchElementException ex) {
            // O.K.
        }
    }

    private static void assertFails(Runnable code) {
        try {
            code.run();
            fail();
        } catch (DebugException ex) {
            // O.K.
        }
    }
}
