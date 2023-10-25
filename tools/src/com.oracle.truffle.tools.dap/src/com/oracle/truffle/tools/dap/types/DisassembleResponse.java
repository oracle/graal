/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.types;

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Response to 'disassemble' request.
 */
public class DisassembleResponse extends Response {

    DisassembleResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return jsonData.has("body") ? new ResponseBody(jsonData.optJSONObject("body")) : null;
    }

    public DisassembleResponse setBody(ResponseBody body) {
        jsonData.putOpt("body", body != null ? body.jsonData : null);
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
        DisassembleResponse other = (DisassembleResponse) obj;
        if (!Objects.equals(this.getBody(), other.getBody())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (this.getRequestSeq() != other.getRequestSeq()) {
            return false;
        }
        if (this.isSuccess() != other.isSuccess()) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getBody() != null) {
            hash = 29 * hash + Objects.hashCode(this.getBody());
        }
        hash = 29 * hash + Objects.hashCode(this.getType());
        hash = 29 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 29 * hash + Boolean.hashCode(this.isSuccess());
        hash = 29 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 29 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 29 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static DisassembleResponse create(Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new DisassembleResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The list of disassembled instructions.
         */
        public List<DisassembledInstruction> getInstructions() {
            final JSONArray json = jsonData.getJSONArray("instructions");
            final List<DisassembledInstruction> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(new DisassembledInstruction(json.getJSONObject(i)));
            }
            return Collections.unmodifiableList(list);
        }

        public ResponseBody setInstructions(List<DisassembledInstruction> instructions) {
            final JSONArray json = new JSONArray();
            for (DisassembledInstruction disassembledInstruction : instructions) {
                json.put(disassembledInstruction.jsonData);
            }
            jsonData.put("instructions", json);
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
            ResponseBody other = (ResponseBody) obj;
            if (!Objects.equals(this.getInstructions(), other.getInstructions())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.getInstructions());
            return hash;
        }

        public static ResponseBody create(List<DisassembledInstruction> instructions) {
            final JSONObject json = new JSONObject();
            JSONArray instructionsJsonArr = new JSONArray();
            for (DisassembledInstruction disassembledInstruction : instructions) {
                instructionsJsonArr.put(disassembledInstruction.jsonData);
            }
            json.put("instructions", instructionsJsonArr);
            return new ResponseBody(json);
        }
    }
}
