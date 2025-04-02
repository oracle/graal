/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

@RunWith(Parameterized.class)
public class RemoveKeyTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.values());
    }

    final Shape rootShape = Shape.newBuilder().allowImplicitCastIntToDouble(true).layout(TestDynamicObjectDefault.class, MethodHandles.lookup()).build();

    @Test
    public void testRemoveAfterReplace() {
        DynamicObject obj = new TestDynamicObjectDefault(rootShape);

        DynamicObjectLibrary in = createLibrary(DynamicObjectLibrary.class, obj);
        in.put(obj, "date", new Object());
        in.put(obj, "time", new Object());
        in.put(obj, "zone", new Object());
        in.put(obj, "answer", 42);
        in.put(obj, "truth", true);
        in.put(obj, "timeout", 1000);
        in.put(obj, "age", 30);
        in.put(obj, "someObj", new Object());
        in.put(obj, "anotherObj", new Object());
        in.put(obj, "weekdays", 7);
        in.put(obj, "workdays", 5);
        in.put(obj, "wait", new Object());
        in.put(obj, "for", new Object());
        in.put(obj, "it", new Object());
        in.put(obj, "parent", new Object());

        // change the type of the location from int to double
        in.put(obj, "timeout", 1000.5);

        in.put(obj, "let", new Object());
        in.put(obj, "us", new Object());
        in.put(obj, "start", 72);
        in.put(obj, "the", new Object());
        in.put(obj, "game", 13.37);
        in.put(obj, "soon", new Object());
        in.put(obj, "now", new Object());

        Map<Object, Object> archive = DOTestAsserts.archive(obj);

        DynamicObjectLibrary rm = createLibrary(DynamicObjectLibrary.class, obj);
        rm.removeKey(obj, "time");

        DOTestAsserts.verifyValues(obj, archive);
    }

    @Test
    public void testRemoveAfterReplaceGR30786() {
        DynamicObject obj = new TestDynamicObjectDefault(rootShape);

        DynamicObjectLibrary in = createLibrary(DynamicObjectLibrary.class, obj);
        in.put(obj, "head", new Object());
        in.put(obj, "fun", new Object());
        in.put(obj, "body", new Object());
        in.put(obj, "async", 42);
        in.put(obj, "sync", true);
        in.put(obj, "timeout", 1000);
        in.put(obj, "slow", 30);
        in.put(obj, "retries", 5);
        in.put(obj, "mock", new Object());
        in.put(obj, "id", new Object());
        in.put(obj, "timedOut", new Object());
        in.put(obj, "retry", 1);
        in.put(obj, "pending", new Object());
        in.put(obj, "type", new Object());
        in.put(obj, "parent", new Object());

        // change the type of the location from int to double
        in.put(obj, "timeout", 1000.5);
        in.put(obj, "slow", 30.5);

        in.put(obj, "ctx", new Object());
        in.put(obj, "file", new Object());
        in.put(obj, "path", new Object());
        in.put(obj, "random", new Object());
        in.put(obj, "events", new Object());
        in.put(obj, "eventsCount", 17);
        in.put(obj, "callback", new Object());
        in.put(obj, "timer", new Object());
        in.put(obj, "duration", 9000.9);
        in.put(obj, "error", new Object());

        Map<Object, Object> archive = DOTestAsserts.archive(obj);

        DynamicObjectLibrary rm = createLibrary(DynamicObjectLibrary.class, obj);
        rm.removeKey(obj, "fun");

        DOTestAsserts.verifyValues(obj, archive);
    }

    /**
     * Performs removeKey operations where moves have to be sorted.
     */
    @Test
    public void testReversePairwise() {
        Object undefined = new Object();
        DynamicObject obj = new TestDynamicObjectDefault(rootShape);

        DynamicObjectLibrary lib = createLibrary(DynamicObjectLibrary.class, obj);

        lib.put(obj, "length", 10.0);
        lib.put(obj, "0", true);
        lib.put(obj, "2", Double.POSITIVE_INFINITY);
        lib.put(obj, "4", undefined);
        lib.put(obj, "5", undefined);
        lib.put(obj, "8", "NaN");
        lib.put(obj, "9", "-1");

        // reverse with length 10
        lib.put(obj, "0", "-1");
        lib.put(obj, "9", true);
        lib.put(obj, "1", "NaN");
        lib.removeKey(obj, "8");
        lib.removeKey(obj, "2");
        lib.put(obj, "7", Double.POSITIVE_INFINITY);
        lib.removeKey(obj, "3"); // no-op
        lib.removeKey(obj, "6"); // no-op
        lib.put(obj, "4", undefined);
        lib.put(obj, "5", undefined);

        DOTestAsserts.verifyValues(obj, Map.of(
                        "length", 10.0,
                        "0", "-1",
                        "1", "NaN",
                        "4", undefined,
                        "5", undefined,
                        "7", Double.POSITIVE_INFINITY,
                        "9", true));

        // reverse again but with length 9
        lib.put(obj, "length", 9.0);
        lib.removeKey(obj, "0");
        lib.put(obj, "8", "-1");
        lib.put(obj, "1", Double.POSITIVE_INFINITY);
        lib.put(obj, "7", "NaN");
        lib.removeKey(obj, "2"); // no-op
        lib.removeKey(obj, "6"); // no-op
        lib.put(obj, "3", undefined);
        lib.removeKey(obj, "5");

        DOTestAsserts.verifyValues(obj, Map.of(
                        "length", 9.0,
                        "1", Double.POSITIVE_INFINITY,
                        "3", undefined,
                        "4", undefined,
                        "7", "NaN",
                        "8", "-1",
                        "9", true));
    }

    @Test
    public void testReverseSequential() {
        Object undefined = new Object();
        DynamicObject obj = new TestDynamicObjectDefault(rootShape);

        DynamicObjectLibrary lib = createLibrary(DynamicObjectLibrary.class, obj);

        lib.put(obj, "length", 10.0);
        lib.put(obj, "0", true);
        lib.put(obj, "2", Double.POSITIVE_INFINITY);
        lib.put(obj, "4", undefined);
        lib.put(obj, "5", undefined);
        lib.put(obj, "8", "NaN");
        lib.put(obj, "9", "-1");

        // reverse with length 10
        lib.put(obj, "0", "-1");
        lib.put(obj, "1", "NaN");
        lib.removeKey(obj, "2");
        lib.removeKey(obj, "3");
        lib.put(obj, "4", undefined);
        lib.put(obj, "5", undefined);
        lib.removeKey(obj, "6");
        lib.put(obj, "7", Double.POSITIVE_INFINITY);
        lib.removeKey(obj, "8");
        lib.put(obj, "9", true);

        DOTestAsserts.verifyValues(obj, Map.of(
                        "length", 10.0,
                        "0", "-1",
                        "1", "NaN",
                        "4", undefined,
                        "5", undefined,
                        "7", Double.POSITIVE_INFINITY,
                        "9", true));

        // reverse again but with length 9
        lib.put(obj, "length", 9.0);
        lib.removeKey(obj, "0");
        lib.put(obj, "1", Double.POSITIVE_INFINITY);
        lib.removeKey(obj, "2"); // no-op
        lib.put(obj, "3", undefined);
        lib.removeKey(obj, "5");
        lib.removeKey(obj, "6"); // no-op
        lib.put(obj, "7", "NaN");
        lib.put(obj, "8", "-1");

        DOTestAsserts.verifyValues(obj, Map.of(
                        "length", 9.0,
                        "1", Double.POSITIVE_INFINITY,
                        "3", undefined,
                        "4", undefined,
                        "7", "NaN",
                        "8", "-1",
                        "9", true));
    }

    /**
     * Performs a removeKey operation that uses the fallback strategy.
     */
    @Test
    public void testRemoveUsingFallback() {
        DynamicObject obj1 = new TestDynamicObjectDefault(rootShape);
        DynamicObjectLibrary lib = createLibrary(DynamicObjectLibrary.class, obj1);
        lib.put(obj1, "length", 10.0);
        lib.put(obj1, "0", true);
        lib.put(obj1, "1", 11);
        lib.put(obj1, "2", Math.E);
        lib.put(obj1, "3", Math.PI);
        lib.put(obj1, "4", 42);
        lib.put(obj1, "5", 056);
        lib.put(obj1, "8", "NaN");
        lib.put(obj1, "9", "-1");

        DynamicObject obj2 = new TestDynamicObjectDefault(rootShape);
        lib.put(obj2, "length", 10.0);
        lib.put(obj2, "0", true);
        lib.put(obj2, "1", 11);
        lib.put(obj2, "2", Math.E);
        lib.put(obj2, "3", Math.PI);
        lib.put(obj2, "4", 42);
        lib.put(obj2, "5", 056);
        lib.put(obj2, "8", "NaN");
        lib.put(obj2, "9", "-1");

        lib.put(obj2, "1", "eleven");
        // Perform removeKey on an obsolete shape.
        lib.removeKey(obj1, "2");
    }
}
