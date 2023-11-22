/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.chromeinspector.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.tools.chromeinspector.objects.JSONTruffleArray;
import com.oracle.truffle.tools.chromeinspector.objects.JSONTruffleObject;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import org.junit.Test;

public class InteropTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testJSONTruffleArraySort() throws InteropException {
        Object array = new JSONTruffleArray(new JSONArray("[2, 7, 1, 8]"));
        InteropLibrary interop = InteropLibrary.getUncached(array);
        long length = interop.getArraySize(array);

        for (int iter = 1; iter < length; iter++) {
            for (int idx = 1; idx < length; idx++) {
                Object prev = interop.readArrayElement(array, idx - 1);
                Object curr = interop.readArrayElement(array, idx);
                if (((Comparable<Object>) prev).compareTo(curr) > 0) {
                    // swap
                    interop.writeArrayElement(array, idx - 1, curr);
                    interop.writeArrayElement(array, idx, prev);
                }
            }
        }

        for (int idx = 1; idx < length; idx++) {
            Object prev = interop.readArrayElement(array, idx - 1);
            Object curr = interop.readArrayElement(array, idx);
            assertTrue(((Comparable<Object>) prev).compareTo(curr) <= 0);
        }
    }

    private static void testIdentity(Object theObject, Object sameObject, Object differentObject) {
        InteropLibrary interop = InteropLibrary.getUncached(theObject);
        assertTrue(interop.hasIdentity(theObject));
        assertTrue(interop.isIdentical(theObject, sameObject, interop));
        assertFalse(interop.isIdentical(theObject, differentObject, interop));
    }

    @Test
    public void testJSONTruffleArrayIdentity() {
        String json = "[42, 211]";
        JSONArray jsonArray = new JSONArray(json);
        Object array = new JSONTruffleArray(jsonArray);
        Object sameArray = new JSONTruffleArray(jsonArray);
        Object anotherArray = new JSONTruffleArray(new JSONArray(json));
        testIdentity(array, sameArray, anotherArray);
    }

    @Test
    public void testJSONTruffleObjectIdentity() {
        String json = "{ answer: 42, question: '?' }";
        JSONObject jsonObject = new JSONObject(json);
        JSONTruffleObject object = new JSONTruffleObject(jsonObject);
        Object sameObject = new JSONTruffleObject(jsonObject);
        Object anotherObject = new JSONTruffleObject(new JSONObject(json));
        testIdentity(object, sameObject, anotherObject);
    }

    @Test
    public void testJSONKeysIdentity() throws InteropException {
        String json = "{ answer: 42, question: '?' }";
        JSONObject jsonObject = new JSONObject(json);
        JSONTruffleObject truffleObject = new JSONTruffleObject(jsonObject);
        InteropLibrary interop = InteropLibrary.getUncached();
        Object keys = interop.getMembers(truffleObject);
        Object sameKeys = interop.getMembers(truffleObject);
        Object otherKeys = interop.getMembers(new JSONTruffleObject(jsonObject));
        testIdentity(keys, sameKeys, otherKeys);
    }

}
