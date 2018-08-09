/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.impl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(characterMimeTypes = "application/x-tck", id = "TCK", name = "TCK", version = "1.0")
public final class TckLanguage extends TruffleLanguage<Env> {

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source code = request.getSource();
        final RootNode root;
        final String txt = code.getCharacters().toString();
        if (txt.startsWith("TCK42:")) {
            int nextColon = txt.indexOf(":", 6);
            String mimeType = txt.substring(6, nextColon);
            Source toParse = Source.newBuilder(txt.substring(nextColon + 1)).name("src.tck").mimeType(mimeType).build();
            root = new MultiplyNode(this, toParse);
        } else {
            final double value = Double.parseDouble(txt);
            root = RootNode.createConstantNode(value);
        }
        return Truffle.getRuntime().createCallTarget(root);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    private static final class MultiplyNode extends RootNode implements TruffleObject, ForeignAccess.Factory {
        private final Source code;

        MultiplyNode(TckLanguage language, Source toParse) {
            super(language);
            this.code = toParse;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Env env = getLanguage(TckLanguage.class).getContextReference().get();
            Object[] arguments = frame.getArguments();
            return parseAndEval(env, arguments);
        }

        @TruffleBoundary
        private Object parseAndEval(Env env, Object[] arguments) {
            if (arguments.length == 0) {
                return this;
            }
            CallTarget call;
            try {
                call = env.parse(code, (String) arguments[1], (String) arguments[2]);
            } catch (Exception ex) {
                throw new AssertionError("Cannot parse " + code, ex);
            }
            return call.call(6, 7);
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(this);
        }

        @Override
        public boolean canHandle(TruffleObject obj) {
            return obj instanceof MultiplyNode;
        }

        @Override
        public CallTarget accessMessage(Message tree) {
            if (tree == Message.IS_EXECUTABLE) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.TRUE));
            } else if (Message.EXECUTE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(this);
            } else {
                throw UnsupportedMessageException.raise(tree);
            }
        }

    }

    public static Number expectNumber(Object o) {
        if (o instanceof Number) {
            return (Number) o;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalArgumentException(o + " not a Number");
    }

    public static String expectString(Object o) {
        if (o instanceof String) {
            return (String) o;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalArgumentException(o + " not a String");
    }

    public static TruffleObject expectTruffleObject(Object o) {
        if (o instanceof TruffleObject) {
            return (TruffleObject) o;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalArgumentException(o + " not a TruffleObject");
    }

    public static int checkBounds(int idx, int size) {
        if (idx < 0 || idx >= size) {
            CompilerDirectives.transferToInterpreter();
            throw new IndexOutOfBoundsException("Index: " + idx + " Size: " + size);
        }
        return idx;
    }

}
