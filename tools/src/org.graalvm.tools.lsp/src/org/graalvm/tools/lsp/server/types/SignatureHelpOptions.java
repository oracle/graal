/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Signature help options.
 */
public class SignatureHelpOptions {

    final JSONObject jsonData;

    SignatureHelpOptions(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The characters that trigger signature help automatically.
     */
    public List<String> getTriggerCharacters() {
        final JSONArray json = jsonData.optJSONArray("triggerCharacters");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public SignatureHelpOptions setTriggerCharacters(List<String> triggerCharacters) {
        if (triggerCharacters != null) {
            final JSONArray json = new JSONArray();
            for (String string : triggerCharacters) {
                json.put(string);
            }
            jsonData.put("triggerCharacters", json);
        }
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
        SignatureHelpOptions other = (SignatureHelpOptions) obj;
        if (!Objects.equals(this.getTriggerCharacters(), other.getTriggerCharacters())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getTriggerCharacters() != null) {
            hash = 17 * hash + Objects.hashCode(this.getTriggerCharacters());
        }
        return hash;
    }

    public static SignatureHelpOptions create() {
        final JSONObject json = new JSONObject();
        return new SignatureHelpOptions(json);
    }
}
