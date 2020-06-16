/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class ObjectNFITest extends NFITest {

    private static Object nativeAPI;

    private static class TestObject implements TruffleObject {

        int intField;

        TestObject() {
            this(0);
        }

        TestObject(int value) {
            intField = value;
        }

        int readField(String field) {
            Assert.assertEquals("field name", "intField", field);
            return intField;
        }

        void writeField(String field, int value) {
            Assert.assertEquals("field name", "intField", field);
            intField = value;
        }
    }

    interface ReadIntField {

        int read(TestObject obj, String field);
    }

    interface WriteIntField {

        void write(TestObject obj, String field, int value);
    }

    @BeforeClass
    public static void initEnv() {
        TruffleObject createNewObject = new TestCallback(0, (args) -> new TestObject());
        TruffleObject readIntField = new TestCallback(2, (args) -> {
            Assert.assertThat("args[0]", args[0], is(instanceOf(TestObject.class)));
            Assert.assertThat("args[1]", args[1], is(instanceOf(String.class)));
            return ((TestObject) args[0]).readField((String) args[1]);
        });
        TruffleObject writeIntField = new TestCallback(3, (args) -> {
            Assert.assertThat("args[0]", args[0], is(instanceOf(TestObject.class)));
            Assert.assertThat("args[1]", args[1], is(instanceOf(String.class)));
            Assert.assertThat("args[2]", args[2], is(instanceOf(Integer.class)));
            ((TestObject) args[0]).writeField((String) args[1], (Integer) args[2]);
            return null;
        });

        TruffleObject initializeAPI = lookupAndBind("initialize_api", "( env, ():object, (object,string):sint32, (object,string,sint32):void ) : pointer");
        try {
            nativeAPI = UNCACHED_INTEROP.execute(initializeAPI, createNewObject, readIntField, writeIntField);
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    @AfterClass
    public static void deleteAPI() {
        TruffleObject deleteAPI = lookupAndBind("delete_api", "(env, pointer):void");
        try {
            UNCACHED_INTEROP.execute(deleteAPI, nativeAPI);
            nativeAPI = null;
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    public static class CopyAndIncrementNode extends SendExecuteNode {

        public CopyAndIncrementNode() {
            super("copy_and_increment", "(env, pointer, object) : object");
        }
    }

    @Test
    public void testCopyAndIncrement(@Inject(CopyAndIncrementNode.class) CallTarget callTarget) {
        TestObject testObj = new TestObject(42);

        Object ret = callTarget.call(nativeAPI, testObj);
        Assert.assertThat("return value", ret, is(instanceOf(TestObject.class)));

        TestObject testRet = (TestObject) ret;
        Assert.assertNotSame("return value", testObj, testRet);
        Assert.assertEquals("intField", 43, testRet.intField);
    }

    private TestObject testArg;

    public class TestKeepObjectNode extends NFITestRootNode {

        final TruffleObject keepExistingObject = lookupAndBind("keep_existing_object", "(env, object):pointer");
        final TruffleObject freeAndGetObject = lookupAndBind("free_and_get_object", "(env, pointer):object");
        final TruffleObject freeAndGetContent = lookupAndBind("free_and_get_content", "(env, pointer, pointer):sint32");

        @Child InteropLibrary keepExistingObjectInterop = getInterop(keepExistingObject);
        @Child InteropLibrary freeAndGetObjectInterop = getInterop(freeAndGetObject);
        @Child InteropLibrary freeAndGetContentInterop = getInterop(freeAndGetContent);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object obj = frame.getArguments()[0];

            testArg.intField = 42;

            Object nativePtr1 = keepExistingObjectInterop.execute(keepExistingObject, obj);
            Object nativePtr2 = keepExistingObjectInterop.execute(keepExistingObject, obj);
            Object nativePtr3 = keepExistingObjectInterop.execute(keepExistingObject, obj);

            Object ret = freeAndGetContentInterop.execute(freeAndGetContent, nativeAPI, nativePtr1);
            assertEquals(42, (int) (Integer) ret);

            testArg.intField--;

            ret = freeAndGetContentInterop.execute(freeAndGetContent, nativeAPI, nativePtr2);
            assertEquals(41, (int) (Integer) ret);

            return freeAndGetObjectInterop.execute(freeAndGetObject, nativePtr3);
        }
    }

    @Test
    public void testKeepObject(@Inject(TestKeepObjectNode.class) CallTarget callTarget) {
        testArg = new TestObject();

        Object finalRet = callTarget.call(testArg);

        Assert.assertThat("return value", finalRet, is(instanceOf(TestObject.class)));
        Assert.assertSame("return value", testArg, finalRet);
    }

    public static class TestKeepNewObjectNode extends NFITestRootNode {

        final TruffleObject keepNewObject = lookupAndBind("keep_new_object", "(pointer):pointer");
        final TruffleObject freeAndGetObject = lookupAndBind("free_and_get_object", "(env, pointer):object");

        @Child InteropLibrary keepNewObjectInterop = getInterop(keepNewObject);
        @Child InteropLibrary freeAndGetObjectInterop = getInterop(freeAndGetObject);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object nativePtr = keepNewObjectInterop.execute(keepNewObject, nativeAPI);
            return freeAndGetObjectInterop.execute(freeAndGetObject, nativePtr);
        }
    }

    @Test
    public void testKeepNewObject(@Inject(TestKeepNewObjectNode.class) CallTarget callTarget) {
        Object ret = callTarget.call();

        Assert.assertThat("return value", ret, is(instanceOf(TestObject.class)));
        Assert.assertEquals("intField", 8472, ((TestObject) ret).intField);
    }

    public class TestCompareObjectNode extends NFITestRootNode {

        final TruffleObject keepExistingObject = lookupAndBind("keep_existing_object", "(env, object):pointer");
        final TruffleObject compareExistingObject = lookupAndBind("compare_existing_object", "(env, pointer, pointer):sint32");

        @Child InteropLibrary keepExistingObjectInterop = getInterop(keepExistingObject);
        @Child InteropLibrary compareExistingObjectInterop = getInterop(compareExistingObject);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object obj1 = frame.getArguments()[0];
            Object obj2 = frame.getArguments()[1];

            Object nativePtr1 = keepExistingObjectInterop.execute(keepExistingObject, obj1);
            Object nativePtr2 = keepExistingObjectInterop.execute(keepExistingObject, obj2);

            Object ret = compareExistingObjectInterop.execute(compareExistingObject, nativePtr1, nativePtr2);
            return ret;
        }
    }

    @Test
    public void testCompareExistingObject(@Inject(TestCompareObjectNode.class) CallTarget callTarget) {
        TestObject testObj = new TestObject(42);
        TestObject testObj2 = new TestObject(43);
        Object ret = callTarget.call(testObj, testObj);
        Assert.assertEquals(1, (int) (Integer) ret);
        ret = callTarget.call(testObj, testObj2);
        Assert.assertEquals(0, (int) (Integer) ret);
    }

}
