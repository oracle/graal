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
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangLoopTest extends PELangTest {

    protected Object constant10() {
        return 10;
    }

    @Before
    public void setup() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.block(
                b.write(0, "counter"),
                b.loop(
                    b.lessThan(b.read("counter"), b.literal(10L)),
                    b.increment(1, "counter")),
                b.ret(b.read("counter"))
            )
        );
        // @formatter:on

        try {
            // do a first compilation to load compiler classes and ignore exceptions
            compileHelper(rootNode.toString(), rootNode, new Object[0]);
        } catch (Exception e) {
            // swallow exception
        }
    }

    @Test
    public void loopTest() {
        PELangBuilder b = new PELangBuilder();

        // @formatter:off
        RootNode rootNode = b.root(
            b.block(
                b.write(0, "counter"),
                b.loop(
                    b.lessThan(
                        b.read("counter"),
                        b.literal(10L)),
                    b.increment(1, "counter")),
                b.ret(
                    b.read("counter")
                )
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

}
