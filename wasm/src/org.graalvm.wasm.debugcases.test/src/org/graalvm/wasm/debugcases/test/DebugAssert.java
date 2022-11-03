/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugcases.test;

import org.junit.Assert;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;

import java.util.List;

public final class DebugAssert {
    private DebugAssert() {

    }

    static void assertStackFramesEquals(Iterable<DebugStackFrame> stackFrames, String[] expectedStackFrames) {
        int i = 0;
        int size = expectedStackFrames.length;
        for (DebugStackFrame stackFrame : stackFrames) {
            int location = size - i - 1;
            if (location < 0) {
                break;
            }
            Assert.assertEquals(expectedStackFrames[location], stackFrame.getName());
            i++;
        }
    }

    public static void assertValueEquals(DebugValue scope, String name, String expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.isString());
        Assert.assertEquals(expectedValue, val.asString());
    }

    public static void assertValueEquals(DebugValue scope, String name, boolean expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.isBoolean());
        Assert.assertEquals(expectedValue, val.asBoolean());
    }

    public static void assertValueEquals(DebugValue scope, String name, short expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInShort());
        Assert.assertEquals(expectedValue, val.asShort());
    }

    public static void assertValueEquals(DebugValue scope, String name, int expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInInt());
        Assert.assertEquals(expectedValue, val.asInt());
    }

    public static void assertValueEquals(DebugValue scope, String name, long expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInLong());
        Assert.assertEquals(expectedValue, val.asLong());
    }

    public static void assertValueEquals(DebugValue scope, String name, float expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInFloat());
        Assert.assertEquals(expectedValue, val.asFloat(), 0.0001);
    }

    public static void assertValueEquals(DebugValue scope, String name, double expectedValue) {
        DebugValue val = scope.getProperty(name);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInDouble());
        Assert.assertEquals(expectedValue, val.asDouble(), 0.0001);
    }

    public static void assertPointersEquals(DebugValue scope, String... values) {
        DebugValue val = scope;
        for (int i = 0; i < values.length; i++) {
            if (i % 2 == 0) {
                val = val.getProperty(values[i]);
                Assert.assertNotNull(val);
            } else {
                if (values[i] == null) {
                    continue;
                }
                Assert.assertEquals(values[i], val.toDisplayString());
            }
        }
    }

    public static void assertArrayElementEquals(List<DebugValue> array, int index, String expectedValue) {
        DebugValue val = array.get(index);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.isString());
        Assert.assertEquals(expectedValue, val.asString());
    }

    public static void assertArrayElementEquals(List<DebugValue> array, int index, short expectedValue) {
        DebugValue val = array.get(index);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInShort());
        Assert.assertEquals(expectedValue, val.asShort());
    }

    public static void assertArrayElementEquals(List<DebugValue> array, int index, int expectedValue) {
        DebugValue val = array.get(index);
        Assert.assertNotNull(val);
        Assert.assertTrue(val.fitsInInt());
        Assert.assertEquals(expectedValue, val.asInt());
    }
}
