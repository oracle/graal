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

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * A document filter denotes a document by different properties like the
 * [language](#TextDocument.languageId), the [scheme](#Uri.scheme) of its resource, or a
 * glob-pattern that is applied to the [path](#TextDocument.fileName).
 *
 * Glob patterns can have the following syntax:
 * <ul>
 * <li>`*` to match one or more characters in a path segment</li>
 * <li>`?` to match on one character in a path segment</li>
 * <li>`**` to match any number of path segments, including none</li>
 * <li>`{}` to group conditions (e.g. `**&#47;*.{ts,js}` matches all TypeScript and JavaScript
 * files)</li>
 * <li>`[]` to declare a range of characters to match in a path segment (e.g., `example.[0-9]` to
 * match on `example.0`, `example.1`, ...)</li>
 * <li>`[!...]` to negate a range of characters to match in a path segment (e.g., `example.[!0-9]`
 * to match on `example.a`, `example.b`, but not `example.0`)</li>
 * </ul>
 *
 * Samples:
 * <ul>
 * <li>A language filter that applies to typescript files on disk: `{ language: 'typescript',
 * scheme: 'file' }`</li>
 * <li>A language filter that applies to all package.json paths: `{ language: 'json', pattern:
 * '**package.json' }`</li>
 * </ul>
 */
public class DocumentFilter extends JSONBase {

    DocumentFilter(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * A language id, like `typescript`.
     */
    public String getLanguage() {
        return jsonData.optString("language", null);
    }

    public DocumentFilter setLanguage(String language) {
        jsonData.putOpt("language", language);
        return this;
    }

    /**
     * A Uri [scheme](#Uri.scheme), like `file` or `untitled`.
     */
    public String getScheme() {
        return jsonData.optString("scheme", null);
    }

    public DocumentFilter setScheme(String scheme) {
        jsonData.putOpt("scheme", scheme);
        return this;
    }

    /**
     * A glob pattern, like `*.{ts,js}`.
     */
    public String getPattern() {
        return jsonData.optString("pattern", null);
    }

    public DocumentFilter setPattern(String pattern) {
        jsonData.putOpt("pattern", pattern);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        DocumentFilter other = (DocumentFilter) obj;
        if (!Objects.equals(this.getLanguage(), other.getLanguage())) {
            return false;
        }
        if (!Objects.equals(this.getScheme(), other.getScheme())) {
            return false;
        }
        if (!Objects.equals(this.getPattern(), other.getPattern())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.getLanguage());
        if (this.getScheme() != null) {
            hash = 89 * hash + Objects.hashCode(this.getScheme());
        }
        if (this.getPattern() != null) {
            hash = 89 * hash + Objects.hashCode(this.getPattern());
        }
        return hash;
    }

    public static DocumentFilter create() {
        final JSONObject json = new JSONObject();
        return new DocumentFilter(json);
    }
}
