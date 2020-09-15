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

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * Response to 'evaluate' request.
 */
public class EvaluateResponse extends Response {

    EvaluateResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public EvaluateResponse setBody(ResponseBody body) {
        jsonData.put("body", body.jsonData);
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
        EvaluateResponse other = (EvaluateResponse) obj;
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
        hash = 59 * hash + Objects.hashCode(this.getBody());
        hash = 59 * hash + Objects.hashCode(this.getType());
        hash = 59 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 59 * hash + Boolean.hashCode(this.isSuccess());
        hash = 59 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 59 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 59 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static EvaluateResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new EvaluateResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The result of the evaluate request.
         */
        public String getResult() {
            return jsonData.getString("result");
        }

        public ResponseBody setResult(String result) {
            jsonData.put("result", result);
            return this;
        }

        /**
         * The optional type of the evaluate result. This attribute should only be returned by a
         * debug adapter if the client has passed the value true for the 'supportsVariableType'
         * capability of the 'initialize' request.
         */
        public String getType() {
            return jsonData.optString("type", null);
        }

        public ResponseBody setType(String type) {
            jsonData.putOpt("type", type);
            return this;
        }

        /**
         * Properties of a evaluate result that can be used to determine how to render the result in
         * the UI.
         */
        public VariablePresentationHint getPresentationHint() {
            return jsonData.has("presentationHint") ? new VariablePresentationHint(jsonData.optJSONObject("presentationHint")) : null;
        }

        public ResponseBody setPresentationHint(VariablePresentationHint presentationHint) {
            jsonData.putOpt("presentationHint", presentationHint != null ? presentationHint.jsonData : null);
            return this;
        }

        /**
         * If variablesReference is > 0, the evaluate result is structured and its children can be
         * retrieved by passing variablesReference to the VariablesRequest. The value should be less
         * than or equal to 2147483647 (2^31 - 1).
         */
        public int getVariablesReference() {
            return jsonData.getInt("variablesReference");
        }

        public ResponseBody setVariablesReference(int variablesReference) {
            jsonData.put("variablesReference", variablesReference);
            return this;
        }

        /**
         * The number of named child variables. The client can use this optional information to
         * present the variables in a paged UI and fetch them in chunks. The value should be less
         * than or equal to 2147483647 (2^31 - 1).
         */
        public Integer getNamedVariables() {
            return jsonData.has("namedVariables") ? jsonData.getInt("namedVariables") : null;
        }

        public ResponseBody setNamedVariables(Integer namedVariables) {
            jsonData.putOpt("namedVariables", namedVariables);
            return this;
        }

        /**
         * The number of indexed child variables. The client can use this optional information to
         * present the variables in a paged UI and fetch them in chunks. The value should be less
         * than or equal to 2147483647 (2^31 - 1).
         */
        public Integer getIndexedVariables() {
            return jsonData.has("indexedVariables") ? jsonData.getInt("indexedVariables") : null;
        }

        public ResponseBody setIndexedVariables(Integer indexedVariables) {
            jsonData.putOpt("indexedVariables", indexedVariables);
            return this;
        }

        /**
         * Optional memory reference to a location appropriate for this result. For pointer type
         * eval results, this is generally a reference to the memory address contained in the
         * pointer. This attribute should be returned by a debug adapter if the client has passed
         * the value true for the 'supportsMemoryReferences' capability of the 'initialize' request.
         */
        public String getMemoryReference() {
            return jsonData.optString("memoryReference", null);
        }

        public ResponseBody setMemoryReference(String memoryReference) {
            jsonData.putOpt("memoryReference", memoryReference);
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
            if (!Objects.equals(this.getResult(), other.getResult())) {
                return false;
            }
            if (!Objects.equals(this.getType(), other.getType())) {
                return false;
            }
            if (!Objects.equals(this.getPresentationHint(), other.getPresentationHint())) {
                return false;
            }
            if (this.getVariablesReference() != other.getVariablesReference()) {
                return false;
            }
            if (!Objects.equals(this.getNamedVariables(), other.getNamedVariables())) {
                return false;
            }
            if (!Objects.equals(this.getIndexedVariables(), other.getIndexedVariables())) {
                return false;
            }
            if (!Objects.equals(this.getMemoryReference(), other.getMemoryReference())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.getResult());
            if (this.getType() != null) {
                hash = 79 * hash + Objects.hashCode(this.getType());
            }
            if (this.getPresentationHint() != null) {
                hash = 79 * hash + Objects.hashCode(this.getPresentationHint());
            }
            hash = 79 * hash + Integer.hashCode(this.getVariablesReference());
            if (this.getNamedVariables() != null) {
                hash = 79 * hash + Integer.hashCode(this.getNamedVariables());
            }
            if (this.getIndexedVariables() != null) {
                hash = 79 * hash + Integer.hashCode(this.getIndexedVariables());
            }
            if (this.getMemoryReference() != null) {
                hash = 79 * hash + Objects.hashCode(this.getMemoryReference());
            }
            return hash;
        }

        public static ResponseBody create(String result, Integer variablesReference) {
            final JSONObject json = new JSONObject();
            json.put("result", result);
            json.put("variablesReference", variablesReference);
            return new ResponseBody(json);
        }
    }
}
