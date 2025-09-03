/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.ext.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.ext.test.ObjectModelRegressionTest.TestDynamicObject;

public class GR42603 {

    private static final DynamicObjectLibrary OBJLIB = DynamicObjectLibrary.getUncached();
    private static final int FROZEN_FLAG = 1;

    @Test
    public void testReplacePropertyRace() throws Throwable {
        for (int i = 0; i < 100; i++) {
            testConcurrentReplaceProperty();
        }
    }

    private static void testConcurrentReplaceProperty() throws Throwable {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        Shape rootShape = Shape.newBuilder().build();

        try (Engine engine = Engine.create()) {
            for (int i = 0; i < 2; i++) {
                int iFixed = i;
                futures.add(executorService.submit(() -> {
                    try (Context context = Context.newBuilder().engine(engine).build()) {
                        TestDynamicObject object = newEmptyObject(rootShape);
                        OBJLIB.put(object, "propertyBefore", newEmptyObject(rootShape));
                        boolean assignObject = iFixed == 0;
                        Object hostNullValue = context.asValue(null);
                        OBJLIB.put(object, "offendingProperty", assignObject ? object : hostNullValue);
                        freezeObject(object);

                        Shape shape = object.getShape();
                        if (!assignObject) {
                            String propertyString = shape.getProperty("offendingProperty").toString();
                            if (!propertyString.contains(hostNullValue.getClass().getTypeName()) && !propertyString.contains("java.lang.Object")) {
                                throw new AssertionError("WRONG TYPE OF OFFENDING PROPERTY " + propertyString + "\nShape:" + shape);
                            }
                        }
                    }
                }));
            }
            try {
                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private static TestDynamicObject newEmptyObject(Shape rootShape) {
        return new TestDynamicObject(rootShape);
    }

    private static void freezeObject(TestDynamicObject object) {
        for (Object key : OBJLIB.getKeyArray(object)) {
            OBJLIB.setPropertyFlags(object, key, FROZEN_FLAG);
        }
    }
}
