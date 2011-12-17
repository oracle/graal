/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.io;

import java.io.*;

import com.sun.max.program.*;

public final class TemporaryFiles {
    private TemporaryFiles() {
    }

    public static void cleanup(final String prefix, final String suffix) {
        if ((prefix == null || prefix.length() == 0) && (suffix == null || suffix.length() == 0)) {
            return;
        }
        try {
            final File tempFile = File.createTempFile(prefix, suffix);
            final File directory = tempFile.getParentFile();
            final FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    if (prefix != null && prefix.length() > 0 && !name.startsWith(prefix)) {
                        return false;
                    }
                    if (suffix != null && suffix.length() > 0 && !name.endsWith(suffix)) {
                        return false;
                    }
                    return true;
                }
            };
            for (File file : directory.listFiles(filter)) {
                if (!file.delete()) {
                    ProgramWarning.message("could not delete temporary file: " + file.getAbsolutePath());
                }
            }
        } catch (IOException ioException) {
            ProgramWarning.message("could not delete temporary files");
        }
    }

    public static void cleanup(String prefix) {
        cleanup(prefix, null);
    }
}
