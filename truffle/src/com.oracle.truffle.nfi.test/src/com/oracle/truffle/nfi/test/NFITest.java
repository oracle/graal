/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.tck.TruffleRunner;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;

public class NFITest {

    protected static final InteropLibrary UNCACHED_INTEROP = InteropLibrary.getFactory().getUncached();

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(Context.newBuilder().allowNativeAccess(true).allowIO(IOAccess.ALL));

    protected static Object defaultLibrary;
    protected static Object testLibrary;

    private static CallTarget lookupAndBind;

    static final String TEST_BACKEND = System.getProperty("native.test.backend");

    static final boolean IS_LINUX = System.getProperty("os.name").equals("Linux");
    static final boolean IS_DARWIN = System.getProperty("os.name").equals("Mac OS X") || System.getProperty("os.name").equals("Darwin");
    static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    static final boolean IS_AMD64 = System.getProperty("os.arch").equals("amd64");

    protected static String getLibPath(String lib) {
        String filename;
        if (IS_LINUX) {
            filename = String.format("lib%s.so", lib);
        } else if (IS_DARWIN) {
            filename = String.format("lib%s.dylib", lib);
        } else {
            assert IS_WINDOWS;
            filename = String.format("%s.dll", lib);
        }

        String propName = "native.test.path";
        if (TEST_BACKEND != null) {
            propName = propName + "." + TEST_BACKEND;
        }

        String path = System.getProperty(propName);
        Path absPath = Paths.get(path, filename).toAbsolutePath();
        return absPath.toString();
    }

    protected static Source parseSource(String source) {
        String sourceString;
        if (TEST_BACKEND != null) {
            sourceString = String.format("with %s %s", TEST_BACKEND, source);
        } else {
            sourceString = source;
        }

        return Source.newBuilder("nfi", sourceString, "loadLibrary").internal(true).build();
    }

    protected static Object loadLibrary(String lib) {
        Source source = parseSource(lib);
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parseInternal(source);
        return target.call();
    }

    @BeforeClass
    public static void loadLibraries() {
        try {
            defaultLibrary = loadLibrary("default");
        } catch (Exception ex) {
            defaultLibrary = null;
        }
        testLibrary = loadLibrary("load '" + getLibPath("nativetest") + "'");
        lookupAndBind = new LookupAndBindNode().getCallTarget();
    }

    private static final class LookupAndBindNode extends RootNode {

        @Child InteropLibrary libInterop = InteropLibrary.getFactory().createDispatched(5);
        @Child SignatureLibrary signatures = SignatureLibrary.getFactory().createDispatched(5);

        private LookupAndBindNode() {
            super(runWithPolyglot.getTestLanguage());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object library = frame.getArguments()[0];
            String symbolName = (String) frame.getArguments()[1];
            Object signature = frame.getArguments()[2];

            try {
                Object symbol = libInterop.readMember(library, symbolName);
                return signatures.bind(signature, symbol);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    protected abstract static class NFITestRootNode extends RootNode {

        protected NFITestRootNode() {
            super(null);
        }

        protected static InteropLibrary getInterop() {
            return InteropLibrary.getFactory().createDispatched(5);
        }

        protected static InteropLibrary getInterop(Object receiver) {
            return InteropLibrary.getFactory().create(receiver);
        }

        @TruffleBoundary
        protected static void assertEquals(Object expected, Object actual) {
            Assert.assertEquals(expected, actual);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                return executeTest(frame);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        public abstract Object executeTest(VirtualFrame frame) throws InteropException;
    }

    protected static class SendExecuteNode extends NFITestRootNode {

        private final Object receiver;

        @Child InteropLibrary interop;

        protected SendExecuteNode(String symbol, String signature) {
            this(lookupAndBind(symbol, signature));
        }

        protected SendExecuteNode(Object receiver) {
            this.receiver = receiver;
            this.interop = getInterop(receiver);
        }

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return interop.execute(receiver, frame.getArguments());
        }
    }

    protected static Object lookupAndBind(String name, String signature) {
        return lookupAndBind(testLibrary, name, signature);
    }

    protected static Object lookupAndBindDefault(String name, String signature) {
        if (IS_WINDOWS) {
            return lookupAndBind(testLibrary, "reexport_" + name, signature);
        } else {
            return lookupAndBind(defaultLibrary, name, signature);
        }
    }

    protected static Object parseSignature(String signature) {
        try {
            Source sigSource = parseSource(signature);
            CallTarget sigTarget = runWithPolyglot.getTruffleTestEnv().parseInternal(sigSource);
            return sigTarget.call();
        } catch (AbstractTruffleException ex) {
            if (ex.getClass().getSimpleName().equals("NFIUnsupportedTypeException")) {
                Assume.assumeNoException(ex);
            }
            throw ex;
        }
    }

    protected static Object lookupAndBind(Object library, String name, String signature) {
        return lookupAndBind.call(library, name, parseSignature(signature));
    }
}
