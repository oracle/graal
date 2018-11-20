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

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class DSLCallTest extends Node {

    @GenerateLibrary
    public abstract static class DSLCallLibrary extends Library {

        public boolean guard(Object receiver) {
            return false;
        }

        public abstract int someCall(Object receiver);

    }

    @ExportLibrary(DSLCallLibrary.class)
    static class SlowPathMyObject {

        boolean isType;
        int value;

        @ExportMessage
        protected boolean guard() {
            return isType;
        }

        @ExportMessage
        protected int someCall() {
            return value;
        }

    }

// public abstract static class DSLNode extends Node {
//
// abstract int execute(Object arg0, Object arg1);
//
// @Specialization(guards = {"lib1.guard(object1) || lib2.guard(object2)"})
// int doActiveMethods1(
// Object object1,
// Object object2,
// @CachedLibrary("object1") DSLCallLibrary lib1,
// @CachedLibrary("object2") DSLCallLibrary lib2) {
// return lib1.someCall(object1) + lib2.someCall(object2);
// }
//
// @Specialization
// int doFallback(Object object1, Object object2) {
// return -1;
// }
//
// }
//
// @Test
// public void testDSLCall() {
// DSLNode node = DSLNodeGen.create();
//
// SlowPathMyObject object = new SlowPathMyObject();
// object.isType = true;
// object.value = 42;
//
// SlowPathMyObject notobject = new SlowPathMyObject();
// notobject.isType = false;
// notobject.value = 1;
//
// Assert.assertEquals(84, node.execute(object, object));
// Assert.assertEquals(43, node.execute(object, notobject));
// Assert.assertEquals(43, node.execute(notobject, object));
// Assert.assertEquals(-1, node.execute("", ""));
//
// }

}
