/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GR52773Test extends AbstractPolyglotTest {

    @Before
    public void setUp() {
        setupEnv(Context.newBuilder().allowHostAccess(HostAccess.newBuilder().allowArrayAccess(true).build()).build());
    }

    @Test
    public void testArrayLookUpDoubleUnderscoreMember() {
        Value array = context.asValue(new Object[]{42});
        String methodName = "__defineSetter__";
        Value underscoreMember = array.getMember(methodName);
        Assert.assertFalse("hasMember(\"" + methodName + "\")", array.hasMember(methodName));
        Assert.assertNull("getMember(\"" + methodName + "\")", underscoreMember);
    }

    @Test
    public void testArrayLookUpCloneMethodByName() {
        String methodName = "clone";
        testArrayCloneMethodCommon(methodName);
    }

    @Test
    public void testArrayLookUpCloneMethodByJNIName() {
        String methodName = "clone__Ljava_lang_Object_2";
        testArrayCloneMethodCommon(methodName);
    }

    @Test
    public void testArrayLookUpCloneMethodBySignature() {
        String methodName = "clone()";
        testArrayCloneMethodCommon(methodName);
    }

    private void testArrayCloneMethodCommon(String methodName) {
        Value array = context.asValue(new Object[]{42});
        Assert.assertEquals(array, array);
        Value cloneMethod = array.getMember(methodName);
        Assert.assertTrue("hasMember(\"" + methodName + "\")", array.hasMember(methodName));
        Assert.assertNotNull("getMember(\"" + methodName + "\")", cloneMethod);

        Value clonedUsingExecute = cloneMethod.execute();
        Assert.assertNotEquals(array, clonedUsingExecute);
        Assert.assertArrayEquals(array.as(Object[].class), clonedUsingExecute.as(Object[].class));

        Value clonedUsingInvoke = array.invokeMember(methodName);
        Assert.assertNotEquals(array, clonedUsingInvoke);
        Assert.assertArrayEquals(array.as(Object[].class), clonedUsingInvoke.as(Object[].class));
    }
}
