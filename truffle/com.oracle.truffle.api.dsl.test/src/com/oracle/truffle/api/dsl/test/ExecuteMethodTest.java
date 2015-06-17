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
package com.oracle.truffle.api.dsl.test;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class ExecuteMethodTest {

    private static final String ERROR_NO_EXECUTE = "No accessible and overridable generic execute method found. Generic execute methods usually have the signature 'public abstract {Type} "
                    + "execute(VirtualFrame)' and must not throw any checked exceptions.";

    @TypeSystem({int.class})
    @DSLOptions(useNewLayout = true)
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
    abstract static class ExecuteWithFrameError3 extends Node {

        abstract Object executeFrame(VirtualFrame frame);

        @Specialization
        @ExpectError("Method signature (MaterializedFrame, int) does not match to the expected signature:%")
        int doInt(@SuppressWarnings("unused") MaterializedFrame frame, int a) {
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
