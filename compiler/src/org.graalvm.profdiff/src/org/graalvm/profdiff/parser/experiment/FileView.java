/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.parser.experiment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.function.BiConsumer;

/**
 * A view into a file providing utility methods to read the file or to create a subview of a line.
 */
public interface FileView {
    /**
     * Creates a view of a file.
     *
     * @param file the file by which the view is backed
     * @return a view of a file
     */
    static FileView fromFile(File file) {
        return new FileView() {
            @Override
            public String getSymbolicPath() {
                return file.getAbsolutePath();
            }

            @Override
            public void forEachLine(BiConsumer<String, FileView> consumer) throws IOException {
                try (FileInputStream inputStream = new FileInputStream(file);
                                InputStreamReader streamReader = new InputStreamReader(inputStream);
                                BufferedReader bufferedReader = new BufferedReader(streamReader)) {
                    long position = 0;
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        long lineStartPosition = position;
                        // assume that line separators are '\n' on all platforms
                        position += line.length() + 1;
                        consumer.accept(line, FileView.fromFileLineAtPosition(file, lineStartPosition));
                    }
                }
            }

            @Override
            public String readFully() throws IOException {
                char[] arr = new char[1024];
                StringBuilder sb = new StringBuilder();
                try (FileReader reader = new FileReader(file)) {
                    int numChars;
                    while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                        sb.append(arr, 0, numChars);
                    }
                }
                return sb.toString();
            }
        };
    }

    /**
     * Creates a view of a line in a file, starting from the provided byte position until the end of
     * the line.
     *
     * @param file the file by which the view is backed
     * @param linePosition the byte position of the start
     * @return a view of a line in a file
     */
    private static FileView fromFileLineAtPosition(File file, long linePosition) {
        return new FileView() {
            @Override
            public String getSymbolicPath() {
                return file.getAbsolutePath();
            }

            @Override
            public void forEachLine(BiConsumer<String, FileView> consumer) throws IOException {
                consumer.accept(readFully(), this);
            }

            @Override
            public String readFully() throws IOException {
                try (FileInputStream inputStream = new FileInputStream(file); FileChannel fileChannel = inputStream.getChannel()) {
                    fileChannel.position(linePosition);
                    try (InputStreamReader streamReader = new InputStreamReader(inputStream); BufferedReader bufferedReader = new BufferedReader(streamReader)) {
                        return bufferedReader.readLine();
                    }
                }
            }
        };
    }

    /**
     * Gets a symbolic path of the file by which the view is backed. The path is "symbolic", because
     * it is meant to be used for error messages, rather than being opened for reading.
     *
     * @return a symbolic path of the file by which the view is backed
     */
    String getSymbolicPath();

    /**
     * Performs an action for each line in this file view. The consumer accepts the contents of the
     * line and a file view of the line.
     *
     * @param consumer the action to be performed for each line and the view of the line
     * @throws IOException failed to read the file
     */
    void forEachLine(BiConsumer<String, FileView> consumer) throws IOException;

    /**
     * Reads the file contents of the view.
     *
     * @return the file contents of the view
     * @throws IOException failed to read the file
     */
    String readFully() throws IOException;
}
