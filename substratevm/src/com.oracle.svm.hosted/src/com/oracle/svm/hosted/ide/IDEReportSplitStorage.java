/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes an IDE report envelope to the side file associated with an image. */
public final class IDEReportSplitStorage {
    private static final String FILE_SUFFIX = ".ide-report";

    private IDEReportSplitStorage() {
    }

    public static Path write(Path imagePath, byte[] envelope) throws IOException {
        Path splitPath = splitPath(imagePath);
        Path parent = splitPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(splitPath, envelope);
        return splitPath;
    }

    public static Path splitPath(Path imagePath) {
        Path imageFileName = imagePath.getFileName();
        if (imageFileName == null) {
            throw new IllegalArgumentException("IDE report split storage requires an image file path");
        }
        return imagePath.resolveSibling(imageFileName + FILE_SUFFIX);
    }
}
