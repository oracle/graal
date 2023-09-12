/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.tests.interop.values.StructObject;
import com.oracle.truffle.llvm.tests.services.TestEngineConfig;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import com.oracle.truffle.tck.TruffleRunner.RunWithPolyglotRule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import static org.hamcrest.CoreMatchers.is;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

@RunWith(TruffleRunner.class)
public class MultiContextBuiltinsTest {

    static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    static Engine sharedEngine;

    @BeforeClass
    public static void createEngine() {
        sharedEngine = Engine.newBuilder().allowExperimentalOptions(true).options(getEngineOptions()).build();
    }

    private static Map<String, String> getEngineOptions() {
        return TestEngineConfig.getInstance().getContextOptions();
    }

    @AfterClass
    public static void closeEngine() {
        sharedEngine.close();
    }

    public static Context.Builder getContextBuilder() {
        Context.Builder builder = Context.newBuilder().allowAllAccess(true).engine(sharedEngine);
        InteropTestBase.updateContextBuilder(builder);
        return builder;
    }

    @Rule public RunWithPolyglotRule runWithPolyglot = new RunWithPolyglotRule(getContextBuilder());

    Object createHandle;
    Object resolveHandle;

    @Before
    public void load() throws InteropException {
        Env env = runWithPolyglot.getTruffleTestEnv();
        LanguageInfo llvm = env.getPublicLanguages().get("llvm");
        Toolchain toolchain = env.lookup(llvm, Toolchain.class);

        // this test uses handle functions, which are only defined in native mode
        Assume.assumeThat("test only valid in native mode", toolchain.getIdentifier(), is("native"));

        File file = InteropTestBase.getTestBitcodeFile(runWithPolyglot.getPolyglotContext(), "builtins.c");
        TruffleFile tf = runWithPolyglot.getTruffleTestEnv().getPublicTruffleFile(file.toURI());
        Source source;
        try {
            source = Source.newBuilder("llvm", tf).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }

        Object testLib = runWithPolyglot.getTruffleTestEnv().parsePublic(source).call();
        createHandle = INTEROP.invokeMember(testLib, "getCreateHandleFn");
        resolveHandle = INTEROP.invokeMember(testLib, "getResolveHandleFn");
    }

    public class InteropExecute extends RootNode {

        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(3);

        public InteropExecute() {
            super(runWithPolyglot.getTestLanguage());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object receiver = frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                return interop.execute(receiver, args);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError(ex);
            }
        }
    }

    @Test
    public void testHandle(@Inject(InteropExecute.class) CallTarget callCreateHandle,
                    @Inject(InteropExecute.class) CallTarget callResolveHandle) {
        Object obj = new StructObject(null);
        Object handle = callCreateHandle.call(createHandle, obj);
        Assert.assertTrue("handle is native pointer", INTEROP.isPointer(handle));

        Object resolved = callResolveHandle.call(resolveHandle, handle);
        Assert.assertSame("got same object back", obj, resolved);
    }

    @Test
    public void testHandle2(@Inject(InteropExecute.class) CallTarget callCreateHandle,
                    @Inject(InteropExecute.class) CallTarget callResolveHandle) {
        // runs in a different context, other than that it's identical to testHandle
        testHandle(callCreateHandle, callResolveHandle);
    }
}
