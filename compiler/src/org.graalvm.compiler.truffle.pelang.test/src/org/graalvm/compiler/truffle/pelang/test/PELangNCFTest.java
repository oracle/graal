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

import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.junit.Test;

public class PELangNCFTest extends PELangTest {

    protected Object constant10() {
        return 10L;
    }

    @Test
    public void testSimpleAdd() {
        PELangRootNode rootNode = PELangSample.simpleAdd();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleBlock() {
        PELangRootNode rootNode = PELangSample.simpleBlock();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangRootNode rootNode = PELangSample.simpleLocalReadWrite();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.simpleGlobalReadWrite();
        assertCallResult(10L, rootNode);

        compileHelper(rootNode.toString(), rootNode, new Object[0]);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testSimpleBranch() {
        PELangRootNode rootNode = PELangSample.simpleBranch();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLoop() {
        PELangRootNode rootNode = PELangSample.simpleLoop();
        assertCallResult(10L, rootNode);

        try {
            // do a first compilation to load compiler classes and ignore exceptions
            compileHelper(rootNode.toString(), rootNode, new Object[0]);
        } catch (Exception e) {
            // swallow exception
        }
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedAdd() {
        PELangRootNode rootNode = PELangSample.nestedAdds();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBlocks() {
        PELangRootNode rootNode = PELangSample.nestedBlocks();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangRootNode rootNode = PELangSample.nestedLocalReadWrites();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLoops() {
        PELangRootNode rootNode = PELangSample.nestedLoops();
        assertCallResult(10L, rootNode);

        try {
            // do a first compilation to load compiler classes and ignore exceptions
            compileHelper(rootNode.toString(), rootNode, new Object[0]);
        } catch (Exception e) {
            // swallow exception
        }
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBranches() {
        PELangRootNode rootNode = PELangSample.nestedBranches();
        assertCallResult(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.branchWithGlobalReadWrite();
        assertCallResult(10L, rootNode);

        compileHelper(rootNode.toString(), rootNode, new Object[0]);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.loopWithGlobalReadWrite();
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
