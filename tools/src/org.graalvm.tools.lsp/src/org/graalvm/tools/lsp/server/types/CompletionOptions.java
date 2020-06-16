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
 * Completion options.
 */
public class CompletionOptions extends WorkDoneProgressOptions {

    CompletionOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Most tools trigger completion request automatically without explicitly requesting it using a
     * keyboard shortcut (e.g. Ctrl+Space). Typically they do so when the user starts to type an
     * identifier. For example if the user types `c` in a JavaScript file code complete will
     * automatically pop up present `console` besides others as a completion item. Characters that
     * make up identifiers don't need to be listed here.
     *
     * If code complete should automatically be trigger on characters not being valid inside an
     * identifier (for example `.` in JavaScript) list them in `triggerCharacters`.
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

    public CompletionOptions setTriggerCharacters(List<String> triggerCharacters) {
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
     * The list of all possible characters that commit a completion. This field can be used if
     * clients don't support individual commmit characters per completion item. See
     * `ClientCapabilities.textDocument.completion.completionItem.commitCharactersSupport`
     *
     * If a server provides both `allCommitCharacters` and commit characters on an individual
     * completion item the ones on the completion item win.
     *
     * @since 3.2.0
     */
    public List<String> getAllCommitCharacters() {
        final JSONArray json = jsonData.optJSONArray("allCommitCharacters");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public CompletionOptions setAllCommitCharacters(List<String> allCommitCharacters) {
        if (allCommitCharacters != null) {
            final JSONArray json = new JSONArray();
            for (String string : allCommitCharacters) {
                json.put(string);
            }
            jsonData.put("allCommitCharacters", json);
        }
        return this;
    }

    /**
     * The server provides support to resolve additional information for a completion item.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getResolveProvider() {
        return jsonData.has("resolveProvider") ? jsonData.getBoolean("resolveProvider") : null;
    }

    public CompletionOptions setResolveProvider(Boolean resolveProvider) {
        jsonData.putOpt("resolveProvider", resolveProvider);
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
        CompletionOptions other = (CompletionOptions) obj;
        if (!Objects.equals(this.getTriggerCharacters(), other.getTriggerCharacters())) {
            return false;
        }
        if (!Objects.equals(this.getAllCommitCharacters(), other.getAllCommitCharacters())) {
            return false;
        }
        if (!Objects.equals(this.getResolveProvider(), other.getResolveProvider())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneProgress(), other.getWorkDoneProgress())) {
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
        if (this.getAllCommitCharacters() != null) {
            hash = 17 * hash + Objects.hashCode(this.getAllCommitCharacters());
        }
        if (this.getResolveProvider() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getResolveProvider());
        }
        if (this.getWorkDoneProgress() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getWorkDoneProgress());
        }
        return hash;
    }

    public static CompletionOptions create() {
        final JSONObject json = new JSONObject();
        return new CompletionOptions(json);
    }
}
