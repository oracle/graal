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
package org.graalvm.tools.api.lsp;

import java.net.URI;
import java.util.Map;

import com.oracle.truffle.api.source.Source;

/**
 * Provides access to the GraalLS from within {@link LSPExtension}s.
 */
public interface LSPServerAccessor {

    /**
     * Get a map of fileURIs to languageIds for all files currently open in the LSP client.
     */
    Map<URI, String> getOpenFileURI2LangId();

    /**
     * Instruct the GraalLS to send a custom notification to the LSP client.
     */
    void sendCustomNotification(String method, Object params);

    /**
     * Get the {@link Source} for a given {@link URI}. Returns {@code null} if no source was found.
     */
    Source getSource(URI uri);

}
