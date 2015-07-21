/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import com.oracle.truffle.tck.TruffleTCK;
import com.oracle.truffle.api.vm.TruffleVM;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * This is the way to verify your language implementation is compatible.
 *
 */
public class SLTckTest extends TruffleTCK {
    @Test
    public void testVerifyPresence() {
        TruffleVM vm = TruffleVM.newVM().build();
        assertTrue("Our language is present", vm.getLanguages().containsKey("application/x-sl"));
    }

    @Override
    protected TruffleVM prepareVM() throws Exception {
        TruffleVM vm = TruffleVM.newVM().build();
        // @formatter:off
        vm.eval("application/x-sl",
            "function fourtyTwo() {\n" +
            "  return 42;\n" + //
            "}\n" +
            "function plus(a, b) {\n" +
            "  return a + b;\n" +
            "}\n" +
            "function apply(f) {\n" +
            "  return f(18, 32) + 10;\n" +
            "}\n" +
            "function null() {\n" +
            "}\n"
        );
        // @formatter:on
        return vm;
    }

    @Override
    protected String mimeType() {
        return "application/x-sl";
    }

    @Override
    protected String fourtyTwo() {
        return "fourtyTwo";
    }

    @Override
    protected String plusInt() {
        return "plus";
    }

    @Override
    protected String returnsNull() {
        return "null";
    }

    @Override
    protected String applyNumbers() {
        return "apply";
    }

    @Override
    protected String invalidCode() {
        // @formatter:off
        return
            "f unction main() {\n" +
            "  retu rn 42;\n" +
            "}\n";
        // @formatter:on
    }
}
