/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings.test;

import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;

@RunWith(Parameterized.class)
public class TStringJCodingsDisabledTest {

    @TruffleLanguage.Registration(name = "TRUFFLE_STRING_DUMMY_LANG_NO_J_CODINGS", id = TStringTestNoJCodingsDummyLanguage.ID, characterMimeTypes = "application/tstringdummynojcodings", version = "0.1")
    public static class TStringTestNoJCodingsDummyLanguage extends TruffleLanguage<TStringTestNoJCodingsDummyLanguage.NoJCodingsDummyLanguageContext> {

        public static final String ID = "tStringDummyLangNoJCodings";

        @Override
        protected CallTarget parse(ParsingRequest parsingRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected NoJCodingsDummyLanguageContext createContext(Env env) {
            return new NoJCodingsDummyLanguageContext(env);
        }

        @Override
        protected boolean patchContext(@SuppressWarnings("hiding") NoJCodingsDummyLanguageContext context, Env newEnv) {
            context.patchContext(newEnv);
            return true;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        public static final class NoJCodingsDummyLanguageContext {

            private static final ContextReference<NoJCodingsDummyLanguageContext> REFERENCE = ContextReference.create(TStringTestNoJCodingsDummyLanguage.class);

            @CompilerDirectives.CompilationFinal private Env env;

            NoJCodingsDummyLanguageContext(Env env) {
                this.env = env;
            }

            void patchContext(Env patchedEnv) {
                this.env = patchedEnv;
            }

            public Env getEnv() {
                return env;
            }

            public static NoJCodingsDummyLanguageContext get(Node node) {
                return REFERENCE.get(node);
            }
        }
    }

    static Context context;

    @BeforeClass
    public static void setUp() {
        context = Context.newBuilder(TStringTestNoJCodingsDummyLanguage.ID).build();
        context.enter();
    }

    @AfterClass
    public static void tearDown() {
        context.leave();
        context.close();
    }

    @Parameter public TruffleString.FromByteArrayNode node;
    @Parameter(1) public MutableTruffleString.FromByteArrayNode nodeMutable;

    @Parameters(name = "{0}, {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                        new Object[]{TruffleString.FromByteArrayNode.create(), MutableTruffleString.FromByteArrayNode.create()},
                        new Object[]{TruffleString.FromByteArrayNode.getUncached(), MutableTruffleString.FromByteArrayNode.getUncached()});
    }

    @Test(expected = AssertionError.class)
    public void testTruffleStringCopy() {
        node.execute(new byte[1], 0, 1, TruffleString.Encoding.Big5, true);
    }

    @Test(expected = AssertionError.class)
    public void testTruffleStringDirect() {
        node.execute(new byte[1], 0, 1, TruffleString.Encoding.Big5, false);
    }

    @Test(expected = AssertionError.class)
    public void testMutableTruffleStringCopy() {
        nodeMutable.execute(new byte[1], 0, 1, TruffleString.Encoding.Big5, true);
    }

    @Test(expected = AssertionError.class)
    public void testMutableTruffleStringDirect() {
        nodeMutable.execute(new byte[1], 0, 1, TruffleString.Encoding.Big5, false);
    }
}
