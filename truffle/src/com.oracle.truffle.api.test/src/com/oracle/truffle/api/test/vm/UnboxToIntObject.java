/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@MessageResolution(receiverType = UnboxToIntObject.class)
public class UnboxToIntObject implements TruffleObject {
    private final int value;

    public UnboxToIntObject(int value) {
        this.value = value;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof UnboxToIntObject;
    }

    @Resolve(message = "IS_NULL")
    abstract static class IsNullNode extends Node {
        boolean access(UnboxToIntObject obj) {
            return obj.value == 0;
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNode extends Node {
        boolean access(UnboxToIntObject obj) {
            return obj.value != 0;
        }
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        int access(UnboxToIntObject obj) {
            return obj.value;
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {
        @SuppressWarnings("unused")
        UnboxToIntObject access(UnboxToIntObject obj, Object[] arguments) {
            return obj;
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return UnboxToIntObjectForeign.ACCESS;
    }

    @TruffleLanguage.Registration(mimeType = "application/x-unbox", name = "Unboxing lang", version = "0.1")
    public static class MyLang extends TruffleLanguage<Object> {

        @Override
        protected Object createContext(Env env) {
            return null;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            int initialValue = Integer.parseInt(request.getSource().getCharacters().toString());
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return new UnboxToIntObject(initialValue);
                }
            });
        }

        @Override
        protected Object getLanguageGlobal(Object context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected String toString(Object context, Object value) {
            assertTrue("Our value: " + value, value instanceof UnboxToIntObject);
            return "Unboxed: " + ((UnboxToIntObject) value).value;
        }
    }
}
