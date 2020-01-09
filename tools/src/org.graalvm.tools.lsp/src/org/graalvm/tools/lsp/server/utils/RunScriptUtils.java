/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.tools.lsp.exceptions.InvalidCoverageScriptURI;

public final class RunScriptUtils {

    private static final String RUN_SCRIPT_PATH = "RUN_SCRIPT_PATH:";

    private RunScriptUtils() {
        assert false;
    }

    public static URI extractScriptPath(TextDocumentSurrogate surrogate) throws InvalidCoverageScriptURI {
        String currentText = surrogate.getEditorText();
        String firstLine;
        try {
            firstLine = new BufferedReader(new StringReader(currentText)).readLine();
        } catch (IOException e1) {
            throw new IllegalStateException(e1);
        }
        int startIndex = firstLine != null ? firstLine.indexOf(RUN_SCRIPT_PATH) : -1;
        if (startIndex >= 0) {
            Path scriptPath;
            try {
                scriptPath = Paths.get(firstLine.substring(startIndex + RUN_SCRIPT_PATH.length()));
                if (!scriptPath.isAbsolute()) {
                    Path currentFile = Paths.get(surrogate.getUri());
                    scriptPath = currentFile.resolveSibling(scriptPath).normalize();
                }
            } catch (InvalidPathException e) {
                throw new InvalidCoverageScriptURI(e, startIndex + RUN_SCRIPT_PATH.length(), firstLine.length());
            }
            if (!Files.exists(scriptPath)) {
                throw new InvalidCoverageScriptURI(startIndex + RUN_SCRIPT_PATH.length(), "File not found: " + scriptPath.toString(), firstLine.length());
            }
            return scriptPath.toUri();
        }
        return null;
    }
}
