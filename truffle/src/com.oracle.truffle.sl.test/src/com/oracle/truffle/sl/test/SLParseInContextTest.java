/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

public class SLParseInContextTest {
    private Context context;

    @Before
    public void setup() throws Exception {
        context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build();
    }

    @After
    public void tearDown() throws Exception {
        context.close();
    }

    @Test
    public void parseAPlusB() throws Exception {
        Value value = context.eval("x-test-eval", "");
        assertTrue("Result is a number: " + value, value.isNumber());
        assertEquals(42, value.asInt());
    }

    @TruffleLanguage.Registration(id = "x-test-eval", name = "EvalLang", version = "1.0")
    public static final class EvalLang extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return parseAndEval();
                }

                @TruffleBoundary
                private Object parseAndEval() {
                    Source aPlusB = Source.newBuilder("sl", "a + b", "plus.sl").build();
                    return CONTEXT_REF.get(this).parsePublic(aPlusB, "a", "b").call(30, 12);
                }
            }.getCallTarget();
        }

        private static final ContextReference<Env> CONTEXT_REF = ContextReference.create(EvalLang.class);

    }
}
