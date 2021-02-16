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
 * The initialize parameters.
 */
public class InitializeParams extends WorkDoneProgressParams {

    InitializeParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The process Id of the parent process that started the server.
     */
    public Integer getProcessId() {
        Object obj = jsonData.get("processId");
        return JSONObject.NULL.equals(obj) ? null : (Integer) obj;
    }

    public InitializeParams setProcessId(Integer processId) {
        jsonData.put("processId", processId == null ? JSONObject.NULL : processId);
        return this;
    }

    /**
     * Information about the client.
     *
     * @since 3.15.0
     */
    public ClientInfoParams getClientInfo() {
        return jsonData.has("clientInfo") ? new ClientInfoParams(jsonData.optJSONObject("clientInfo")) : null;
    }

    public InitializeParams setClientInfo(ClientInfoParams clientInfo) {
        jsonData.putOpt("clientInfo", clientInfo != null ? clientInfo.jsonData : null);
        return this;
    }

    /**
     * The rootPath of the workspace. Is null if no folder is open.
     *
     * @deprecated in favour of rootUri.
     */
    @Deprecated
    public String getRootPath() {
        Object obj = jsonData.opt("rootPath");
        return JSONObject.NULL.equals(obj) ? null : (String) obj;
    }

    public InitializeParams setRootPath(String rootPath) {
        jsonData.put("rootPath", rootPath == null ? JSONObject.NULL : rootPath);
        return this;
    }

    /**
     * The rootUri of the workspace. Is null if no folder is open. If both `rootPath` and `rootUri`
     * are set `rootUri` wins.
     *
     * @deprecated in favour of workspaceFolders.
     */
    @Deprecated
    public String getRootUri() {
        Object obj = jsonData.get("rootUri");
        return JSONObject.NULL.equals(obj) ? null : (String) obj;
    }

    public InitializeParams setRootUri(String rootUri) {
        jsonData.put("rootUri", rootUri == null ? JSONObject.NULL : rootUri);
        return this;
    }

    /**
     * The capabilities provided by the client (editor or tool).
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
        return jsonData.optString("trace", null);
    }

    public InitializeParams setTrace(String trace) {
        jsonData.putOpt("trace", trace);
        return this;
    }

    /**
     * The actual configured workspace folders.
     */
    public List<WorkspaceFolder> getWorkspaceFolders() {
        final JSONArray json = jsonData.optJSONArray("workspaceFolders");
        if (json == null) {
            return null;
        }
        final List<WorkspaceFolder> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new WorkspaceFolder(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public InitializeParams setWorkspaceFolders(List<WorkspaceFolder> workspaceFolders) {
        if (workspaceFolders != null) {
            final JSONArray json = new JSONArray();
            for (WorkspaceFolder workspaceFolder : workspaceFolders) {
                json.put(workspaceFolder.jsonData);
            }
            jsonData.put("workspaceFolders", json);
        } else {
            jsonData.put("workspaceFolders", JSONObject.NULL);
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
        InitializeParams other = (InitializeParams) obj;
        if (!Objects.equals(this.getProcessId(), other.getProcessId())) {
            return false;
        }
        if (!Objects.equals(this.getClientInfo(), other.getClientInfo())) {
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
        if (!Objects.equals(this.getWorkspaceFolders(), other.getWorkspaceFolders())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneToken(), other.getWorkDoneToken())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getProcessId() != null) {
            hash = 59 * hash + Integer.hashCode(this.getProcessId());
        }
        if (this.getClientInfo() != null) {
            hash = 59 * hash + Objects.hashCode(this.getClientInfo());
        }
        if (this.getRootPath() != null) {
            hash = 59 * hash + Objects.hashCode(this.getRootPath());
        }
        if (this.getRootUri() != null) {
            hash = 59 * hash + Objects.hashCode(this.getRootUri());
        }
        hash = 59 * hash + Objects.hashCode(this.getCapabilities());
        if (this.getInitializationOptions() != null) {
            hash = 59 * hash + Objects.hashCode(this.getInitializationOptions());
        }
        if (this.getTrace() != null) {
            hash = 59 * hash + Objects.hashCode(this.getTrace());
        }
        if (this.getWorkspaceFolders() != null) {
            hash = 59 * hash + Objects.hashCode(this.getWorkspaceFolders());
        }
        if (this.getWorkDoneToken() != null) {
            hash = 59 * hash + Objects.hashCode(this.getWorkDoneToken());
        }
        return hash;
    }

    public static InitializeParams create(Integer processId, String rootUri, ClientCapabilities capabilities, List<WorkspaceFolder> workspaceFolders) {
        final JSONObject json = new JSONObject();
        json.put("processId", processId == null ? JSONObject.NULL : processId);
        json.put("rootUri", rootUri == null ? JSONObject.NULL : rootUri);
        json.put("capabilities", capabilities.jsonData);
        if (workspaceFolders != null) {
            JSONArray workspaceFoldersJsonArr = new JSONArray();
            for (WorkspaceFolder workspaceFolder : workspaceFolders) {
                workspaceFoldersJsonArr.put(workspaceFolder.jsonData);
            }
            json.put("workspaceFolders", workspaceFoldersJsonArr);
        } else {
            json.put("workspaceFolders", JSONObject.NULL);
        }
        return new InitializeParams(json);
    }

    public static class ClientInfoParams extends JSONBase {

        ClientInfoParams(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The name of the client as defined by the client.
         */
        public String getName() {
            return jsonData.getString("name");
        }

        public ClientInfoParams setName(String name) {
            jsonData.put("name", name);
            return this;
        }

        /**
         * The client's version as defined by the client.
         */
        public String getVersion() {
            return jsonData.optString("version", null);
        }

        public ClientInfoParams setVersion(String version) {
            jsonData.putOpt("version", version);
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
            ClientInfoParams other = (ClientInfoParams) obj;
            if (!Objects.equals(this.getName(), other.getName())) {
                return false;
            }
            if (!Objects.equals(this.getVersion(), other.getVersion())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89 * hash + Objects.hashCode(this.getName());
            if (this.getVersion() != null) {
                hash = 89 * hash + Objects.hashCode(this.getVersion());
            }
            return hash;
        }
    }
}
