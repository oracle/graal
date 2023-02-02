/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractLibraryTest;

public class PropertyGetterTest extends AbstractLibraryTest {

    @Test
    public void testPropertyGetter() throws Exception {
        Shape rootShape = Shape.newBuilder().build();
        DynamicObject o1 = new TestDynamicObjectMinimal(rootShape);
        DynamicObject o2 = new TestDynamicObjectDefault(rootShape);
        DynamicObject o3 = new TestDynamicObjectMinimal(rootShape);

        String key = "key";
        String val = "value";
        String intKey = "intKey";
        int intVal = 42;
        String longKey = "longKey";
        long longVal = 1645568542000L;
        double doubleVal = 3.14159265359;
        String doubleKey = "doubleKey";

        DynamicObjectLibrary uncached = DynamicObjectLibrary.getUncached();
        uncached.putWithFlags(o1, key, val, 13);
        uncached.putWithFlags(o2, key, val, 13);

        assertSame("expected same shape", o1.getShape(), o2.getShape());

        PropertyGetter getter = o1.getShape().makePropertyGetter(key);
        assertTrue(getter.accepts(o1));
        assertEquals(val, getter.get(o1));
        assertTrue(getter.accepts(o2));
        assertEquals(val, getter.get(o2));
        assertFalse(getter.accepts(o3));

        assertFails(() -> getter.get(o3), IllegalArgumentException.class);
        assertFails(() -> getter.getInt(o3), IllegalArgumentException.class);
        assertFails(() -> getter.getInt(o1), UnexpectedResultException.class, e -> assertEquals(val, e.getResult()));

        uncached.put(o1, intKey, intVal);
        uncached.put(o1, longKey, longVal);
        uncached.put(o1, doubleKey, doubleVal);

        PropertyGetter intGetter = o1.getShape().makePropertyGetter(intKey);
        PropertyGetter longGetter = o1.getShape().makePropertyGetter(longKey);
        PropertyGetter doubleGetter = o1.getShape().makePropertyGetter(doubleKey);

        assertEquals(intVal, intGetter.getInt(o1));
        assertEquals(intVal, intGetter.get(o1));
        assertFails(() -> intGetter.getLong(o1), UnexpectedResultException.class, e -> assertEquals(intVal, e.getResult()));
        assertEquals(longVal, longGetter.getLong(o1));
        assertEquals(doubleVal, doubleGetter.getDouble(o1), 1e-9);

        assertEquals(key, getter.getKey());
        assertEquals(13, getter.getFlags());

        assertNull(o1.getShape().makePropertyGetter("nonexistent"));
    }

}
