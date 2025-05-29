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

package com.oracle.svm.hosted.webimage.test.unit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.svm.core.OS;
import com.oracle.svm.webimage.substitute.system.WebImageFileSystem;

/**
 * Tests the implementation of {@link WebImageFileSystem#canonicalizePath(String)} to make sure it
 * matches the implementation of {@link File#getCanonicalPath()}.
 */
@RunWith(Parameterized.class)
public class CanonicalizePathTest {

    @ClassRule public static final TemporaryFolder TEMP = new TemporaryFolder();

    /**
     * Makes sure the test only runs on linux, because that's the semantics we implemented, and sets
     * up some files and folders for testing.
     * <p>
     * Sets up the following directory structure under the {@link #TEMP} folder (symlinks are marked
     * with arrows):
     *
     * <pre>
     *     TEMP/
     *     |- dir1
     *     |- dir2 -> dir1
     *     |- dir3 -> exists/not
     *     |- dir4
     *        |- file2
     *        |- file3 -> ../file1
     *        |- dir6
     *           |- dir7 -> ../../dir5
     *     |- dir5
     *        |- dir8
     *           |- file4
     *        |- dir9
     *        |- dir10 -> ../dir4/dir6/dir7/dir9
     *     |- file1
     * </pre>
     */
    @BeforeClass
    public static void setup() throws IOException {
        Assume.assumeTrue(OS.LINUX.isCurrent());

        // Create folders
        TEMP.newFolder("dir1");
        TEMP.newFolder("dir4", "dir6");
        TEMP.newFolder("dir5", "dir8");
        TEMP.newFolder("dir5", "dir9");

        // Create files
        TEMP.newFile("file1");
        TEMP.newFile("dir4/file2");
        TEMP.newFile("dir5/dir8/file4");

        // Create symlinks
        Files.createSymbolicLink(getTempPath("dir2"), Path.of("dir1"));
        Files.createSymbolicLink(getTempPath("dir3"), Path.of("exists/not"));
        Files.createSymbolicLink(getTempPath("dir4/file3"), Path.of("../file1"));
        Files.createSymbolicLink(getTempPath("dir4/dir6/dir7"), Path.of("../../dir5"));
        Files.createSymbolicLink(getTempPath("dir5/dir10"), Path.of("../dir4/dir6/dir7/dir9"));
    }

    private static Path getTempPath(String p) {
        return TEMP.getRoot().toPath().resolve(p);
    }

    private static Path getPath(String p) {
        return Path.of(p);
    }

    /**
     * Each testcase has two values: the path to test and the expected canonicalized path.
     * <p>
     * Has to use a supplier to defer construction of paths because {@link #TEMP} has not created
     * the folder yet when this method runs.
     */
    @Parameterized.Parameters()
    public static Iterable<Supplier<Path[]>> data() {
        return List.of(
                        // Absolute paths that exist
                        () -> new Path[]{getPath("/"), getPath("/")},
                        () -> new Path[]{getTempPath("dir1"), getTempPath("dir1")},
                        () -> new Path[]{getTempPath("dir2"), getTempPath("dir1")},
                        () -> new Path[]{getTempPath("dir4/dir6/dir7"), getTempPath("dir5")},
                        () -> new Path[]{getTempPath("dir5/dir10"), getTempPath("dir5/dir9")},
                        () -> new Path[]{getTempPath("dir4/dir6/dir7/../dir4/file3"), getTempPath("file1")},

                        // Absolute paths that don't exist
                        () -> new Path[]{getPath("/does/../not/exist"), getPath("/not/exist")},
                        () -> new Path[]{getTempPath("dir3/../some/folder"), getTempPath("some/folder")},
                        () -> new Path[]{getTempPath("dir4/dir6/dir7/../dir1/some/folder"), getTempPath("dir1/some/folder")},

                        /*
                         * Relative paths that don't exist. We can't really test relative paths that
                         * do exist because we do not want to pollute the user's CWD.
                         */
                        () -> new Path[]{getPath("does/../not/exist"), getPath("not/exist")},

                        // Dangling symlinks are not resolved
                        () -> new Path[]{getTempPath("dir3"), getTempPath("dir3")},
                        () -> new Path[]{getTempPath("dir4/file3"), getTempPath("file1")});
    }

    @Parameterized.Parameter public Supplier<Path[]> supplier;

    @Test
    public void test() throws IOException {
        Path[] paths = supplier.get();
        Assert.assertEquals(2, paths.length);

        Path path = paths[0];
        Path expected = paths[1];

        String fromJvm = path.toFile().getCanonicalPath();
        String actual = WebImageFileSystem.canonicalizePath(path.toFile().getAbsolutePath()).toString();

        Assert.assertEquals("Our expected value does not match JVM implementation", expected.toAbsolutePath().toString(), fromJvm);
        Assert.assertEquals(fromJvm, actual);
    }
}
