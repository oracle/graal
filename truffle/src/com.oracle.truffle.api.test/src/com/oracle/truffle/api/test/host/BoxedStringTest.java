/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings("static-method")
@ExportLibrary(InteropLibrary.class)
public class BoxedStringTest extends ProxyLanguageEnvTest implements TruffleObject {
    public interface ExactMatchInterop {
        String stringValue();

        char charValue();
    }

    private String value;
    private ExactMatchInterop interop;

    static final List<String> KEYS = Arrays.asList(new String[]{"charValue", "stringValue"});

    @Before
    public void initObjects() {
        interop = asJavaObject(ExactMatchInterop.class, this);
    }

    @Test
    public void convertToString() {
        value = "Hello";
        assertEquals("Hello", interop.stringValue());
    }

    @Test
    public void convertToChar() {
        value = "W";
        assertEquals('W', interop.charValue());
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof BoxedStringTest;
    }

    @ExportMessage
    boolean isString() {
        return true;
    }

    @ExportMessage
    String asString() {
        return value;
    }

    @ExportMessage
    final boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object readMember(String member) {
        assert KEYS.contains(member);
        return this;
    }

    @ExportMessage
    final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return asTruffleObject(KEYS);
    }

    @ExportMessage
    final boolean isMemberReadable(String member) {
        return KEYS.contains(member);
    }

}
