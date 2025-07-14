/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GR66539Test {

    @Parameter(0) public String methodName;

    @Parameters(name = "{0}")
    public static List<String> data() {
        return List.of("directMethod", "overloadedMethod");
    }

    /*
     * Originally reported bug.
     */
    @Test
    public void test1Direct() {
        try (Context context = Context.create()) {
            Value v = context.asValue(new GR66539Test());

            assertNull(v.invokeMember(methodName, "a", null).as(Object[].class));
            assertArrayEquals(new Object[]{"b"}, v.invokeMember(methodName, "a", "b").as(Object[].class));
        }
    }

    @Test
    public void test2Direct() {
        try (Context context = Context.create()) {
            Value v = context.asValue(new GR66539Test());
            Object[] testArray = new Object[2];

            assertArrayEquals(new Object[]{null, null}, v.invokeMember(methodName, "a", testArray).as(Object[].class));
            assertArrayEquals(new Object[]{"b"}, v.invokeMember(methodName, "a", "b").as(Object[].class));
        }
    }

    @Test
    public void test3Direct() {
        AtomicBoolean targetMappingEnabled = new AtomicBoolean(true);
        Object testArray = new Object();

        HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)//
                        .targetTypeMapping(Value.class, Object[].class, (v) -> targetMappingEnabled.get() && v.isHostObject() && v.asHostObject() == testArray, (v) -> {
                            if (targetMappingEnabled.get()) {
                                return new Object[1];
                            } else {
                                throw new ClassCastException();
                            }
                        })//
                        .build();

        try (Context context = Context.newBuilder().allowHostAccess(access).build()) {
            Value v = context.asValue(new GR66539Test());

            assertArrayEquals(new Object[]{null}, v.invokeMember(methodName, "a", testArray).as(Object[].class));
            targetMappingEnabled.set(false);

            // testing that this does not throw now when the targetTypeMapping changes
            assertArrayEquals(new Object[]{testArray}, v.invokeMember(methodName, "a", testArray).as(Object[].class));
        }
    }

    @HostAccess.Export
    @SuppressWarnings("unused")
    public Object[] directMethod(String a, final Object... b) {
        return b;
    }

    @HostAccess.Export
    @SuppressWarnings("unused")
    public Object[] overloadedMethod(String a, final Object... b) {
        return b;
    }

    @HostAccess.Export
    @SuppressWarnings("unused")
    public Object[] overloadedMethod(Object a, final Object... b) {
        // not supposed to be called
        throw new AssertionError();
    }

}
