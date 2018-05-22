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

import org.graalvm.compiler.truffle.pelang.PELangBCFGenerator;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.junit.Test;

public class PELangBCFTest extends PELangTest {

    protected Object constant10() {
        return 10L;
    }

    @Test
    public void testSimpleAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleAdd());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleBlock() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBlock());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLocalReadWrite());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleGlobalReadWrite());
        assertCallResult(10L, rootNode);

        compileHelper(rootNode.toString(), rootNode, new Object[0]);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testSimpleBranch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBranch());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLoop() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLoop());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedAdds());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBlocks() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBlocks());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLocalReadWrites());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLoops() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLoops());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBranches() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBranches());
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.branchWithGlobalReadWrite());
        assertCallResult(10L, rootNode);

        compileHelper(rootNode.toString(), rootNode, new Object[0]);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.loopWithGlobalReadWrite());
        assertCallResult(10L, rootNode);

        try {
            // do a first compilation to load compiler classes and ignore exceptions
            compileHelper(rootNode.toString(), rootNode, new Object[0]);
        } catch (Exception e) {
            // swallow exception
        }

        compileHelper(rootNode.toString(), rootNode, new Object[0]);
        // TODO: add partial evaluation asserts
    }

}
