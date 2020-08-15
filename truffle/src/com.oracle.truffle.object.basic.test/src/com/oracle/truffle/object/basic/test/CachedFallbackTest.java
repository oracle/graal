/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractLibraryTest;

public class CachedFallbackTest extends AbstractLibraryTest {

    @Test
    public void testMixedReceiverTypeSameShape() {
        Shape shape = Shape.newBuilder().build();
        DynamicObject o1 = new TestDynamicObjectMinimal(shape);
        DynamicObject o2 = new TestDynamicObjectDefault(shape);
        String key = "key";
        String val = "value";

        CachedPutNode writeNode = adopt(CachedPutNodeGen.create());
        writeNode.execute(o1, key, val);
        writeNode.execute(o2, key, val);

        assertSame("expected same shape", o1.getShape(), o2.getShape());

        CachedGetNode readNode = adopt(CachedGetNodeGen.create());
        assertEquals(val, readNode.execute(o1, key));
        assertEquals(val, readNode.execute(o2, key));
    }

    @Test
    public void testTransition() {
        Shape shape = Shape.newBuilder().build();
        DynamicObject o1 = new TestDynamicObjectDefault(shape);
        DynamicObject o2 = new TestDynamicObjectDefault(shape);
        String key1 = "key1";
        String val1 = "value1";
        String key2 = "key2";
        String val2 = "value2";

        DynamicObjectLibrary library = adopt(DynamicObjectLibrary.getFactory().create(o1));
        assertTrue(library.accepts(o1));
        assertTrue(library.accepts(o2));
        library.put(o1, key1, val1);
        library.put(o2, key1, val1);
        library.put(o1, key2, val2);
        library.put(o2, key2, val2);

        assertSame("expected same shape", o1.getShape(), o2.getShape());

        CachedGetNode readNode = adopt(CachedGetNodeGen.create());
        assertEquals(val1, readNode.execute(o1, key1));
        assertEquals(val1, readNode.execute(o2, key1));
        assertEquals(val2, readNode.execute(o1, key2));
        assertEquals(val2, readNode.execute(o2, key2));
    }

    @Ignore
    @Test
    public void testMixedReceiverTypeSameShapeWithFallback() {
        Shape shape = Shape.newBuilder().build();
        DynamicObject o1 = new TestDynamicObjectMinimal(shape);
        DynamicObject o2 = new TestDynamicObjectDefault(shape);
        String key1 = "key1";
        String val1 = "value1";
        String key2 = "key2";
        String val2 = "value2";

        DynamicObjectLibrary library1 = adopt(DynamicObjectLibrary.getFactory().create(o1));
        library1.put(o1, key1, val1);
        library1.put(o2, key1, val1);
        DynamicObjectLibrary library2 = adopt(DynamicObjectLibrary.getFactory().create(o1));
        library2.put(o1, key2, val2);
        library2.put(o2, key2, val2);

        assertSame("expected same shape", o1.getShape(), o2.getShape());

        CachedGetNode readNode = adopt(CachedGetNodeGen.create());
        assertEquals(val1, readNode.execute(o1, key1));
        assertEquals(val1, readNode.execute(o2, key1));
        assertEquals(val2, readNode.execute(o1, key2));
        assertEquals(val2, readNode.execute(o2, key2));
    }

}
