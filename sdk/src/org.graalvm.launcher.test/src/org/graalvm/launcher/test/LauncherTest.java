/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher.test;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.launcher.Launcher;
import org.junit.Before;
import org.junit.Test;

public class LauncherTest {

    private Path tmpDir;
    private Method newLogStreamMethod;

    @Before
    public void setUp() throws IOException, ReflectiveOperationException {
        tmpDir = Files.createTempDirectory(LauncherTest.class.getName()).toAbsolutePath();
        Class<Launcher> launcherClass = Launcher.class;
        newLogStreamMethod = launcherClass.getDeclaredMethod("newLogStream", Path.class);
        newLogStreamMethod.setAccessible(true);
    }

    @Test
    public void testLogFileNames() throws Exception {
        Path testLogName = tmpDir.resolve("test.log");
        Path testLog1Name = tmpDir.resolve("test.log1");
        Path testLogLockName = tmpDir.resolve("test.log.lck");
        Path testLog1LockName = tmpDir.resolve("test.log1.lck");
        assertFalse(exists(testLogName));
        assertFalse(exists(testLog1Name));
        assertFalse(exists(testLogLockName));
        assertFalse(exists(testLog1LockName));

        int writeCounter = 0;
        ByteArrayOutputStream outerExpectedContent = new ByteArrayOutputStream();
        // Log file: test.log should be created
        try (OutputStream out = new ProxyOutputStream(newLogStream(testLogName), outerExpectedContent)) {
            assertTrue(exists(testLogName));
            assertTrue(exists(testLogLockName));
            assertFalse(exists(testLog1Name));
            assertFalse(exists(testLog1LockName));
            out.write(++writeCounter);
        }
        assertTrue(exists(testLogName));
        assertFalse(exists(testLogLockName));
        assertArrayEquals(outerExpectedContent.toByteArray(), readAllBytes(testLogName));

        // Log file: test.log should be reused
        try (OutputStream out = new ProxyOutputStream(newLogStream(testLogName), outerExpectedContent)) {
            assertTrue(exists(testLogName));
            assertTrue(exists(testLogLockName));
            assertFalse(exists(testLog1Name));
            assertFalse(exists(testLog1LockName));
            out.write(++writeCounter);
        }
        assertTrue(exists(testLogName));
        assertFalse(exists(testLogLockName));
        assertArrayEquals(outerExpectedContent.toByteArray(), readAllBytes(testLogName));

        // Log file: test.log should be reused
        // Log file: test.log1 should be created for inner (overlapping) execution
        ByteArrayOutputStream innerExpectedContent = new ByteArrayOutputStream();
        try (OutputStream out = new ProxyOutputStream(newLogStream(testLogName), outerExpectedContent)) {
            try (OutputStream outInner = new ProxyOutputStream(newLogStream(testLogName), innerExpectedContent)) {
                assertTrue(exists(testLogName));
                assertTrue(exists(testLogLockName));
                assertTrue(exists(testLog1Name));
                assertTrue(exists(testLog1LockName));
                outInner.write(++writeCounter);
            }
            assertTrue(exists(testLogName));
            assertTrue(exists(testLogLockName));
            assertTrue(exists(testLog1Name));
            assertFalse(exists(testLog1LockName));
            out.write(++writeCounter);
        }
        assertTrue(exists(testLogName));
        assertFalse(exists(testLogLockName));
        assertTrue(exists(testLog1Name));
        assertFalse(exists(testLog1LockName));
        assertArrayEquals(outerExpectedContent.toByteArray(), readAllBytes(testLogName));
        assertArrayEquals(innerExpectedContent.toByteArray(), readAllBytes(testLog1Name));
    }

    private OutputStream newLogStream(Path logFile) throws ReflectiveOperationException {
        return (OutputStream) newLogStreamMethod.invoke(null, logFile);
    }

    private static final class ProxyOutputStream extends OutputStream {
        private OutputStream[] delegates;

        ProxyOutputStream(OutputStream... delegates) {
            this.delegates = delegates;
        }

        @Override
        public void write(int b) throws IOException {
            for (OutputStream out : delegates) {
                out.write(b);
            }
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            for (OutputStream out : delegates) {
                try {
                    out.close();
                } catch (IOException ioe) {
                    if (exception == null) {
                        exception = ioe;
                    } else {
                        exception.addSuppressed(ioe);
                    }
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }
}
