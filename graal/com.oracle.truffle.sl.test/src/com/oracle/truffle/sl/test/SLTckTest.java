/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.sl.test;

import com.oracle.truffle.api.test.vm.TruffleTCK;
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
            "function null() {\n" +
            "}\n"
        );
        // @formatter:on
        return vm;
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
}
