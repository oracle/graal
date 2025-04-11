/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted.webimage.test.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;

import org.junit.rules.ExternalResource;

import jdk.graal.compiler.debug.GraalError;

/**
 * Manages the life-cycle of the test directory containing all test artifacts (compiled JS code,
 * support files, etc.).
 *
 * By default, will create a temporary directory and delete it once done. If the system property
 * 'webimage.test.dir' is set, that directory is used and deleted at the end.
 *
 * The contents of this folder are preserved across an entire test suite ({@link JTTTestSuite}) and
 * deleted afterwards.
 *
 * Generated files relevant for the test execution should be placed here because they will be dumped
 * in case of failure and can help with debugging.
 */
public class TestDirectory extends ExternalResource {
    private Path dir = null;

    /**
     * Stores the path + base64 encoded SHA256 hash for each file that was dumped the last time
     *
     * If a new dump is created, we can skip it if all files are the same.
     */
    private HashMap<Path, String> dumpedFiles = null;

    /**
     * Collects and hashes all files in the test directory.
     */
    private HashMap<Path, String> collectTestFiles() throws IOException {
        HashMap<Path, String> map = new HashMap<>();

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw GraalError.shouldNotReachHere(e);
        }

        Files.walk(dir).filter(path -> !Files.isDirectory(path)).forEach(path -> {
            try {
                md.reset();
                md.update(Files.readAllBytes(path));
                String encoded = Base64.getEncoder().encodeToString(md.digest());
                map.put(path, encoded);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return map;
    }

    /**
     * Collects a collection of paths that should be included in the next dump.
     *
     * @return A collection of paths. null, if the files haven't changed since the last dump.
     */
    public Collection<Path> getFilesToDump() throws IOException {
        HashMap<Path, String> files = collectTestFiles();

        if (files.equals(dumpedFiles)) {
            return null;
        }

        dumpedFiles = files;

        return files.keySet();
    }

    @Override
    public void before() throws Throwable {
        String testDir = WebImageTestOptions.TEST_DIRECTORY;
        if (testDir == null) {
            dir = Files.createTempDirectory("webimage-test");
        } else {
            dir = Paths.get(testDir);
            // Clean folder contents since we can't guarantee it is empty
            WebImageTestUtil.clearDirectory(dir);
        }
    }

    @Override
    protected void after() {
        super.after();
        if (WebImageTestOptions.CLEANUP && dir != null) {
            WebImageTestUtil.deleteRecursive(dir);
        }
    }

    public Path getDirectory() {
        assert dir != null;
        return dir;
    }
}
