/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings("unused")
public class DelegateLibraryTest extends AbstractLibraryTest {

    @GenerateLibrary
    public abstract static class DelegateTestLibrary extends Library {

        public String doSomething(Object receiver) {
            return "default";
        }

        public String doOther(Object receiver) {
            return "other";
        }
    }

    @ExportLibrary(DelegateTestLibrary.class)
    static class SomeObject {

        @ExportMessage
        String doSomething() {
            return "SomeObject";
        }

        @ExportMessage
        String doOther() {
            return "SomeObjectOther";
        }
    }

    @ExportLibrary(DelegateTestLibrary.class)
    static class WrapperObject {

        final Object wrapped;

        WrapperObject(Object wrapped) {
            this.wrapped = wrapped;
        }

        @ExportMessage(limit = "1")
        @ExpectError("Specialized cached libraries cannot be shared yet.")
        String doSomething(@ExpectError("Specialized cached libraries cannot be shared yet.") @Shared("delegate") @CachedLibrary("this.wrapped") DelegateTestLibrary delegate) {
            return "wrapped:" + delegate.doSomething(wrapped);
        }

        @ExportMessage(limit = "1")
        String doOther(@ExpectError("Specialized cached libraries cannot be shared yet.") @Shared("delegate") @CachedLibrary("this.wrapped") DelegateTestLibrary delegate) {
            return "wrapped:" + delegate.doOther(wrapped);
        }
    }

    @Test
    @org.junit.Ignore // not supported yet with specialized libraries.
    public void test() {
        DelegateTestLibrary lib = createCachedDispatch(DelegateTestLibrary.class, 5);
        Assert.assertEquals("wrapped:SomeObject", lib.doSomething(new WrapperObject(new SomeObject())));
        Assert.assertEquals("wrapped:other", lib.doOther(new WrapperObject(42)));
    }
}
