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

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * Test of iterator methods of DebugValue.
 */
public class DebugValueIteratorTest extends AbstractDebugTest {

    @Test
    public void testIterable() throws Throwable {
        List<Integer> list = Arrays.asList(1, 2, 3);
        Object iterable = new TestIteratorObject(true, false, list);
        checkDebugValueOf(iterable, value -> {
            assertTrue(value.hasIterator());
            assertFalse(value.isIterator());
            try {
                value.hasIteratorNextElement();
                fail();
            } catch (DebugException ex) {
                // O.K.
            }
            try {
                value.getIteratorNextElement();
                fail();
            } catch (DebugException ex) {
                // O.K.
            }
            // Iterable's iterator:
            DebugValue iterator = value.getIterator();
            checkIntIterator(iterator, list);
            // The iterator is re-created when obtained again:
            iterator = value.getIterator();
            checkIntIterator(iterator, list);
        });
    }

    @Test
    public void testIterator() throws Throwable {
        List<Integer> list = Arrays.asList(1, 2, 3);
        Object iterable = new TestIteratorObject(false, true, list);
        checkDebugValueOf(iterable, value -> {
            checkIntIterator(value, list);
        });
    }

    @Test
    public void testChangingIterator() throws Throwable {
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));
        Object iterable = new TestIteratorObject(false, true, list);
        checkDebugValueOf(iterable, value -> {
            assertTrue(value.isIterator());
            assertTrue(value.hasIteratorNextElement());
            assertEquals(1, value.getIteratorNextElement().asInt());
            assertTrue(value.hasIteratorNextElement());
            assertEquals(2, value.getIteratorNextElement().asInt());
            assertTrue(value.hasIteratorNextElement());
            list.remove(2);
            try {
                value.getIteratorNextElement();
                fail();
            } catch (NoSuchElementException ex) {
                // O.K.
            }
            assertFalse(value.hasIteratorNextElement());
            list.add(10);
            assertEquals(10, value.getIteratorNextElement().asInt());
            assertFalse(value.hasIteratorNextElement());
        });
    }

    static void checkIntIterator(DebugValue value, List<Integer> list) {
        checkIntIterator(value, list, false);
    }

    static void checkIntIterator(DebugValue value, List<Integer> list, boolean lastFails) {
        assertTrue(value.isIterator());
        assertFalse(value.hasIterator()); // it's not iterable
        try {
            value.getIterator();
            fail();
        } catch (DebugException ex) {
            // O.K.
        }
        for (Integer i : list) {
            assertTrue(value.hasIteratorNextElement());
            assertEquals(i.intValue(), value.getIteratorNextElement().asInt());
        }
        if (!lastFails) {
            assertFalse(value.hasIteratorNextElement());
            try {
                value.getIteratorNextElement();
                fail();
            } catch (NoSuchElementException ex) {
                // O.K.
            }
        } else {
            try {
                value.getIteratorNextElement();
                fail();
            } catch (DebugException ex) {
                // O.K.
            }
        }
    }
}
