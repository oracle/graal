/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Copies file(s) while removing the license. The content of the copied file starts after the first
 * blank line that ought to end the license. This allows to have the tests immune to license
 * changes.
 */
public class CopyStripLicense {

    /**
     * @param args Two or more arguments are required: source file(s) and destination file or folder
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Insufficient number of arguments.");
            return;
        }
        int n = args.length - 1;
        File destFileOrFolder = new File(args[n]);
        boolean toFolder = destFileOrFolder.isDirectory();
        if (args.length > 2 && !toFolder) {
            System.err.println("More than one file to copy, but destination is not a folder.");
            return;
        }
        for (int i = 0; i < n; i++) {
            File sourceFile = new File(args[i]);
            File destFile = (toFolder) ? new File(destFileOrFolder, sourceFile.getName()) : destFileOrFolder;
            try (BufferedWriter w = new BufferedWriter(new FileWriter(destFile))) {
                try (BufferedReader r = new BufferedReader(new FileReader(sourceFile))) {
                    boolean licenseEnded = false;
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (!licenseEnded) {
                            if (line.isBlank()) {
                                licenseEnded = true;
                            }
                        } else {
                            w.write(line);
                            w.newLine();
                        }
                    }
                }
            }
        }
    }
}
