/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.ExecuteMethodTestFactory.SpecializationMethodOverload1NodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteMethodTestFactory.SpecializationMethodOverload2NodeGen;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ExecuteMethodTest {

    private static final String ERROR_NO_EXECUTE = "No accessible and overridable generic execute method found. Generic execute methods usually have the signature 'public abstract {Type} " +
                    "execute(VirtualFrame)'.";

    @TypeSystem({int.class})
    static class ExecuteMethodTypes {
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    abstract static class ChildNoFrame extends Node {
        abstract Object execute();
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    @ExpectError(ERROR_NO_EXECUTE)
    abstract static class ExecuteThis1 extends Node {

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    @ExpectError(ERROR_NO_EXECUTE)
    abstract static class ExecuteThis2 extends Node {

        abstract Object execute() throws UnexpectedResultException;

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    @ExpectError(ERROR_NO_EXECUTE)
    abstract static class ExecuteThis3 extends Node {

        abstract int execute() throws UnexpectedResultException;

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThis4 extends Node {

        protected abstract Object execute();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThis5 extends Node {

        public abstract Object execute();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    @ExpectError(ERROR_NO_EXECUTE)
    abstract static class ExecuteThis6 extends Node {

        @SuppressWarnings({"unused", "static-method"})
        private Object execute() {
            return 0;
        }

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    @ExpectError(ERROR_NO_EXECUTE)
    abstract static class ExecuteThis7 extends Node {

        @SuppressWarnings("static-method")
        public final int executeInt() {
            return 0;
        }

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThis8 extends Node {

        abstract int executeInt();

        abstract Object executeObject();

        @Specialization
        int doInt(int a) {
            return a;
        }

    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThis9 extends Node {

        abstract int executeInt();

        // disambiguate executeObject
        final Object executeObject() {
            return executeInt();
        }

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThisVoid1 extends Node {

        abstract void executeVoid();

        @Specialization
        void doInt(@SuppressWarnings("unused") int a) {
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThisVoid2 extends Node {

        // allow one execute void
        abstract void executeVoid();

        abstract Object executeObject();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteThisVoid3 extends Node {

        // allow only one execute void
        abstract void executeVoid1();

        abstract void executeVoid2();

        abstract Object executeObject();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteWithFrame1 extends Node {

        // no frame in execute. no parameter in specializations
        abstract Object executeNoFrame();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteWithFrame2 extends Node {

        // frame in execute also usable in specialization
        abstract Object executeWithFrame(VirtualFrame frame);

        @Specialization
        int doInt(@SuppressWarnings("unused") VirtualFrame frame, int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteWithFrame3 extends Node {

        abstract Object executeWithFrame(Frame frame);

        @Specialization
        int doInt(@SuppressWarnings("unused") Frame frame, int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ExecuteWithFrame4.class)
    abstract static class ExecuteWithFrame4 extends Node {

        abstract Object executeWithFrame(MaterializedFrame frame);

        @Specialization
        int doInt(@SuppressWarnings("unused") MaterializedFrame frame, int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteWithFrameError1 extends Node {

        abstract Object executeNoFrame();

        @Specialization
        @ExpectError("Method signature (VirtualFrame, int) does not match to the expected signature:%")
        int doInt(@SuppressWarnings("unused") VirtualFrame frame, int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteWithFrameError2 extends Node {

        abstract Object executeFrame(MaterializedFrame frame);

        @Specialization
        @ExpectError("Method signature (VirtualFrame, int) does not match to the expected signature:%")
        int doInt(@SuppressWarnings("unused") VirtualFrame frame, int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    @ExpectError("Invalid inconsistent frame types [MaterializedFrame, VirtualFrame] found for the declared execute methods.%")
    abstract static class ExecuteWithFrameError4 extends Node {

        abstract Object execute(VirtualFrame frame);

        abstract int executeInt(MaterializedFrame frame) throws UnexpectedResultException;

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteWithFrameError5 extends Node {

        abstract Object execute();

        abstract int executeInt(MaterializedFrame frame) throws UnexpectedResultException;

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    abstract static class ChildVirtualFrame extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    abstract static class ChildMaterializedFrame extends Node {
        abstract Object execute(MaterializedFrame frame);
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    abstract static class ChildFrame extends Node {
        abstract Object execute(Frame frame);
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildNoFrame.class)
    abstract static class ExecuteChildFrame1 extends Node {

        abstract Object execute(VirtualFrame frame);

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildFrame.class)
    abstract static class ExecuteChildFrame2 extends Node {

        abstract Object execute(VirtualFrame frame);

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildFrame.class)
    abstract static class ExecuteChildFrame3 extends Node {

        abstract Object execute(MaterializedFrame frame);

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildFrame.class)
    abstract static class ExecuteChildFrame4 extends Node {

        abstract Object execute(Frame frame);

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    abstract static class SpecializationMethodOverload1Node extends Node {

        public abstract String execute(int value);

        public abstract String execute(Object value);

        @Specialization(guards = "value < 10")
        String test(@SuppressWarnings("unused") int value) {
            return "value < 10";
        }

        @Specialization
        String test(@SuppressWarnings("unused") Object value) {
            return "any value";
        }

    }

    @Test
    public void testSpecializationMethodOverload1Node() {
        SpecializationMethodOverload1Node node = SpecializationMethodOverload1NodeGen.create();
        assertEquals("any value", node.execute(100));
        assertEquals("any value", node.execute(100));
    }

    abstract static class SpecializationMethodOverload2Node extends Node {

        public abstract String execute(String value);

        public abstract String execute(CharSequence value);

        @Specialization(guards = "guard(value)")
        String test(@SuppressWarnings("unused") String value) {
            return "is string";
        }

        protected static boolean guard(Object value) {
            return value.equals("string");
        }

        @Specialization
        String test(@SuppressWarnings("unused") CharSequence value) {
            return "any value";
        }
    }

    @Test
    public void testSpecializationMethodOverload2Node() {
        SpecializationMethodOverload2Node node = SpecializationMethodOverload2NodeGen.create();
        assertEquals("any value", node.execute("foobar"));
        assertEquals("any value", node.execute("foobar"));
    }

    @ExpectError("No generic execute method found with 0 evaluated arguments for node type ChildVirtualFrame and frame types [com.oracle.truffle.api.frame.Frame].")
    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildVirtualFrame.class)
    abstract static class ExecuteChildFrameError1 extends Node {

        abstract Object execute(Frame frame);

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @ExpectError("No generic execute method found with 0 evaluated arguments for node type ChildFrame and frame types [].")
    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildFrame.class)
    abstract static class ExecuteChildFrameError2 extends Node {

        abstract Object execute();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @ExpectError("No generic execute method found with 0 evaluated arguments for node type ChildVirtualFrame and frame types [].")
    @TypeSystemReference(ExecuteMethodTypes.class)
    @NodeChild(value = "a", type = ChildVirtualFrame.class)
    abstract static class ExecuteChildFrameError3 extends Node {

        abstract Object execute();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

}
