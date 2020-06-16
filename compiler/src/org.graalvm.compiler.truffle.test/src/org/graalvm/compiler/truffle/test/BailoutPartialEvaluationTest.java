/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

import jdk.vm.ci.code.BailoutException;
import org.graalvm.polyglot.Context;

public class BailoutPartialEvaluationTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build());
    }

    static Object notConstant;
    static int notConstantInt;

    @Test(expected = BailoutException.class)
    public void partialEvaluationConstantBailout1() {
        FrameDescriptor fd = new FrameDescriptor();
        RootTestNode rootNode = new RootTestNode(fd, "partialEvaluationConstantBailout1", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(notConstant);
                return 0;
            }
        });
        compileHelper("partialEvaluationConstantBailout1", rootNode, new Object[]{});
    }

    @Test(expected = BailoutException.class)
    public void partialEvaluationConstantBailout2() {
        FrameDescriptor fd = new FrameDescriptor();
        RootTestNode rootNode = new RootTestNode(fd, "partialEvaluationConstantBailout2", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(notConstantInt);
                return 0;
            }
        });
        compileHelper("partialEvaluationConstantBailout2", rootNode, new Object[]{});
    }
}
