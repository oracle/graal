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
 * Arguments for 'initialize' request.
 */
public class InitializeRequestArguments extends JSONBase {

    InitializeRequestArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The ID of the (frontend) client using this adapter.
     */
    public String getClientID() {
        return jsonData.optString("clientID", null);
    }

    public InitializeRequestArguments setClientID(String clientID) {
        jsonData.putOpt("clientID", clientID);
        return this;
    }

    /**
     * The human readable name of the (frontend) client using this adapter.
     */
    public String getClientName() {
        return jsonData.optString("clientName", null);
    }

    public InitializeRequestArguments setClientName(String clientName) {
        jsonData.putOpt("clientName", clientName);
        return this;
    }

    /**
     * The ID of the debug adapter.
     */
    public String getAdapterID() {
        return jsonData.getString("adapterID");
    }

    public InitializeRequestArguments setAdapterID(String adapterID) {
        jsonData.put("adapterID", adapterID);
        return this;
    }

    /**
     * The ISO-639 locale of the (frontend) client using this adapter, e.g. en-US or de-CH.
     */
    public String getLocale() {
        return jsonData.optString("locale", null);
    }

    public InitializeRequestArguments setLocale(String locale) {
        jsonData.putOpt("locale", locale);
        return this;
    }

    /**
     * If true all line numbers are 1-based (default).
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getLinesStartAt1() {
        return jsonData.has("linesStartAt1") ? jsonData.getBoolean("linesStartAt1") : null;
    }

    public InitializeRequestArguments setLinesStartAt1(Boolean linesStartAt1) {
        jsonData.putOpt("linesStartAt1", linesStartAt1);
        return this;
    }

    /**
     * If true all column numbers are 1-based (default).
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getColumnsStartAt1() {
        return jsonData.has("columnsStartAt1") ? jsonData.getBoolean("columnsStartAt1") : null;
    }

    public InitializeRequestArguments setColumnsStartAt1(Boolean columnsStartAt1) {
        jsonData.putOpt("columnsStartAt1", columnsStartAt1);
        return this;
    }

    /**
     * Determines in what format paths are specified. The default is 'path', which is the native
     * format. Values: 'path', 'uri', etc.
     */
    public String getPathFormat() {
        return jsonData.optString("pathFormat", null);
    }

    public InitializeRequestArguments setPathFormat(String pathFormat) {
        jsonData.putOpt("pathFormat", pathFormat);
        return this;
    }

    /**
     * Client supports the optional type attribute for variables.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsVariableType() {
        return jsonData.has("supportsVariableType") ? jsonData.getBoolean("supportsVariableType") : null;
    }

    public InitializeRequestArguments setSupportsVariableType(Boolean supportsVariableType) {
        jsonData.putOpt("supportsVariableType", supportsVariableType);
        return this;
    }

    /**
     * Client supports the paging of variables.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsVariablePaging() {
        return jsonData.has("supportsVariablePaging") ? jsonData.getBoolean("supportsVariablePaging") : null;
    }

    public InitializeRequestArguments setSupportsVariablePaging(Boolean supportsVariablePaging) {
        jsonData.putOpt("supportsVariablePaging", supportsVariablePaging);
        return this;
    }

    /**
     * Client supports the runInTerminal request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsRunInTerminalRequest() {
        return jsonData.has("supportsRunInTerminalRequest") ? jsonData.getBoolean("supportsRunInTerminalRequest") : null;
    }

    public InitializeRequestArguments setSupportsRunInTerminalRequest(Boolean supportsRunInTerminalRequest) {
        jsonData.putOpt("supportsRunInTerminalRequest", supportsRunInTerminalRequest);
        return this;
    }

    /**
     * Client supports memory references.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsMemoryReferences() {
        return jsonData.has("supportsMemoryReferences") ? jsonData.getBoolean("supportsMemoryReferences") : null;
    }

    public InitializeRequestArguments setSupportsMemoryReferences(Boolean supportsMemoryReferences) {
        jsonData.putOpt("supportsMemoryReferences", supportsMemoryReferences);
        return this;
    }

    /**
     * Client supports progress reporting.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsProgressReporting() {
        return jsonData.has("supportsProgressReporting") ? jsonData.getBoolean("supportsProgressReporting") : null;
    }

    public InitializeRequestArguments setSupportsProgressReporting(Boolean supportsProgressReporting) {
        jsonData.putOpt("supportsProgressReporting", supportsProgressReporting);
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
        InitializeRequestArguments other = (InitializeRequestArguments) obj;
        if (!Objects.equals(this.getClientID(), other.getClientID())) {
            return false;
        }
        if (!Objects.equals(this.getClientName(), other.getClientName())) {
            return false;
        }
        if (!Objects.equals(this.getAdapterID(), other.getAdapterID())) {
            return false;
        }
        if (!Objects.equals(this.getLocale(), other.getLocale())) {
            return false;
        }
        if (!Objects.equals(this.getLinesStartAt1(), other.getLinesStartAt1())) {
            return false;
        }
        if (!Objects.equals(this.getColumnsStartAt1(), other.getColumnsStartAt1())) {
            return false;
        }
        if (!Objects.equals(this.getPathFormat(), other.getPathFormat())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsVariableType(), other.getSupportsVariableType())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsVariablePaging(), other.getSupportsVariablePaging())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsRunInTerminalRequest(), other.getSupportsRunInTerminalRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsMemoryReferences(), other.getSupportsMemoryReferences())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsProgressReporting(), other.getSupportsProgressReporting())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getClientID() != null) {
            hash = 97 * hash + Objects.hashCode(this.getClientID());
        }
        if (this.getClientName() != null) {
            hash = 97 * hash + Objects.hashCode(this.getClientName());
        }
        hash = 97 * hash + Objects.hashCode(this.getAdapterID());
        if (this.getLocale() != null) {
            hash = 97 * hash + Objects.hashCode(this.getLocale());
        }
        if (this.getLinesStartAt1() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getLinesStartAt1());
        }
        if (this.getColumnsStartAt1() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getColumnsStartAt1());
        }
        if (this.getPathFormat() != null) {
            hash = 97 * hash + Objects.hashCode(this.getPathFormat());
        }
        if (this.getSupportsVariableType() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsVariableType());
        }
        if (this.getSupportsVariablePaging() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsVariablePaging());
        }
        if (this.getSupportsRunInTerminalRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsRunInTerminalRequest());
        }
        if (this.getSupportsMemoryReferences() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsMemoryReferences());
        }
        if (this.getSupportsProgressReporting() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsProgressReporting());
        }
        return hash;
    }

    public static InitializeRequestArguments create(String adapterID) {
        final JSONObject json = new JSONObject();
        json.put("adapterID", adapterID);
        return new InitializeRequestArguments(json);
    }
}
