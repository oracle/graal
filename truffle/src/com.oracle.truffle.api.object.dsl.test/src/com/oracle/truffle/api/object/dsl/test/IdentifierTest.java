/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl.test;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.dsl.Layout;

import org.junit.Assert;

public class IdentifierTest {
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();

    @Layout
    public interface IdentifierTestLayout {

        String A_IDENTIFIER = "a_identifier";
        Integer B_IDENTIFIER = 14;

        DynamicObject createIdentifierTest(int a, int b);

    }

    private static final IdentifierTestLayout LAYOUT = IdentifierTestLayoutImpl.INSTANCE;

    @Test
    public void testContainsKey() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertTrue(LIBRARY.containsKey(object, IdentifierTestLayout.A_IDENTIFIER));
        Assert.assertTrue(LIBRARY.containsKey(object, IdentifierTestLayout.B_IDENTIFIER));
    }

    @Test
    public void testHasProperty() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertTrue(object.getShape().hasProperty(IdentifierTestLayout.A_IDENTIFIER));
        Assert.assertTrue(object.getShape().hasProperty(IdentifierTestLayout.B_IDENTIFIER));
    }

    @Test
    public void testGet() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertEquals(1, LIBRARY.getOrDefault(object, IdentifierTestLayout.A_IDENTIFIER, null));
        Assert.assertEquals(2, LIBRARY.getOrDefault(object, IdentifierTestLayout.B_IDENTIFIER, null));
    }

    @Test
    public void testSet() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertEquals(1, LIBRARY.getOrDefault(object, IdentifierTestLayout.A_IDENTIFIER, null));
        LIBRARY.putIfPresent(object, IdentifierTestLayout.A_IDENTIFIER, 11);
        Assert.assertEquals(11, LIBRARY.getOrDefault(object, IdentifierTestLayout.A_IDENTIFIER, null));
    }

}
