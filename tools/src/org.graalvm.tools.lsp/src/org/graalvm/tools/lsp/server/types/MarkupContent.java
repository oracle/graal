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
 * A `MarkupContent` literal represents a string value which content is interpreted base on its kind
 * flag. Currently the protocol supports `plaintext` and `markdown` as markup kinds.
 *
 * If the kind is `markdown` then the value can contain fenced code blocks like in GitHub issues.
 * See https://help.github.com/articles/creating-and-highlighting-code-blocks/#syntax-highlighting
 *
 * Here is an example how such a string can be constructed using JavaScript / TypeScript: ```ts let
 * markdown: MarkdownContent = { kind: MarkupKind.Markdown, value: [ '# Header', 'Some text',
 * '```typescript', 'someCode();', '```' ].join('\n') }; ```
 *
 * *Please Note* that clients might sanitize the return markdown. A client could decide to remove
 * HTML from the markdown to avoid script execution.
 */
public class MarkupContent extends JSONBase {

    MarkupContent(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The type of the Markup.
     */
    public MarkupKind getKind() {
        return MarkupKind.get(jsonData.getString("kind"));
    }

    public MarkupContent setKind(MarkupKind kind) {
        jsonData.put("kind", kind.getStringValue());
        return this;
    }

    /**
     * The content itself.
     */
    public String getValue() {
        return jsonData.getString("value");
    }

    public MarkupContent setValue(String value) {
        jsonData.put("value", value);
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
        MarkupContent other = (MarkupContent) obj;
        if (this.getKind() != other.getKind()) {
            return false;
        }
        if (!Objects.equals(this.getValue(), other.getValue())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 13 * hash + Objects.hashCode(this.getKind());
        hash = 13 * hash + Objects.hashCode(this.getValue());
        return hash;
    }

    public static MarkupContent create(MarkupKind kind, String value) {
        final JSONObject json = new JSONObject();
        json.put("kind", kind.getStringValue());
        json.put("value", value);
        return new MarkupContent(json);
    }
}
