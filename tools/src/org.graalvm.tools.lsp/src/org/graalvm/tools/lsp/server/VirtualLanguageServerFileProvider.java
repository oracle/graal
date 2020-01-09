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
package org.graalvm.tools.lsp.server;

import java.nio.file.Path;

/**
 * This service interface provides callbacks for a custom file system. As we execute source code to
 * collect run-time information, the executed code needs to access the state of (potentially)
 * unsaved files in the LSP source code editor instead of the state on disk. Therefore, a custom
 * file system needs to check for every file access if there is an edited version in the source code
 * editor.
 *
 */
public interface VirtualLanguageServerFileProvider {

    /**
     * @param path A path to a file in the file system.
     * @return The source code of the file as seen/edited by the user in the source code editor.
     */
    String getSourceText(Path path);

    /**
     * @param path A path to a file in the file system.
     * @return true if the file has been marked as "opened" in our LSP server.
     */
    boolean isVirtualFile(Path path);
}
