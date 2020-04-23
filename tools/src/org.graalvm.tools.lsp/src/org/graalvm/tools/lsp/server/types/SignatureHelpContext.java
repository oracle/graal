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
 * Additional information about the context in which a signature help request was triggered.
 *
 * @since 3.15.0
 */
public class SignatureHelpContext extends JSONBase {

    SignatureHelpContext(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Action that caused signature help to be triggered.
     */
    public SignatureHelpTriggerKind getTriggerKind() {
        return SignatureHelpTriggerKind.get(jsonData.getInt("triggerKind"));
    }

    public SignatureHelpContext setTriggerKind(SignatureHelpTriggerKind triggerKind) {
        jsonData.put("triggerKind", triggerKind.getIntValue());
        return this;
    }

    /**
     * Character that caused signature help to be triggered.
     *
     * This is undefined when `triggerKind !== SignatureHelpTriggerKind.TriggerCharacter`
     */
    public String getTriggerCharacter() {
        return jsonData.optString("triggerCharacter", null);
    }

    public SignatureHelpContext setTriggerCharacter(String triggerCharacter) {
        jsonData.putOpt("triggerCharacter", triggerCharacter);
        return this;
    }

    /**
     * `true` if signature help was already showing when it was triggered.
     *
     * Retriggers occur when the signature help is already active and can be caused by actions such
     * as typing a trigger character, a cursor move, or document content changes.
     */
    public boolean isRetrigger() {
        return jsonData.getBoolean("isRetrigger");
    }

    public SignatureHelpContext setRetrigger(boolean isRetrigger) {
        jsonData.put("isRetrigger", isRetrigger);
        return this;
    }

    /**
     * The currently active `SignatureHelp`.
     *
     * The `activeSignatureHelp` has its `SignatureHelp.activeSignature` field updated based on the
     * user navigating through available signatures.
     */
    public SignatureHelp getActiveSignatureHelp() {
        return jsonData.has("activeSignatureHelp") ? new SignatureHelp(jsonData.optJSONObject("activeSignatureHelp")) : null;
    }

    public SignatureHelpContext setActiveSignatureHelp(SignatureHelp activeSignatureHelp) {
        jsonData.putOpt("activeSignatureHelp", activeSignatureHelp != null ? activeSignatureHelp.jsonData : null);
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
        SignatureHelpContext other = (SignatureHelpContext) obj;
        if (this.getTriggerKind() != other.getTriggerKind()) {
            return false;
        }
        if (!Objects.equals(this.getTriggerCharacter(), other.getTriggerCharacter())) {
            return false;
        }
        if (this.isRetrigger() != other.isRetrigger()) {
            return false;
        }
        if (!Objects.equals(this.getActiveSignatureHelp(), other.getActiveSignatureHelp())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.getTriggerKind());
        if (this.getTriggerCharacter() != null) {
            hash = 31 * hash + Objects.hashCode(this.getTriggerCharacter());
        }
        hash = 31 * hash + Boolean.hashCode(this.isRetrigger());
        if (this.getActiveSignatureHelp() != null) {
            hash = 31 * hash + Objects.hashCode(this.getActiveSignatureHelp());
        }
        return hash;
    }

    public static SignatureHelpContext create(SignatureHelpTriggerKind triggerKind, Boolean isRetrigger) {
        final JSONObject json = new JSONObject();
        json.put("triggerKind", triggerKind.getIntValue());
        json.put("isRetrigger", isRetrigger);
        return new SignatureHelpContext(json);
    }
}
