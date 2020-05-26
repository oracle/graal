/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.tests.BaseSuiteHarness;
import org.graalvm.polyglot.Context;
import org.junit.ClassRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;
import org.graalvm.polyglot.Value;

public class InteropTestBase {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(getContextBuilder());

    public static Context.Builder getContextBuilder() {
        String lib = System.getProperty("test.sulongtest.lib.path");
        return Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option(SulongEngineOption.LIBRARY_PATH_NAME, lib).option(SulongEngineOption.CXX_INTEROP_NAME, "true");
    }

    private static final Path testBase = Paths.get(TestOptions.TEST_SUITE_PATH, "interop");
    public static final String TEST_FILE_NAME = "O1." + NFIContextExtension.getNativeLibrarySuffix();

    protected static TruffleObject loadTestBitcodeInternal(String name) {
        File file = Paths.get(testBase.toString(), name + BaseSuiteHarness.TEST_DIR_EXT, TEST_FILE_NAME).toFile();
        TruffleFile tf = runWithPolyglot.getTruffleTestEnv().getPublicTruffleFile(file.toURI());
        Source source;
        try {
            source = Source.newBuilder("llvm", tf).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parsePublic(source);
        return (TruffleObject) target.call();
    }

    protected static Value loadTestBitcodeValue(String name) {
        File file = Paths.get(testBase.toString(), name + BaseSuiteHarness.TEST_DIR_EXT, TEST_FILE_NAME).toFile();
        org.graalvm.polyglot.Source source;
        try {
            source = org.graalvm.polyglot.Source.newBuilder("llvm", file).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        return runWithPolyglot.getPolyglotContext().eval(source);
    }

    public static class SulongTestNode extends RootNode {

        private final Object function;
        @Child InteropLibrary interop;

        protected SulongTestNode(TruffleObject testLibrary, String fnName) {
            super(null);
            try {
                function = InteropLibrary.getFactory().getUncached().readMember(testLibrary, fnName);
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
            this.interop = InteropLibrary.getFactory().create(function);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return interop.execute(function, frame.getArguments());
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        }
    }

}
