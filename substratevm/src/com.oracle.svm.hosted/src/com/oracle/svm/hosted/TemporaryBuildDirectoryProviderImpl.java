/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;
import com.oracle.svm.core.util.VMError;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TemporaryBuildDirectoryProviderImpl implements TemporaryBuildDirectoryProvider {

    private Path tempDirectory;
    private boolean deleteTempDirectory;

    @Override
    public synchronized Path getTemporaryBuildDirectory() {
        if (tempDirectory == null) {
            try {
                String tempName = NativeImageOptions.TempDirectory.getValue();
                if (tempName == null || tempName.isEmpty()) {
                    tempDirectory = Files.createTempDirectory("SVM-");
                    deleteTempDirectory = true;
                } else {
                    tempDirectory = FileSystems.getDefault().getPath(tempName).resolve("SVM-" + System.currentTimeMillis());
                    assert !Files.exists(tempDirectory);
                    Files.createDirectories(tempDirectory);
                }
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        return tempDirectory.toAbsolutePath();
    }

    private static void deleteAll(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    void clean() {
        if (deleteTempDirectory) {
            deleteAll(getTemporaryBuildDirectory());
        }
    }

}
