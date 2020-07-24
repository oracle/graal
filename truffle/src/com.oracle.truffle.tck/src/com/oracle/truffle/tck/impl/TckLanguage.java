/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.impl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
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
            Source toParse = Source.newBuilder(code.getLanguage(), txt.substring(nextColon + 1), "src.tck").mimeType(mimeType).build();
            root = new MultiplyNode(this, toParse);
        } else {
            final double value = Double.parseDouble(txt);
            root = RootNode.createConstantNode(value);
        }
        return Truffle.getRuntime().createCallTarget(root);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MultiplyNode extends RootNode implements TruffleObject {
        private final Source code;

        MultiplyNode(TckLanguage language, Source toParse) {
            super(language);
            this.code = toParse;
        }

        @Override
        @Ignore
        public Object execute(VirtualFrame frame) {
            Env env = lookupContextReference(TckLanguage.class).get();
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
                call = env.parsePublic(code, (String) arguments[1], (String) arguments[2]);
            } catch (Exception ex) {
                throw new AssertionError("Cannot parse " + code, ex);
            }
            return call.call(6, 7);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            return execute(Truffle.getRuntime().createVirtualFrame(arguments, getFrameDescriptor()));
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
