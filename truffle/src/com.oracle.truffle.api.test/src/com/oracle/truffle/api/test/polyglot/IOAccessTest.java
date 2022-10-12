/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

public class IOAccessTest {

    @Test
    @SuppressWarnings("deprecation")
    public void testInvalid() {
        AbstractPolyglotTest.assertFails(() -> Context.newBuilder().allowIO(true).allowIO(IOAccess.ALL).build(),
                        IllegalArgumentException.class,
                        (iae) -> assertEquals("The method allowIO with boolean and with IOAccess are mutually exclusive.", iae.getMessage()));

        AbstractPolyglotTest.assertFails(() -> Context.newBuilder().fileSystem(new MemoryFileSystem()).allowIO(IOAccess.ALL).build(),
                        IllegalArgumentException.class,
                        (iae) -> assertEquals("The method allowIO with IOAccess and the method fileSystem are mutually exclusive.", iae.getMessage()));

        AbstractPolyglotTest.assertFails(() -> IOAccess.newBuilder().allowHostFileAccess(true).fileSystem(new MemoryFileSystem()).build(),
                        IllegalArgumentException.class,
                        (iae) -> assertEquals("The allow host file access and custom filesystem are mutually exclusive.", iae.getMessage()));
    }

    @Test
    public void testFileAccess() {
        // Default context - file access is disabled
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestFileAccessDisabledLanguage.class, "");
        }

        // IOAccess#NONE - file access is disabled
        try (Context context = Context.newBuilder().allowIO(IOAccess.NONE).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestFileAccessDisabledLanguage.class, "");
        }

        // IOAccess#ALL - file access is enabled
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestFileAccessEnabledLanguage.class, "");
        }

        // IOAccess#NONE with enabled file access
        IOAccess ioAccess = IOAccess.newBuilder(IOAccess.NONE).allowHostFileAccess(true).build();
        try (Context context = Context.newBuilder().allowIO(ioAccess).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestFileAccessEnabledLanguage.class, "");
        }
    }

    @Test
    public void testFileAccessWithCustomFileSystem() throws Exception {
        // GR-32704 FileSystem forwarding is not yet supported.
        TruffleTestAssumptions.assumeWeakEncapsulation();

        // IOAccess with custom file system - file access is enabled
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        try (Context context = Context.newBuilder().allowIO(ioAccess).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestFileAccessEnabledLanguage.class, "");
        }
    }

    @TruffleLanguage.Registration
    public static final class TestFileAccessEnabledLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return env.getPublicTruffleFile("test").exists();
        }
    }

    @TruffleLanguage.Registration
    public static final class TestFileAccessDisabledLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            AbstractPolyglotTest.assertFails(() -> env.getPublicTruffleFile("test").exists(), SecurityException.class);
            return null;
        }
    }

    @Test
    public void testSocketAccess() {
        // Default context - socket access is disabled
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestSocketAccessDisabledLanguage.class, "");
        }

        // IOAccess#NONE - socket access is disabled
        try (Context context = Context.newBuilder().allowIO(IOAccess.NONE).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestSocketAccessDisabledLanguage.class, "");
        }

        // IOAccess#ALL - socket access is enabled
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestSocketAccessEnabledLanguage.class, "");
        }

        // IOAccess#NONE with enabled socket access
        IOAccess ioAccess = IOAccess.newBuilder(IOAccess.NONE).allowHostSocketAccess(true).build();
        try (Context context = Context.newBuilder().allowIO(ioAccess).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestSocketAccessEnabledLanguage.class, "");
        }
    }

    @Test
    public void testSocketAccessWithCustomFileSystem() throws Exception {
        // GR-32704 FileSystem forwarding is not yet supported.
        TruffleTestAssumptions.assumeWeakEncapsulation();

        // IOAccess with custom file system - socket access is disabled
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        try (Context context = Context.newBuilder().allowIO(ioAccess).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestSocketAccessDisabledLanguage.class, "");
        }

        // IOAccess with custom file system and enabled socket access
        ioAccess = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).allowHostSocketAccess(true).build();
        try (Context context = Context.newBuilder().allowIO(ioAccess).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestSocketAccessEnabledLanguage.class, "");
        }
    }

    @TruffleLanguage.Registration
    public static final class TestSocketAccessEnabledLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            URL resource = new URL("http://localhost:1234/test.js");
            try {
                Source.newBuilder("TestJS", resource).build();
            } catch (IOException ioException) {
                // Expected IOException, there is no running http server on localhost:1234
            }
            return null;
        }
    }

    @TruffleLanguage.Registration
    public static final class TestSocketAccessDisabledLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            URL resource = new URL("http://localhost:1234/test.js");
            AbstractPolyglotTest.assertFails(() -> Source.newBuilder("TestJS", resource).build(), SecurityException.class);
            return null;
        }
    }
}
