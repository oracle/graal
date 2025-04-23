/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;

public class FileDumpingUtil {
    public static Path createTempFile(Path directory, String name, String suffix) {
        try {
            long start = System.nanoTime();
            String filePrefix = String.format(name + "-%d", start);
            return Files.createTempFile(directory, filePrefix, suffix);
        } catch (Exception e) {
            throw GraalError.shouldNotReachHere(e, "Error during temporary file creation.");
        }
    }

    @SuppressFBWarnings(value = "", justification = "FB reports null pointer dereferencing although it is not possible in this case.")
    public static void dumpFile(Path path, String name, String suffix, Consumer<OutputStream> streamConsumer) {
        Path tempPath = createTempFile(path.getParent(), name, suffix);
        try {
            try (FileOutputStream fileOutputStream = new FileOutputStream(tempPath.toFile())) {
                streamConsumer.accept(fileOutputStream);
            }
            moveTryAtomically(tempPath, path);
        } catch (Exception e) {
            throw GraalError.shouldNotReachHere(e, "Error during file dumping.");
        }
    }

    public static void moveTryAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LogUtils.warning("Could not move temporary file (" + source.toAbsolutePath() + ") to (" + target.toAbsolutePath() + ") atomically. " +
                            "This might result in inconsistencies while reading the file.");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
