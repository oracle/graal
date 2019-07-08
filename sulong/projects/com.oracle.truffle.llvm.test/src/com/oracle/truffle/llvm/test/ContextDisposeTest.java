/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

public class ContextDisposeTest {
    private static final String TEST_ID = "test_lang";
    private static final String TEST_NAME = "TEST_LANG";

    public static class TestContext {
        final TruffleLanguage.Env env;

        public TestContext(TruffleLanguage.Env env) {
            this.env = env;
        }
    }

    @TruffleLanguage.Registration(id = TEST_ID, version = "1.0", internal = false, name = TEST_NAME)
    public static class TestLang extends TruffleLanguage<TestContext> {

        @Override
        protected TestContext createContext(TruffleLanguage.Env env) {
            return new TestContext(env);
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            TestContext context = getContextReference().get();
            Env env = context.env;
            LanguageInfo llvmInfo = env.getPublicLanguages().get(LLVMLanguage.ID);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            Assert.assertNotNull(toolchain.getIdentifier());
            Assert.assertNotNull(toolchain.getToolPath("CC"));
            Assert.assertNotNull(toolchain.getToolPath("CXX"));
            return arguments -> 42;
        }

    }

    @Test
    @SuppressWarnings("unused")
    public void testUninitializedDispose() {
        Context.Builder cb = Context.newBuilder(LLVMLanguage.ID, TEST_ID).allowAllAccess(true);
        try (Context ctx = cb.build()) {
            Value v = ctx.eval(TEST_ID, "");
            Assert.assertEquals(42, v.asInt());
        }
    }

}
