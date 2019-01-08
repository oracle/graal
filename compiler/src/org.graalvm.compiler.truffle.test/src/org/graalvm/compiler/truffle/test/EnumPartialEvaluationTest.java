/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

public class EnumPartialEvaluationTest extends PartialEvaluationTest {
    public static Object constant42() {
        return 42;
    }

    public static Object constant0() {
        return 0;
    }

    enum TestEnum {
        Test0,
        Test1,
    }

    static class SwitchTestNode extends AbstractTestNode {
        private final TestEnum testEnum;

        SwitchTestNode(TestEnum testEnum) {
            this.testEnum = testEnum;
        }

        @Override
        public int execute(VirtualFrame frame) {
            switch (testEnum) {
                case Test0:
                    return 42;
                case Test1:
                    return 43;
                default:
                    throw new AssertionError();
            }
        }
    }

    @Test
    public void enumSwitchConstantFolding() {
        AbstractTestNode result = new SwitchTestNode(TestEnum.Test0);
        assertPartialEvalEquals("constant42", new RootTestNode(new FrameDescriptor(), "enumSwitchConstantFolding", result));
    }

    @Test
    public void enumOrdinalConstant() {
        AbstractTestNode result = new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                return TestEnum.Test0.ordinal();
            }
        };
        assertPartialEvalEquals("constant0", new RootTestNode(new FrameDescriptor(), "enumOrdinalConstant", result));
    }

    @Ignore("Currently only works if compiled with ecj")
    @Test
    public void enumValuesConstant() {
        AbstractTestNode result = new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                return TestEnum.values()[0].ordinal();
            }
        };
        assertPartialEvalEquals("constant0", new RootTestNode(new FrameDescriptor(), "enumValuesConstant", result));
    }
}
