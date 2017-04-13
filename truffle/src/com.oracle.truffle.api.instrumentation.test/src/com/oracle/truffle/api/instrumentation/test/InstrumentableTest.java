/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import java.io.IOException;

import org.junit.Test;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

public class InstrumentableTest {

    @Instrumentable(factory = TestNode1Wrapper.class)
    public abstract static class TestNode1 extends Node {

        public abstract void execute1();

        public abstract Object execute2();

        public abstract int execute3();

        public abstract String execute4();

        public abstract double execute5();

        public abstract long execute6();

        public abstract float execute7();

        public abstract short execute8();

        public abstract byte execute9();

        public abstract Object execute10(VirtualFrame frame1);

        public abstract Object execute11(int a, VirtualFrame frame1);

        public abstract Object execute12(VirtualFrame frame1, int b);

        public abstract Object execute13(int a, VirtualFrame frame1, int b);

        public abstract byte execute15() throws UnexpectedResultException;

        public abstract byte execute16() throws IOException;

        public abstract Object execute17(int a, VirtualFrame frame1, int b) throws UnexpectedResultException;

        @SuppressWarnings("unused")
        public Object execute18(int a, VirtualFrame frame1, int b) throws UnexpectedResultException {
            return null;
        }
    }

    // test constructor with source section
    @Instrumentable(factory = TestNode2Wrapper.class)
    public abstract static class TestNode2 extends Node {

        private final SourceSection sourceSection;

        public TestNode2(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public abstract void execute1();

    }

    // test copy constructor
    @Instrumentable(factory = TestNode3Wrapper.class)
    public abstract static class TestNode3 extends Node {

        public TestNode3(@SuppressWarnings("unused") TestNode3 sourceSection) {
        }

        public abstract void execute1();

    }

    @Test
    public void testTestNode1() {
    }

    @ExpectError("Class must not be final to generate a wrapper.")
    @Instrumentable(factory = TestErrorFactory.class)
    public static final class ErrorNode0 extends Node {
    }

    @ExpectError("Class must be public to generate a wrapper.")
    @Instrumentable(factory = TestErrorFactory.class)
    static class ErrorNode2 extends Node {
    }

    @ExpectError("Inner class must be static to generate a wrapper.")
    @Instrumentable(factory = TestErrorFactory.class)
    public class ErrorNode3 extends Node {
    }

    @ExpectError("No methods starting with name execute found to wrap.")
    @Instrumentable(factory = TestErrorFactory.class)
    public static class ErrorNode4 extends Node {

        @SuppressWarnings("unused")
        private void execute1() {
        }

        void execute2() {
        }

        public final void execute3() {
        }
    }

    @ExpectError("Unable to implement unknown abstract method foobar() in generated wrapper node.")
    @Instrumentable(factory = TestErrorFactory.class)
    public abstract static class ErrorNode5 extends Node {

        public abstract void foobar();
    }

    @ExpectError("No suiteable constructor found for wrapper factory generation. At least one default or copy constructor must be visible.")
    @Instrumentable(factory = TestErrorFactory.class)
    @SuppressWarnings("unused")
    public abstract static class ErrorNode6 extends Node {

        ErrorNode6() {
        }

        private ErrorNode6(SourceSection notVisible) {
        }

        private ErrorNode6(ErrorNode6 copyNotVisible) {
        }

        public ErrorNode6(int a, int b) {
        }

        public ErrorNode6(String foobar) {
        }
    }

}
