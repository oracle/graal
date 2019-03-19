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
package com.oracle.truffle.api.library.test.examples;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;

/**
 * Example for reflective exports without dependency to the library code. Reflective exports are
 * currently not fully supported yet. There is no way to dispatch to the default implementation atm.
 */
@SuppressWarnings("unused")
public class ReflectiveExportExample {

    @GenerateLibrary
    @SuppressWarnings("unused")
    abstract static class ReflectiveExportTestLibrary extends Library {
        public String message0(Object receiver) {
            return "message0";
        }

        public String message1(Object receiver) {
            return "message1";
        }
    }

    @ExportLibrary(ReflectionLibrary.class)
    static final class ReflectiveExport {

        private static final Message MESSAGE = Message.resolve(ReflectiveExportTestLibrary.class, "message0", false);

        @SuppressWarnings("static-method")
        @ExportMessage
        Object send(Message message, Object[] args) throws Exception {
            if (message == MESSAGE) {
                return "reflectiveExport";
            } else {
                // TODO how to invoke the super implementation?
                throw new AbstractMethodError();
            }
        }
    }

    @Test
    public void runExample() throws Exception {
        ReflectiveExportTestLibrary library = LibraryFactory.resolve(ReflectiveExportTestLibrary.class).getUncached();

        Object value = new ReflectiveExport();

        assertEquals("reflectiveExport", library.message0(value));

        try {
            assertEquals("message1", library.message1(value));
        } catch (AbstractMethodError e) {
            // TODO currently throws abstract method error but should return default value
            // "message1".
        }
    }

}
