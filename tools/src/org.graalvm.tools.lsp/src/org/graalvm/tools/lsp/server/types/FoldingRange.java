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
 * Represents a folding range.
 */
public class FoldingRange extends JSONBase {

    FoldingRange(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The zero-based line number from where the folded range starts.
     */
    public int getStartLine() {
        return jsonData.getInt("startLine");
    }

    public FoldingRange setStartLine(int startLine) {
        jsonData.put("startLine", startLine);
        return this;
    }

    /**
     * The zero-based character offset from where the folded range starts. If not defined, defaults
     * to the length of the start line.
     */
    public Integer getStartCharacter() {
        return jsonData.has("startCharacter") ? jsonData.getInt("startCharacter") : null;
    }

    public FoldingRange setStartCharacter(Integer startCharacter) {
        jsonData.putOpt("startCharacter", startCharacter);
        return this;
    }

    /**
     * The zero-based line number where the folded range ends.
     */
    public int getEndLine() {
        return jsonData.getInt("endLine");
    }

    public FoldingRange setEndLine(int endLine) {
        jsonData.put("endLine", endLine);
        return this;
    }

    /**
     * The zero-based character offset before the folded range ends. If not defined, defaults to the
     * length of the end line.
     */
    public Integer getEndCharacter() {
        return jsonData.has("endCharacter") ? jsonData.getInt("endCharacter") : null;
    }

    public FoldingRange setEndCharacter(Integer endCharacter) {
        jsonData.putOpt("endCharacter", endCharacter);
        return this;
    }

    /**
     * Describes the kind of the folding range such as `comment' or 'region'. The kind is used to
     * categorize folding ranges and used by commands like 'Fold all comments'. See
     * [FoldingRangeKind](#FoldingRangeKind) for an enumeration of standardized kinds.
     */
    public String getKind() {
        return jsonData.optString("kind", null);
    }

    public FoldingRange setKind(String kind) {
        jsonData.putOpt("kind", kind);
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
        FoldingRange other = (FoldingRange) obj;
        if (this.getStartLine() != other.getStartLine()) {
            return false;
        }
        if (!Objects.equals(this.getStartCharacter(), other.getStartCharacter())) {
            return false;
        }
        if (this.getEndLine() != other.getEndLine()) {
            return false;
        }
        if (!Objects.equals(this.getEndCharacter(), other.getEndCharacter())) {
            return false;
        }
        if (!Objects.equals(this.getKind(), other.getKind())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 43 * hash + Integer.hashCode(this.getStartLine());
        if (this.getStartCharacter() != null) {
            hash = 43 * hash + Integer.hashCode(this.getStartCharacter());
        }
        hash = 43 * hash + Integer.hashCode(this.getEndLine());
        if (this.getEndCharacter() != null) {
            hash = 43 * hash + Integer.hashCode(this.getEndCharacter());
        }
        if (this.getKind() != null) {
            hash = 43 * hash + Objects.hashCode(this.getKind());
        }
        return hash;
    }

    /**
     * Creates a new FoldingRange literal.
     */
    public static FoldingRange create(int startLine, int endLine, Integer startCharacter, Integer endCharacter, String kind) {
        final JSONObject json = new JSONObject();
        json.put("startLine", startLine);
        if (startCharacter != null) {
            json.put("startCharacter", startCharacter);
        }
        json.put("endLine", endLine);
        if (endCharacter != null) {
            json.put("endCharacter", endCharacter);
        }
        json.putOpt("kind", kind);
        return new FoldingRange(json);
    }
}
