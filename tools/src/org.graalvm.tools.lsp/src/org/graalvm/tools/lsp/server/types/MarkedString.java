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
 * MarkedString can be used to render human readable text. It is either a markdown string or a
 * code-block that provides a language and a code snippet. The language identifier is semantically
 * equal to the optional language identifier in fenced code blocks in GitHub issues. See
 * https://help.github.com/articles/creating-and-highlighting-code-blocks/#syntax-highlighting
 *
 * The pair of a language and a value is an equivalent to markdown: ```${language} ${value} ```
 *
 * Note that markdown strings will be sanitized - that means html will be escaped.
 *
 * @deprecated use MarkupContent instead.
 */
@Deprecated
public class MarkedString extends JSONBase {

    MarkedString(JSONObject jsonData) {
        super(jsonData);
    }

    public String getLanguage() {
        return jsonData.getString("language");
    }

    public MarkedString setLanguage(String language) {
        jsonData.put("language", language);
        return this;
    }

    public String getValue() {
        return jsonData.getString("value");
    }

    public MarkedString setValue(String value) {
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
        MarkedString other = (MarkedString) obj;
        if (!Objects.equals(this.getLanguage(), other.getLanguage())) {
            return false;
        }
        if (!Objects.equals(this.getValue(), other.getValue())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.getLanguage());
        hash = 71 * hash + Objects.hashCode(this.getValue());
        return hash;
    }

    public static MarkedString create(String language, String value) {
        final JSONObject json = new JSONObject();
        json.put("language", language);
        json.put("value", value);
        return new MarkedString(json);
    }
}
