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

/**
 * A readable source is a character data source that provides a Reader to read the data.
 */
public interface ReadableSource {

    /**
     * @param buffered if true, the returned reader is guaranteed to be a BufferedReader
     * 
     * @return a reader to read the character data represented by this source
     */
    Reader reader(boolean buffered) throws IOException;

    public static final class Static {

        private Static() {

        }

        /**
         * Creates a ReadableSource to provides readers for the characters in a string.
         */
        public static ReadableSource fromString(final String s) {
            return new ReadableSource() {
                public Reader reader(boolean buffered) throws IOException {
                    return buffered ? new BufferedReader(new StringReader(s)) : new StringReader(s);
                }
            };
        }

        /**
         * Creates a ReadableSource to provides readers for the characters in a file.
         */
        public static ReadableSource fromFile(final File file) {
            return new ReadableSource() {
                public Reader reader(boolean buffered) throws IOException {
                    return buffered ? new BufferedReader(new FileReader(file)) : new FileReader(file);
                }
            };

        }
    }
}
