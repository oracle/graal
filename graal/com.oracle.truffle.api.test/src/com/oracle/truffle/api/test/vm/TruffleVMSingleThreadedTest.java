/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import java.io.*;
import java.net.*;

import org.junit.*;

import com.oracle.truffle.api.vm.*;

public class TruffleVMSingleThreadedTest {
    TruffleVM tvm;

    @Before
    public void initInDifferentThread() throws InterruptedException {
        final TruffleVM.Builder b = TruffleVM.newVM();
        Thread t = new Thread("Initializer") {
            @Override
            public void run() {
                tvm = b.build();
            }
        };
        t.start();
        t.join();
    }

    @Test(expected = IllegalStateException.class)
    public void evalURI() throws IOException, URISyntaxException {
        tvm.eval(new URI("http://unknown.js"));
    }

    @Test(expected = IllegalStateException.class)
    public void evalString() throws IOException {
        tvm.eval("text/javascript", "1 + 1");
    }

    @Test(expected = IllegalStateException.class)
    public void evalReader() throws IOException {
        try (StringReader sr = new StringReader("1 + 1")) {
            tvm.eval("text/javascript", sr);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void findGlobalSymbol() {
        tvm.findGlobalSymbol("doesNotExists");
    }
}
