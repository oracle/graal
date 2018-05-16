/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.truffle.pelang.PELangBuilder;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangNCFTest extends PELangTest {

    protected Object constant10() {
        return 10L;
    }

    @Test
    public void testAdd() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.ret(b.add(5L, 5L))
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testGlobalReadWrite() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.block(
                b.writeGlobal(10L, "var1"),
                b.ret(b.readGlobal("var1"))
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        // assertPartialEvalEquals("constant10", rootNode);
    }

}
