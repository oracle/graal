/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.*;

/**
 * Represents a unit (typically a file) of guest language source code.
 */
public interface Source {

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file.
     *
     * @return the name of the guest language program
     */
    String getName();

    /**
     * Returns a short version of the name of the resource holding a guest language program (as
     * described in @getName). For example, this could be just the name of the file, rather than a
     * full path.
     *
     * @return the short name of the guest language program
     */
    String getShortName();

    /**
     * The normalized, canonical name of the file.
     */
    String getPath();

    /**
     * Access to the source contents.
     */
    Reader getReader();

    /**
     * Access to the source contents.
     */
    InputStream getInputStream();

    /**
     * Return the complete text of the code.
     */
    String getCode();

    /**
     * Given a 1-based line number, return the text in the line, not including a possible
     * terminating newline.
     */
    String getCode(int lineNumber);

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line.
     */
    int getLineCount();

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position.
     */
    int getLineNumber(int offset);

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     */
    int getLineStartOffset(int lineNumber);

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line.
     */
    int getLineLength(int lineNumber);

}
