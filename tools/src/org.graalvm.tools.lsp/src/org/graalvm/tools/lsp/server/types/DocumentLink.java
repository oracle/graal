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
 * A document link is a range in a text document that links to an internal or external resource,
 * like another text document or a web site.
 */
public class DocumentLink extends JSONBase {

    DocumentLink(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range this link applies to.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public DocumentLink setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The uri this link points to.
     */
    public String getTarget() {
        return jsonData.optString("target", null);
    }

    public DocumentLink setTarget(String target) {
        jsonData.putOpt("target", target);
        return this;
    }

    /**
     * The tooltip text when you hover over this link.
     *
     * If a tooltip is provided, is will be displayed in a string that includes instructions on how
     * to trigger the link, such as `{0} (ctrl + click)`. The specific instructions vary depending
     * on OS, user settings, and localization.
     *
     * @since 3.15.0
     */
    public String getTooltip() {
        return jsonData.optString("tooltip", null);
    }

    public DocumentLink setTooltip(String tooltip) {
        jsonData.putOpt("tooltip", tooltip);
        return this;
    }

    /**
     * A data entry field that is preserved on a document link between a DocumentLinkRequest and a
     * DocumentLinkResolveRequest.
     */
    public Object getData() {
        return jsonData.opt("data");
    }

    public DocumentLink setData(Object data) {
        jsonData.putOpt("data", data);
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
        DocumentLink other = (DocumentLink) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getTarget(), other.getTarget())) {
            return false;
        }
        if (!Objects.equals(this.getTooltip(), other.getTooltip())) {
            return false;
        }
        if (!Objects.equals(this.getData(), other.getData())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.getRange());
        if (this.getTarget() != null) {
            hash = 37 * hash + Objects.hashCode(this.getTarget());
        }
        if (this.getTooltip() != null) {
            hash = 37 * hash + Objects.hashCode(this.getTooltip());
        }
        if (this.getData() != null) {
            hash = 37 * hash + Objects.hashCode(this.getData());
        }
        return hash;
    }

    /**
     * Creates a new DocumentLink literal.
     */
    public static DocumentLink create(Range range, String target, Object data) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.putOpt("target", target);
        json.putOpt("data", data);
        return new DocumentLink(json);
    }
}
