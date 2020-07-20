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
 * Contains additional information about the context in which a completion request is triggered.
 */
public class CompletionContext extends JSONBase {

    CompletionContext(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * How the completion was triggered.
     */
    public CompletionTriggerKind getTriggerKind() {
        return CompletionTriggerKind.get(jsonData.getInt("triggerKind"));
    }

    public CompletionContext setTriggerKind(CompletionTriggerKind triggerKind) {
        jsonData.put("triggerKind", triggerKind.getIntValue());
        return this;
    }

    /**
     * The trigger character (a single character) that has trigger code complete. Is undefined if
     * `triggerKind !== CompletionTriggerKind.TriggerCharacter`
     */
    public String getTriggerCharacter() {
        return jsonData.optString("triggerCharacter", null);
    }

    public CompletionContext setTriggerCharacter(String triggerCharacter) {
        jsonData.putOpt("triggerCharacter", triggerCharacter);
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
        CompletionContext other = (CompletionContext) obj;
        if (this.getTriggerKind() != other.getTriggerKind()) {
            return false;
        }
        if (!Objects.equals(this.getTriggerCharacter(), other.getTriggerCharacter())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.getTriggerKind());
        if (this.getTriggerCharacter() != null) {
            hash = 59 * hash + Objects.hashCode(this.getTriggerCharacter());
        }
        return hash;
    }

    public static CompletionContext create(CompletionTriggerKind triggerKind) {
        final JSONObject json = new JSONObject();
        json.put("triggerKind", triggerKind.getIntValue());
        return new CompletionContext(json);
    }
}
