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
 * Signature help represents the signature of something callable. There can be multiple signature
 * but only one active and only one active parameter.
 */
public class SignatureHelp {

    final JSONObject jsonData;

    SignatureHelp(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * One or more signatures.
     */
    public List<SignatureInformation> getSignatures() {
        final JSONArray json = jsonData.getJSONArray("signatures");
        final List<SignatureInformation> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new SignatureInformation(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public SignatureHelp setSignatures(List<SignatureInformation> signatures) {
        final JSONArray json = new JSONArray();
        for (SignatureInformation signatureInformation : signatures) {
            json.put(signatureInformation.jsonData);
        }
        jsonData.put("signatures", json);
        return this;
    }

    /**
     * The active signature. Set to `null` if no signatures exist.
     */
    public int getActiveSignature() {
        return jsonData.getInt("activeSignature");
    }

    public SignatureHelp setActiveSignature(int activeSignature) {
        jsonData.put("activeSignature", activeSignature);
        return this;
    }

    /**
     * The active parameter of the active signature. Set to `null` if the active signature has no
     * parameters.
     */
    public int getActiveParameter() {
        return jsonData.getInt("activeParameter");
    }

    public SignatureHelp setActiveParameter(int activeParameter) {
        jsonData.put("activeParameter", activeParameter);
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
        SignatureHelp other = (SignatureHelp) obj;
        if (!Objects.equals(this.getSignatures(), other.getSignatures())) {
            return false;
        }
        if (this.getActiveSignature() != other.getActiveSignature()) {
            return false;
        }
        if (this.getActiveParameter() != other.getActiveParameter()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getSignatures());
        hash = 41 * hash + Integer.hashCode(this.getActiveSignature());
        hash = 41 * hash + Integer.hashCode(this.getActiveParameter());
        return hash;
    }

    public static SignatureHelp create(List<SignatureInformation> signatures, Integer activeSignature, Integer activeParameter) {
        final JSONObject json = new JSONObject();
        JSONArray signaturesJsonArr = new JSONArray();
        for (SignatureInformation signatureInformation : signatures) {
            signaturesJsonArr.put(signatureInformation.jsonData);
        }
        json.put("signatures", signaturesJsonArr);
        json.put("activeSignature", activeSignature);
        json.put("activeParameter", activeParameter);
        return new SignatureHelp(json);
    }
}
