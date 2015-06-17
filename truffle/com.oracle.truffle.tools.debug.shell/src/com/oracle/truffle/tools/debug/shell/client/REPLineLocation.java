/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.shell.client;

import java.io.*;

import com.oracle.truffle.api.source.*;
import com.oracle.truffle.tools.debug.shell.*;

final class REPLineLocation {

    private final Source source;
    private final int lineNumber;

    /**
     * Attempts to extract description of a source line from {@code arg[1]}, either
     * "<source name>:<n>" or just "<n>".
     */
    static REPLineLocation parse(REPLClientContext context, String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            throw new IllegalArgumentException("no location specified");
        }

        Source source = null;
        int lineNumber = -1;
        String lineNumberText = null;

        final String[] split = args[1].split(":");
        if (split.length == 1) {
            // Specification only has one part; it should be a line number
            lineNumberText = split[0];
            try {
                lineNumber = Integer.parseInt(lineNumberText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("no line number specified");
            }
            // If only line number specified, then there must be a selected file
            final Source selectedSource = context.getSelectedSource();
            if (selectedSource == null) {
                throw new IllegalArgumentException("no selected file set");
            }
            source = selectedSource;

        } else {
            final String fileName = split[0];
            lineNumberText = split[1];
            try {
                source = Source.fromFileName(fileName);
            } catch (IOException e1) {
                throw new IllegalArgumentException("Can't find file \"" + fileName + "\"");
            }
            try {
                lineNumber = Integer.parseInt(lineNumberText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid line number \"" + lineNumberText + "\"");
            }
            if (lineNumber <= 0) {
                throw new IllegalArgumentException("Invalid line number \"" + lineNumberText + "\"");
            }
        }

        return new REPLineLocation(source, lineNumber);
    }

    REPLineLocation(Source source, int lineNumber) {
        this.source = source;
        this.lineNumber = lineNumber;
    }

    public Source getSource() {
        return source;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Creates a message containing an "op" and a line location.
     *
     * @param op the operation to be performed on this location
     */
    public REPLMessage createMessage(String op) {
        final REPLMessage msg = new REPLMessage(REPLMessage.OP, op);
        msg.put(REPLMessage.SOURCE_NAME, source.getShortName());
        msg.put(REPLMessage.FILE_PATH, source.getPath());
        msg.put(REPLMessage.LINE_NUMBER, Integer.toString(lineNumber));
        return msg;
    }

}
