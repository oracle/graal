/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotEngine;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class InvokeAndReadExecTest {
    interface Tester {
        String test(String param);
    }

    @Test
    public void invokeAndReadShallBehaveTheSame() {
        InvokeObject obj1 = new InvokeObject();
        ReadExecObject obj2 = new ReadExecObject();

        PolyglotEngine engine = PolyglotEngine.newBuilder().globalSymbol("obj1", obj1).globalSymbol("obj2", obj2).build();

        Tester t1 = engine.findGlobalSymbol("obj1").as(Tester.class);
        Tester t2 = engine.findGlobalSymbol("obj2").as(Tester.class);

        assertEquals("Invoked Hi", t1.test("Hi"));
        assertEquals("Executed Hello", t2.test("Hello"));
        assertEquals("Invoked Hello", t1.test("Hello"));
        assertEquals("Executed Hi", t2.test("Hi"));
    }

    @MessageResolution(receiverType = InvokeObject.class)
    static final class InvokeObject implements TruffleObject {

        @Resolve(message = "INVOKE")
        abstract static class InvokeImpl extends Node {
            @SuppressWarnings("unused")
            protected Object access(InvokeObject obj, String name, Object... args) {
                if (name.equals("test")) {
                    return "Invoked " + args[0];
                }
                return JavaInterop.asTruffleValue(null);
            }
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return InvokeObjectForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof InvokeObject;
        }
    }

    @MessageResolution(receiverType = ReadExecObject.class)
    static final class ReadExecObject implements TruffleObject {
        @Resolve(message = "READ")
        abstract static class ReadImpl extends Node {
            protected Object access(ReadExecObject obj, String name) {
                if (name.equals("test")) {
                    return obj;
                }
                return JavaInterop.asTruffleValue(null);
            }
        }

        @Resolve(message = "EXECUTE")
        @SuppressWarnings("unused")
        abstract static class ExecImpl extends Node {
            protected Object access(ReadExecObject obj, Object... args) {
                return "Executed " + args[0];
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecImpl extends Node {
            @SuppressWarnings("unused")
            protected Object access(ReadExecObject obj) {
                return true;
            }
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof ReadExecObject;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ReadExecObjectForeign.ACCESS;
        }

    }

    abstract static class Dummy extends TruffleLanguage<Object> {
    }
}
