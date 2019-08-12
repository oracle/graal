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

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * The initialize parameters
 */
public class InitializeParams {

    final JSONObject jsonData;

    InitializeParams(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The process Id of the parent process that started
     * the server.
     */
    public int getProcessId() {
        return jsonData.getInt("processId");
    }

    public InitializeParams setProcessId(int processId) {
        jsonData.put("processId", processId);
        return this;
    }

    /**
     * The rootPath of the workspace. Is null
     * if no folder is open.
     *
     * @deprecated in favour of rootUri.
     */
    public String getRootPath() {
        return jsonData.optString("rootPath");
    }

    public InitializeParams setRootPath(String rootPath) {
        jsonData.putOpt("rootPath", rootPath);
        return this;
    }

    /**
     * The rootUri of the workspace. Is null if no
     * folder is open. If both `rootPath` and `rootUri` are set
     * `rootUri` wins.
     *
     * @deprecated in favour of workspaceFolders.
     */
    public String getRootUri() {
        return jsonData.getString("rootUri");
    }

    public InitializeParams setRootUri(String rootUri) {
        jsonData.put("rootUri", rootUri);
        return this;
    }

    /**
     * The capabilities provided by the client (editor or tool)
     */
    public ClientCapabilities getCapabilities() {
        return new ClientCapabilities(jsonData.getJSONObject("capabilities"));
    }

    public InitializeParams setCapabilities(ClientCapabilities capabilities) {
        jsonData.put("capabilities", capabilities.jsonData);
        return this;
    }

    /**
     * User provided initialization options.
     */
    public Object getInitializationOptions() {
        return jsonData.opt("initializationOptions");
    }

    public InitializeParams setInitializationOptions(Object initializationOptions) {
        jsonData.putOpt("initializationOptions", initializationOptions);
        return this;
    }

    /**
     * The initial trace setting. If omitted trace is disabled ('off').
     */
    public String getTrace() {
        return jsonData.optString("trace");
    }

    public InitializeParams setTrace(String trace) {
        jsonData.putOpt("trace", trace);
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
        InitializeParams other = (InitializeParams) obj;
        if (this.getProcessId() != other.getProcessId()) {
            return false;
        }
        if (!Objects.equals(this.getRootPath(), other.getRootPath())) {
            return false;
        }
        if (!Objects.equals(this.getRootUri(), other.getRootUri())) {
            return false;
        }
        if (!Objects.equals(this.getCapabilities(), other.getCapabilities())) {
            return false;
        }
        if (!Objects.equals(this.getInitializationOptions(), other.getInitializationOptions())) {
            return false;
        }
        if (!Objects.equals(this.getTrace(), other.getTrace())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Integer.hashCode(this.getProcessId());
        if (this.getRootPath() != null) {
            hash = 59 * hash + Objects.hashCode(this.getRootPath());
        }
        hash = 59 * hash + Objects.hashCode(this.getRootUri());
        hash = 59 * hash + Objects.hashCode(this.getCapabilities());
        if (this.getInitializationOptions() != null) {
            hash = 59 * hash + Objects.hashCode(this.getInitializationOptions());
        }
        if (this.getTrace() != null) {
            hash = 59 * hash + Objects.hashCode(this.getTrace());
        }
        return hash;
    }

    public static InitializeParams create(Integer processId, String rootUri, ClientCapabilities capabilities) {
        final JSONObject json = new JSONObject();
        json.put("processId", processId);
        json.put("rootUri", rootUri);
        json.put("capabilities", capabilities.jsonData);
        return new InitializeParams(json);
    }
}
