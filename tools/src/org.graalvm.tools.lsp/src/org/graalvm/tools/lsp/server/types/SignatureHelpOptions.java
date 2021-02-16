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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Server Capabilities for a [SignatureHelpRequest](#SignatureHelpRequest).
 */
public class SignatureHelpOptions extends WorkDoneProgressOptions {

    SignatureHelpOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * List of characters that trigger signature help.
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

    /**
     * List of characters that re-trigger signature help.
     *
     * These trigger characters are only active when signature help is already showing. All trigger
     * characters are also counted as re-trigger characters.
     *
     * @since 3.15.0
     */
    public List<String> getRetriggerCharacters() {
        final JSONArray json = jsonData.optJSONArray("retriggerCharacters");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public SignatureHelpOptions setRetriggerCharacters(List<String> retriggerCharacters) {
        if (retriggerCharacters != null) {
            final JSONArray json = new JSONArray();
            for (String string : retriggerCharacters) {
                json.put(string);
            }
            jsonData.put("retriggerCharacters", json);
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
        if (!Objects.equals(this.getRetriggerCharacters(), other.getRetriggerCharacters())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneProgress(), other.getWorkDoneProgress())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getTriggerCharacters() != null) {
            hash = 17 * hash + Objects.hashCode(this.getTriggerCharacters());
        }
        if (this.getRetriggerCharacters() != null) {
            hash = 17 * hash + Objects.hashCode(this.getRetriggerCharacters());
        }
        if (this.getWorkDoneProgress() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getWorkDoneProgress());
        }
        return hash;
    }

    public static SignatureHelpOptions create() {
        final JSONObject json = new JSONObject();
        return new SignatureHelpOptions(json);
    }
}
