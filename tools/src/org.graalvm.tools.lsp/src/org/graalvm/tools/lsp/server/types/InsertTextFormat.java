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
 * Defines whether the insert text in a completion item should be interpreted as plain text or a
 * snippet.
 */
public enum InsertTextFormat {

    /**
     * The primary text to be inserted is treated as a plain string.
     */
    PlainText(1),
    /**
     * The primary text to be inserted is treated as a snippet.
     *
     * A snippet can define tab stops and placeholders with `$1`, `$2` and `${3:foo}`. `$0` defines
     * the final tab stop, it defaults to the end of the snippet. Placeholders with equal
     * identifiers are linked, that is typing in one will update others too.
     *
     * @see <a href=
     *      "https://github.com/Microsoft/vscode/blob/master/src/vs/editor/contrib/snippet/common/snippet.md">
     *      https://github.com/Microsoft/vscode/blob/master/src/vs/editor/contrib/snippet/common/
     *      snippet.md</a>
     */
    Snippet(2);

    private final int intValue;

    InsertTextFormat(int intValue) {
        this.intValue = intValue;
    }

    public int getIntValue() {
        return intValue;
    }

    private static final Map<Integer, InsertTextFormat> lookup = new HashMap<>();

    static {
        for (InsertTextFormat value : InsertTextFormat.values()) {
            lookup.put(value.getIntValue(), value);
        }
    }

    public static InsertTextFormat get(Integer intValue) {
        return lookup.get(intValue);
    }
}
