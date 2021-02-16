/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

@SuppressWarnings("unused")
public class ReflectionTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @GenerateLibrary
    public abstract static class ReflectionTestLibrary extends Library {

        public abstract void voidReturn(Object receiver);

        @SuppressWarnings("unused")
        public int primitive(Object receiver, int primitive) {
            return 42;
        }

        public abstract Object objectArray(Object receiver, Object[] array);

        public abstract Object varArgs(Object receiver, Object... array);

        public abstract Object varArgsAndObject(Object receiver, Object value, Object... array);

    }

    @ExportLibrary(ReflectionTestLibrary.class)
    static class ReflectiveObject {

        static int voidReturnCount = 0;
        static int voidReturnUncachedCount = 0;

        @ExportMessage
        protected void voidReturn(@Cached(value = "0", uncached = "1") int cached) {
            if (cached == 1) {
                voidReturnUncachedCount++;
            } else {
                voidReturnCount++;
            }
        }

        @ExportMessage
        protected int primitive(int primitive) {
            return primitive;
        }

        @ExportMessage
        protected Object objectArray(Object[] array) {
            return array;
        }

        @ExportMessage
        protected Object varArgs(Object... array) {
            return array;
        }

        @ExportMessage
        protected Object varArgsAndObject(Object value, Object... array) {
            return array;
        }
    }

    @Test
    public void testReflectionDefault() throws Exception {
        Object receiver = new Object();
        ReflectionLibrary reflection = createLibrary(ReflectionLibrary.class, receiver);

        Message primitive = Message.resolve(ReflectionTestLibrary.class, "primitive");
        Message voidReturn = Message.resolve(ReflectionTestLibrary.class, "voidReturn");

        Assert.assertEquals(42, reflection.send(receiver, primitive, 12));
        try {
            reflection.send(receiver, voidReturn);
            Assert.fail();
        } catch (AbstractMethodError e) {
        }
    }

    @Test
    public void testReflectionOnObject() throws Exception {
        ReflectiveObject object = new ReflectiveObject();
        ReflectionLibrary reflection = createLibrary(ReflectionLibrary.class, object);

        Message primitive = Message.resolve(ReflectionTestLibrary.class, "primitive");

        // TODO more tests necessary

        Assert.assertEquals(11, (int) reflection.send(object, primitive, 11));

        try {
            reflection.send(object, primitive, new Object());
            Assert.fail();
        } catch (ClassCastException e) {
        }
        try {
            reflection.send(object, primitive, 11, new Object());
            Assert.fail();
        } catch (IllegalArgumentException e) {
        }
    }

}
