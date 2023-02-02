/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.source.Source;
import org.junit.Assume;

/**
 * NFI provides a native isolated namespace on Linux (via dlmopen); one per NFI context.
 *
 * This test ensures that the same library can be loaded twice, in two different contexts, and that
 * the libraries are effectively isolated.
 */
public class IsolatedNamespaceTest extends NFITest {

    private static class IsolatedGetAndSet implements AutoCloseable {

        private final TruffleContext ctx;
        private final Object getAndSet;

        IsolatedGetAndSet(Env env, Source librarySource, Source signatureSource) {
            this.ctx = env.newInnerContextBuilder().inheritAllAccess(true).build();
            Object library;
            try {
                library = ctx.evalInternal(null, librarySource);
            } catch (AbstractTruffleException ex) {
                if (ex.getMessage().contains("not supported")) {
                    Assume.assumeNoException(ex);
                }
                throw ex;
            }

            try {
                Object symbol = UNCACHED_INTEROP.readMember(library, "get_and_set");
                Object signature = ctx.evalInternal(null, signatureSource);
                this.getAndSet = UNCACHED_INTEROP.invokeMember(signature, "bind", symbol);
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        }

        int execute(int arg) throws InteropException {
            Object prevCtx = ctx.enter(null);
            try {
                return (int) UNCACHED_INTEROP.execute(getAndSet, arg);
            } finally {
                ctx.leave(null, prevCtx);
            }
        }

        @Override
        public void close() {
            ctx.close();
        }
    }

    @Test
    public void testIsolatedNamespace() throws InteropException {
        Source signature = parseSource("(sint32) : sint32");
        Source library = parseSource(String.format("load(ISOLATED_NAMESPACE) '%s'", getLibPath("isolationtest")));

        Env env = runWithPolyglot.getTruffleTestEnv();
        try (IsolatedGetAndSet getAndSet1 = new IsolatedGetAndSet(env, library, signature);
                        IsolatedGetAndSet getAndSet2 = new IsolatedGetAndSet(env, library, signature)) {
            getAndSet1.execute(123);
            getAndSet2.execute(456);

            Assert.assertEquals(123, getAndSet1.execute(321));
            Assert.assertEquals(456, getAndSet2.execute(654));
        }
    }
}
