/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.parser.ParseSignatureTestFactory.InlineCacheNodeGen;
import com.oracle.truffle.nfi.test.parser.backend.NFITestBackend.ArrayType;
import com.oracle.truffle.nfi.test.parser.backend.TestCallInfo;
import com.oracle.truffle.nfi.test.parser.backend.TestSignature;
import com.oracle.truffle.tck.TruffleRunner.RunWithPolyglotRule;
import java.util.Arrays;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;

public class ParseSignatureTest {

    @ClassRule public static RunWithPolyglotRule runWithPolyglot = new RunWithPolyglotRule();

    private static InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    static Object testSymbol;

    @BeforeClass
    public static void loadTestSymbol() throws InteropException {
        Source source = Source.newBuilder("nfi", "with test default", "ParseSignatureTest").internal(true).build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parseInternal(source);
        Object library = target.call();
        testSymbol = INTEROP.readMember(library, "testSymbol");
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class InlineCacheNode extends Node {

        abstract Object execute(CallTarget callTarget);

        @NeverDefault
        static DirectCallNode createInlined(CallTarget callTarget) {
            DirectCallNode ret = DirectCallNode.create(callTarget);
            ret.forceInlining();
            return ret;
        }

        @Specialization(limit = "1", guards = "call.getCallTarget() == callTarget")
        Object doCached(@SuppressWarnings("unused") CallTarget callTarget,
                        @Cached("createInlined(callTarget)") DirectCallNode call) {
            return call.call();
        }

        // no specialization for the polymorphic case
        // if we need it, something is wrong with code caching
    }

    class ParseSignatureNode extends RootNode {

        final Source source;
        @Child InlineCacheNode inlineCache = InlineCacheNodeGen.create();

        protected ParseSignatureNode(String format, Object... args) {
            this(String.format(format, args));
        }

        protected ParseSignatureNode(String signature) {
            super(null);
            this.source = Source.newBuilder("nfi", String.format("with test %s", signature), "ParseSignatureTest").internal(true).build();
        }

        @TruffleBoundary
        CallTarget parse() {
            return runWithPolyglot.getTruffleTestEnv().parseInternal(source);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return inlineCache.execute(parse());
        }
    }

    static Object[] mkArgs(int count) {
        Object[] ret = new Object[count];
        // 0 is compatible with all numeric types
        Arrays.fill(ret, 0);
        return ret;
    }

    protected static TestSignature getSignature(CallTarget parsedSignature, int argCount) {
        Object ret = parsedSignature.call();
        try {
            TestCallInfo info = (TestCallInfo) SignatureLibrary.getUncached().call(ret, testSymbol, mkArgs(argCount));
            return info.signature;
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    protected static Matcher<Object> isArrayType(NativeSimpleType expected) {
        return new TypeSafeMatcher<>(ArrayType.class) {

            @Override
            protected boolean matchesSafely(Object item) {
                ArrayType type = (ArrayType) item;
                return type.base == expected;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("array of ").appendText(expected.name());
            }
        };
    }
}
