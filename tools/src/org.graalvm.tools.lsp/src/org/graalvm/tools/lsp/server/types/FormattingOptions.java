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
 * Value-object describing what options formatting should use.
 */
public class FormattingOptions extends JSONBase {

    FormattingOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Size of a tab in spaces.
     */
    public int getTabSize() {
        return jsonData.getInt("tabSize");
    }

    public FormattingOptions setTabSize(int tabSize) {
        jsonData.put("tabSize", tabSize);
        return this;
    }

    /**
     * Prefer spaces over tabs.
     */
    public boolean isInsertSpaces() {
        return jsonData.getBoolean("insertSpaces");
    }

    public FormattingOptions setInsertSpaces(boolean insertSpaces) {
        jsonData.put("insertSpaces", insertSpaces);
        return this;
    }

    /**
     * Trim trailing whitespaces on a line.
     *
     * @since 3.15.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getTrimTrailingWhitespace() {
        return jsonData.has("trimTrailingWhitespace") ? jsonData.getBoolean("trimTrailingWhitespace") : null;
    }

    public FormattingOptions setTrimTrailingWhitespace(Boolean trimTrailingWhitespace) {
        jsonData.putOpt("trimTrailingWhitespace", trimTrailingWhitespace);
        return this;
    }

    /**
     * Insert a newline character at the end of the file if one does not exist.
     *
     * @since 3.15.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getInsertFinalNewline() {
        return jsonData.has("insertFinalNewline") ? jsonData.getBoolean("insertFinalNewline") : null;
    }

    public FormattingOptions setInsertFinalNewline(Boolean insertFinalNewline) {
        jsonData.putOpt("insertFinalNewline", insertFinalNewline);
        return this;
    }

    /**
     * Trim all newlines after the final newline at the end of the file.
     *
     * @since 3.15.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getTrimFinalNewlines() {
        return jsonData.has("trimFinalNewlines") ? jsonData.getBoolean("trimFinalNewlines") : null;
    }

    public FormattingOptions setTrimFinalNewlines(Boolean trimFinalNewlines) {
        jsonData.putOpt("trimFinalNewlines", trimFinalNewlines);
        return this;
    }

    /**
     * Signature for further properties.
     */
    public Object get(String key) {
        return jsonData.get(key);
    }

    public FormattingOptions set(String key, Object value) {
        jsonData.put(key, value);
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
        FormattingOptions other = (FormattingOptions) obj;
        if (this.getTabSize() != other.getTabSize()) {
            return false;
        }
        if (this.isInsertSpaces() != other.isInsertSpaces()) {
            return false;
        }
        if (!Objects.equals(this.getTrimTrailingWhitespace(), other.getTrimTrailingWhitespace())) {
            return false;
        }
        if (!Objects.equals(this.getInsertFinalNewline(), other.getInsertFinalNewline())) {
            return false;
        }
        if (!Objects.equals(this.getTrimFinalNewlines(), other.getTrimFinalNewlines())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Integer.hashCode(this.getTabSize());
        hash = 83 * hash + Boolean.hashCode(this.isInsertSpaces());
        if (this.getTrimTrailingWhitespace() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getTrimTrailingWhitespace());
        }
        if (this.getInsertFinalNewline() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getInsertFinalNewline());
        }
        if (this.getTrimFinalNewlines() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getTrimFinalNewlines());
        }
        return hash;
    }

    /**
     * Creates a new FormattingOptions literal.
     */
    public static FormattingOptions create(int tabSize, boolean insertSpaces) {
        final JSONObject json = new JSONObject();
        json.put("tabSize", tabSize);
        json.put("insertSpaces", insertSpaces);
        return new FormattingOptions(json);
    }
}
