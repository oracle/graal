/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines how the host (editor) should sync document changes to the language server.
 */
public enum TextDocumentSyncKind {

    /**
     * Documents should not be synced at all.
     */
    None(0),
    /**
     * Documents are synced by always sending the full content of the document.
     */
    Full(1),
    /**
     * Documents are synced by sending the full content on open. After that only incremental updates
     * to the document are send.
     */
    Incremental(2);

    private final int intValue;

    TextDocumentSyncKind(int intValue) {
        this.intValue = intValue;
    }

    public int getIntValue() {
        return intValue;
    }

    private static final Map<Integer, TextDocumentSyncKind> lookup = new HashMap<>();

    static {
        for (TextDocumentSyncKind value : TextDocumentSyncKind.values()) {
            lookup.put(value.getIntValue(), value);
        }
    }

    public static TextDocumentSyncKind get(Integer intValue) {
        return lookup.get(intValue);
    }
}
