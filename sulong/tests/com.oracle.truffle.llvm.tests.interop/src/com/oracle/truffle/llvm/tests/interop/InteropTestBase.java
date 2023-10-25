/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource.OS;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.tests.CommonTestUtils;
import com.oracle.truffle.llvm.tests.options.TestOptions;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.ClassRule;

public class InteropTestBase {

    @BeforeClass
    public static void bundledOnly() {
        TestOptions.assumeBundledLLVM();
    }

    @ClassRule public static CommonTestUtils.RunWithTestEngineConfigRule runWithPolyglot = new CommonTestUtils.RunWithTestEngineConfigRule(InteropTestBase::updateContextBuilder);

    public static void updateContextBuilder(Context.Builder builder) {
        String lib = System.getProperty("test.sulongtest.lib.path");
        Map<String, String> options = new HashMap<>();
        options.put(SulongEngineOption.LIBRARY_PATH_NAME, lib);
        options.put(SulongEngineOption.CXX_INTEROP_NAME, "true");
        builder.allowAllAccess(true).allowExperimentalOptions(true).options(options);
    }

    protected static final Path testBase = Paths.get(TestOptions.getTestDistribution("SULONG_EMBEDDED_TEST_SUITES"), "interop");
    public static final String TEST_FILE_NAME = "toolchain-plain";

    protected static String getLibrary(String library) {
        runWithPolyglot.getPolyglotContext().initialize("llvm");
        return LLVMLanguage.get(null).getCapability(PlatformCapability.class).getLibrary(library);
    }

    public static OS getOS(Context context) {
        context.initialize("llvm");
        PlatformCapability<?> platform = LLVMLanguage.get(null).getCapability(PlatformCapability.class);
        return platform.getOS();
    }

    public static OS getOS() {
        return getOS(runWithPolyglot.getPolyglotContext());
    }

    static String getLibrarySuffixName(Context context, String fileName) {
        context.initialize("llvm");
        PlatformCapability<?> platform = LLVMLanguage.get(null).getCapability(PlatformCapability.class);
        // TODO: GR-41902 remove this platform dependent code
        if (platform.getOS() == OS.DARWIN) {
            return fileName + ".so";
        }
        return fileName + "." + platform.getLibrarySuffix();
    }

    public static String getTestLibraryName(Context context) {
        return getLibrarySuffixName(context, TEST_FILE_NAME);
    }

    public static String getTestLibraryName() {
        return getTestLibraryName(runWithPolyglot.getPolyglotContext());
    }

    public static File getTestBitcodeFile(Context context, String name) {
        return Paths.get(testBase.toString(), name + CommonTestUtils.TEST_DIR_EXT, getTestLibraryName(context)).toFile();
    }

    protected static File getTestBitcodeFile(String name) {
        return getTestBitcodeFile(runWithPolyglot.getPolyglotContext(), name);
    }

    protected static Object loadTestBitcodeInternal(String name) {
        File file = getTestBitcodeFile(name);
        CallTarget target = getTestBitcodeCallTarget(file);
        return target.call();
    }

    protected static CallTarget getTestBitcodeCallTarget(File file) {
        TruffleFile tf = runWithPolyglot.getTruffleTestEnv().getPublicTruffleFile(file.toURI());
        try {
            Source source = Source.newBuilder("llvm", tf).build();
            return runWithPolyglot.getTruffleTestEnv().parsePublic(source);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    protected static Value loadTestBitcodeValue(String name) {
        File file = getTestBitcodeFile(name);
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

        protected SulongTestNode(Object testLibrary, String fnName) {
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
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

}
