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
import com.oracle.truffle.api.nodes.*;

public class ExecuteMethodTest {

    private static final String NO_EXECUTE = "No accessible and overridable generic execute method found. Generic execute methods usually have the signature 'public abstract {Type} "
                    + "executeGeneric(VirtualFrame)' and must not throw any checked exceptions.";

    @TypeSystem({int.class, Object[].class})
    static class ExecuteTypeSystem {

    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    abstract static class ValidChildNode extends Node {
        abstract Object execute();
    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    @ExpectError(NO_EXECUTE)
    abstract static class ExecuteThis1 extends Node {

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    @ExpectError(NO_EXECUTE)
    abstract static class ExecuteThis2 extends Node {

        abstract Object execute() throws UnexpectedResultException;

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    @ExpectError(NO_EXECUTE)
    abstract static class ExecuteThis3 extends Node {

        abstract int execute() throws UnexpectedResultException;

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    abstract static class ExecuteThis4 extends Node {

        protected abstract Object execute();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    abstract static class ExecuteThis5 extends Node {

        public abstract Object execute();

        @Specialization
        int doInt(int a) {
            return a;
        }
    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    @ExpectError(NO_EXECUTE)
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

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    @ExpectError(NO_EXECUTE)
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

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
    @ExpectError("Multiple accessible and overridable generic execute methods found [executeInt(), executeObject()]. Remove all but one or mark all but one as final.")
    abstract static class ExecuteThis8 extends Node {

        abstract int executeInt();

        abstract Object executeObject();

        @Specialization
        int doInt(int a) {
            return a;
        }

    }

    @TypeSystemReference(ExecuteTypeSystem.class)
    @NodeChild(value = "a", type = ValidChildNode.class)
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

}
