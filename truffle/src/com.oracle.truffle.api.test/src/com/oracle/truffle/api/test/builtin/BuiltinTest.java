/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.builtin;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.test.interop.InteropLibraryBaseTest;

public class BuiltinTest extends InteropLibraryBaseTest {

    private static final String EXISTING = "testArg1";
    private static final String NOT_EXISTING = "testArg1_";

    @Test
    public void testExisting() throws InteropException {
        BuiltinTestObject testObject = new BuiltinTestObject();
        InteropLibrary builtinLib = createLibrary(InteropLibrary.class, testObject);
        Assert.assertTrue("test42", builtinLib.isMemberReadable(testObject, EXISTING));
        Assert.assertTrue("test42", builtinLib.isMemberInvocable(testObject, EXISTING));

        Object function = builtinLib.readMember(testObject, EXISTING);
        InteropLibrary functionLib = createLibrary(InteropLibrary.class, function);

        Assert.assertEquals("test42", builtinLib.invokeMember(testObject, EXISTING, "42"));
        Assert.assertEquals("test42", functionLib.execute(function, "42"));
    }

    @Test
    public void testGetMembers() throws InteropException {
        BuiltinTestObject testObject = new BuiltinTestObject();
        InteropLibrary builtinLib = createLibrary(InteropLibrary.class, testObject);
        Object members = builtinLib.getMembers(testObject);
        InteropLibrary membersLib = createLibrary(InteropLibrary.class, members);
        Assert.assertTrue(membersLib.hasArrayElements(members));
        Assert.assertEquals(4, membersLib.getArraySize(members));
        Assert.assertEquals(EXISTING, membersLib.readArrayElement(members, 1));
    }

    @Test
    public void testInvalidArguments() throws InteropException {
        BuiltinTestObject testObject = new BuiltinTestObject();
        InteropLibrary builtinLib = createLibrary(InteropLibrary.class, testObject);
        try {
            builtinLib.invokeMember(testObject, EXISTING);
            Assert.fail();
        } catch (ArityException e) {
        }
        try {
            builtinLib.invokeMember(testObject, EXISTING, "", "");
            Assert.fail();
        } catch (ArityException e) {
        }
        try {
            builtinLib.invokeMember(testObject, EXISTING, 42);
            Assert.fail();
        } catch (UnsupportedTypeException e) {
        }

        Object function = builtinLib.readMember(testObject, EXISTING);
        InteropLibrary functionLib = createLibrary(InteropLibrary.class, function);
        try {
            Assert.assertEquals("test42", functionLib.execute(function));
            Assert.fail();
        } catch (ArityException e) {
        }
        try {
            Assert.assertEquals("test42", functionLib.execute(function, "", ""));
            Assert.fail();
        } catch (ArityException e) {
        }
        try {
            Assert.assertEquals("test42", functionLib.execute(function, 42));
            Assert.fail();
        } catch (UnsupportedTypeException e) {
        }
    }

    @Test
    public void testNotExisting() throws InteropException {
        BuiltinTestObject testObject = new BuiltinTestObject();
        InteropLibrary builtinLib = createLibrary(InteropLibrary.class, testObject);
        Assert.assertFalse("test42", builtinLib.isMemberReadable(testObject, NOT_EXISTING));
        Assert.assertFalse("test42", builtinLib.isMemberInvocable(testObject, NOT_EXISTING));

        try {
            builtinLib.readMember(testObject, NOT_EXISTING);
            Assert.fail();
        } catch (UnknownIdentifierException e) {
        }

        try {
            builtinLib.invokeMember(testObject, NOT_EXISTING);
            Assert.fail();
        } catch (UnknownIdentifierException e) {
        }
    }

}
