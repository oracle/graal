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

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.polyglot.FileSystemsTest.ForwardingFileSystem;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class GR39689Test extends AbstractPolyglotTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    public GR39689Test() {
        needsLanguageEnv = true;
    }

    @Before
    public void setUp() {
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new MockExistsFileSystem()).build();
        setupEnv(Context.newBuilder().allowIO(ioAccess).build());
    }

    @Test
    public void testGetCanonicalFileOfNoLongerExistingFile() {
        TruffleFile file = languageEnv.getPublicTruffleFile(OSUtils.isUnix() ? "/non-existent/a/b" : "C:/non-existent/a/b");
        Assert.assertTrue(file.exists());

        Source.newBuilder(ProxyLanguage.ID, file).content("irrelevant").build();
    }

    private static final class MockExistsFileSystem extends ForwardingFileSystem {
        MockExistsFileSystem() {
            super(FileSystem.newDefaultFileSystem());
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            if (modes.isEmpty()) { // exists
                return;
            }
            super.checkAccess(path, modes, linkOptions);
        }
    }
}
