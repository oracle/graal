/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.processor;

import static org.junit.Assert.*;

import java.util.*;

import javax.annotation.processing.*;
import javax.tools.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.test.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.verify.*;

/**
 * Verify errors emitted by the processor.
 */
public class TruffleProcessorTest {
    //
    // AnnotationProcessor test using the NetBeans style
    //

    @Test
    public void childCannotBeFinal() throws Exception {
        // @formatter:off
        String code = "package x.y.z;\n" +
            "import com.oracle.truffle.api.nodes.Node;\n" +
            "abstract class MyNode extends Node {\n" +
            "  @Child final MyNode first;\n" +
            "  MyNode(MyNode n) {\n" +
            "    this.first = n;\n" +
            "  };\n" +
            "}\n";
        // @formatter:on

        Compile c = Compile.create(VerifyTruffleProcessor.class, code);
        c.assertErrors();
        boolean ok = false;
        StringBuilder msgs = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> e : c.getErrors()) {
            String msg = e.getMessage(Locale.ENGLISH);
            if (msg.contains("cannot be final")) {
                ok = true;
            }
            msgs.append("\n").append(msg);
        }
        if (!ok) {
            fail("Should contain warning about final:" + msgs);
        }
    }

    @Test
    public void workAroundCannonicalDependency() throws Exception {
        Class<?> myProc = VerifyTruffleProcessor.class;
        assertNotNull(myProc);
        StringBuilder sb = new StringBuilder();
        sb.append("Cannot find ").append(myProc);
        for (Processor load : ServiceLoader.load(Processor.class)) {
            sb.append("Found ").append(load);
            if (myProc.isInstance(load)) {
                return;
            }
        }
        fail(sb.toString());
    }

    //
    // and now the Truffle traditional way
    //

    abstract class MyNode extends Node {
        @ExpectError("@Child field cannot be final") @Child final MyNode first;

        MyNode(MyNode n) {
            this.first = n;
        }
    }
}
