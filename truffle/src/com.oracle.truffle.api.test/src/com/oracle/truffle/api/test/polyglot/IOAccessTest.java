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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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
                        (iae) -> assertEquals("The method Context.Builder.allowIO(boolean) and the method Context.Builder.allowIO(IOAccess) are mutually exclusive.", iae.getMessage()));

        AbstractPolyglotTest.assertFails(() -> Context.newBuilder().fileSystem(new MemoryFileSystem()).allowIO(IOAccess.ALL).build(),
                        IllegalArgumentException.class,
                        (iae) -> assertEquals("The method Context.Builder.allowIO(IOAccess) and the method Context.Builder.fileSystem(FileSystem) are mutually exclusive.", iae.getMessage()));

        AbstractPolyglotTest.assertFails(() -> IOAccess.newBuilder().allowHostFileAccess(true).fileSystem(new MemoryFileSystem()).build(),
                        IllegalArgumentException.class,
                        (iae) -> assertEquals("The method IOAccess.Builder.allowHostFileAccess(boolean) and the method IOAccess.Builder.fileSystem(FileSystem) are mutually exclusive.",
                                        iae.getMessage()));
    }

    @Test
    public void testEquals() throws IOException {
        IOAccess sockets = IOAccess.newBuilder().allowHostSocketAccess(true).build();
        IOAccess files = IOAccess.newBuilder().allowHostFileAccess(true).build();
        IOAccess virtualFs = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        IOAccess virtualFsWithSockets = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).allowHostSocketAccess(true).build();
        assertEquals(IOAccess.ALL, IOAccess.ALL);
        assertEquals(IOAccess.ALL, IOAccess.newBuilder(IOAccess.ALL).build());
        assertEquals(IOAccess.ALL, IOAccess.newBuilder().allowHostSocketAccess(true).allowHostFileAccess(true).build());
        assertNotEquals(IOAccess.ALL, null);
        assertNotEquals(IOAccess.ALL, new Object());
        assertNotEquals(IOAccess.ALL, IOAccess.NONE);
        assertNotEquals(IOAccess.ALL, null);
        assertNotEquals(IOAccess.ALL, sockets);
        assertNotEquals(IOAccess.ALL, files);
        assertNotEquals(IOAccess.ALL, virtualFs);
        assertNotEquals(IOAccess.ALL, virtualFsWithSockets);
        assertEquals(IOAccess.NONE, IOAccess.NONE);
        assertEquals(IOAccess.NONE, IOAccess.newBuilder().build());
        assertEquals(IOAccess.NONE, IOAccess.newBuilder(IOAccess.NONE).build());
        assertNotEquals(IOAccess.NONE, null);
        assertNotEquals(IOAccess.NONE, new Object());
        assertNotEquals(IOAccess.NONE, IOAccess.ALL);
        assertNotEquals(IOAccess.NONE, sockets);
        assertNotEquals(IOAccess.NONE, files);
        assertNotEquals(IOAccess.NONE, virtualFs);
        assertNotEquals(IOAccess.NONE, virtualFsWithSockets);
        assertEquals(sockets, sockets);
        assertEquals(sockets, IOAccess.newBuilder(sockets).build());
        assertNotEquals(sockets, null);
        assertNotEquals(sockets, new Object());
        assertNotEquals(sockets, IOAccess.ALL);
        assertNotEquals(sockets, IOAccess.NONE);
        assertNotEquals(sockets, files);
        assertNotEquals(sockets, virtualFs);
        assertNotEquals(sockets, virtualFsWithSockets);
        assertEquals(files, files);
        assertEquals(files, IOAccess.newBuilder(files).build());
        assertNotEquals(files, null);
        assertNotEquals(files, new Object());
        assertNotEquals(files, IOAccess.ALL);
        assertNotEquals(files, IOAccess.NONE);
        assertNotEquals(files, sockets);
        assertNotEquals(files, virtualFs);
        assertNotEquals(files, virtualFsWithSockets);
        assertEquals(virtualFs, virtualFs);
        assertEquals(virtualFs, IOAccess.newBuilder(virtualFs).build());
        assertNotEquals(virtualFs, null);
        assertNotEquals(virtualFs, new Object());
        assertNotEquals(virtualFs, IOAccess.ALL);
        assertNotEquals(virtualFs, IOAccess.NONE);
        assertNotEquals(virtualFs, sockets);
        assertNotEquals(virtualFs, files);
        assertNotEquals(virtualFs, virtualFsWithSockets);
        assertEquals(virtualFsWithSockets, virtualFsWithSockets);
        assertEquals(virtualFsWithSockets, IOAccess.newBuilder(virtualFsWithSockets).build());
        assertNotEquals(virtualFsWithSockets, null);
        assertNotEquals(virtualFsWithSockets, new Object());
        assertNotEquals(virtualFsWithSockets, IOAccess.ALL);
        assertNotEquals(virtualFsWithSockets, IOAccess.NONE);
        assertNotEquals(virtualFsWithSockets, sockets);
        assertNotEquals(virtualFsWithSockets, files);
        assertNotEquals(virtualFsWithSockets, virtualFs);
    }

    @Test
    public void testHashCode() throws IOException {
        IOAccess sockets = IOAccess.newBuilder().allowHostSocketAccess(true).build();
        IOAccess files = IOAccess.newBuilder().allowHostFileAccess(true).build();
        IOAccess virtualFs = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        IOAccess virtualFsWithSockets = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).allowHostSocketAccess(true).build();
        assertEquals(IOAccess.ALL.hashCode(), IOAccess.ALL.hashCode());
        assertEquals(IOAccess.ALL.hashCode(), IOAccess.newBuilder(IOAccess.ALL).build().hashCode());
        assertEquals(IOAccess.ALL.hashCode(), IOAccess.newBuilder().allowHostSocketAccess(true).allowHostFileAccess(true).build().hashCode());
        assertNotEquals(IOAccess.ALL.hashCode(), IOAccess.NONE.hashCode());
        assertNotEquals(IOAccess.ALL.hashCode(), sockets.hashCode());
        assertNotEquals(IOAccess.ALL.hashCode(), files.hashCode());
        assertEquals(IOAccess.NONE.hashCode(), IOAccess.NONE.hashCode());
        assertEquals(IOAccess.NONE.hashCode(), IOAccess.newBuilder().build().hashCode());
        assertEquals(IOAccess.NONE.hashCode(), IOAccess.newBuilder(IOAccess.NONE).build().hashCode());
        assertNotEquals(IOAccess.NONE.hashCode(), IOAccess.ALL.hashCode());
        assertNotEquals(IOAccess.NONE.hashCode(), sockets.hashCode());
        assertNotEquals(IOAccess.NONE.hashCode(), files.hashCode());
        assertEquals(sockets.hashCode(), sockets.hashCode());
        assertEquals(sockets.hashCode(), IOAccess.newBuilder(sockets).build().hashCode());
        assertNotEquals(sockets.hashCode(), IOAccess.ALL.hashCode());
        assertNotEquals(sockets.hashCode(), IOAccess.NONE.hashCode());
        assertNotEquals(sockets.hashCode(), files.hashCode());
        assertEquals(files.hashCode(), files.hashCode());
        assertEquals(files.hashCode(), IOAccess.newBuilder(files).build().hashCode());
        assertNotEquals(files.hashCode(), IOAccess.ALL.hashCode());
        assertNotEquals(files.hashCode(), IOAccess.NONE.hashCode());
        assertNotEquals(files.hashCode(), sockets.hashCode());
        assertEquals(virtualFs.hashCode(), virtualFs.hashCode());
        assertEquals(virtualFs.hashCode(), IOAccess.newBuilder(virtualFs).build().hashCode());
        assertEquals(virtualFsWithSockets.hashCode(), virtualFsWithSockets.hashCode());
        assertEquals(virtualFsWithSockets.hashCode(), IOAccess.newBuilder(virtualFsWithSockets).build().hashCode());
    }

    @Test
    public void testToString() throws IOException {
        IOAccess sockets = IOAccess.newBuilder().allowHostSocketAccess(true).build();
        IOAccess files = IOAccess.newBuilder().allowHostFileAccess(true).build();
        IOAccess virtualFs = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        IOAccess virtualFsWithSockets = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).allowHostSocketAccess(true).build();
        assertEquals("IOAccess.ALL", IOAccess.ALL.toString());
        assertEquals("IOAccess.NONE", IOAccess.NONE.toString());
        assertEquals("IOAccess[allowHostFileAccess=false, allowHostSocketAccess=false, fileSystem=null]", IOAccess.newBuilder().build().toString());
        assertEquals("IOAccess[allowHostFileAccess=false, allowHostSocketAccess=false, fileSystem=null]", IOAccess.newBuilder(IOAccess.NONE).build().toString());
        assertEquals("IOAccess[allowHostFileAccess=true, allowHostSocketAccess=true, fileSystem=null]", IOAccess.newBuilder(IOAccess.ALL).build().toString());
        assertEquals("IOAccess[allowHostFileAccess=false, allowHostSocketAccess=true, fileSystem=null]", sockets.toString());
        assertEquals("IOAccess[allowHostFileAccess=true, allowHostSocketAccess=false, fileSystem=null]", files.toString());
        assertTrue(virtualFs.toString().startsWith("IOAccess[allowHostFileAccess=false, allowHostSocketAccess=false, fileSystem=com.oracle.truffle.api.test.polyglot.MemoryFileSystem"));
        assertTrue(virtualFsWithSockets.toString().startsWith("IOAccess[allowHostFileAccess=false, allowHostSocketAccess=true, fileSystem=com.oracle.truffle.api.test.polyglot.MemoryFileSystem"));
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
            assertTrue(env.isFileIOAllowed());
            return env.getPublicTruffleFile("test").exists();
        }
    }

    @TruffleLanguage.Registration
    public static final class TestFileAccessDisabledLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            assertFalse(env.isFileIOAllowed());
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

        @SuppressWarnings("deprecation")
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            assertTrue(env.isSocketIOAllowed());
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

        @SuppressWarnings("deprecation")
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            assertFalse(env.isSocketIOAllowed());
            URL resource = new URL("http://localhost:1234/test.js");
            AbstractPolyglotTest.assertFails(() -> Source.newBuilder("TestJS", resource).build(), SecurityException.class);
            return null;
        }
    }
}
