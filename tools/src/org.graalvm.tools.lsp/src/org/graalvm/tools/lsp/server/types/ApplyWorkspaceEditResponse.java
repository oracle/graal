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
 * A response returned from the apply workspace edit request.
 */
public class ApplyWorkspaceEditResponse extends JSONBase {

    ApplyWorkspaceEditResponse(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Indicates whether the edit was applied or not.
     */
    public boolean isApplied() {
        return jsonData.getBoolean("applied");
    }

    public ApplyWorkspaceEditResponse setApplied(boolean applied) {
        jsonData.put("applied", applied);
        return this;
    }

    /**
     * An optional textual description for why the edit was not applied. This may be used by the
     * server for diagnostic logging or to provide a suitable error for a request that triggered the
     * edit.
     */
    public String getFailureReason() {
        return jsonData.optString("failureReason", null);
    }

    public ApplyWorkspaceEditResponse setFailureReason(String failureReason) {
        jsonData.putOpt("failureReason", failureReason);
        return this;
    }

    /**
     * Depending on the client's failure handling strategy `failedChange` might contain the index of
     * the change that failed. This property is only available if the client signals a
     * `failureHandlingStrategy` in its client capabilities.
     */
    public Integer getFailedChange() {
        return jsonData.has("failedChange") ? jsonData.getInt("failedChange") : null;
    }

    public ApplyWorkspaceEditResponse setFailedChange(Integer failedChange) {
        jsonData.putOpt("failedChange", failedChange);
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
        ApplyWorkspaceEditResponse other = (ApplyWorkspaceEditResponse) obj;
        if (this.isApplied() != other.isApplied()) {
            return false;
        }
        if (!Objects.equals(this.getFailureReason(), other.getFailureReason())) {
            return false;
        }
        if (!Objects.equals(this.getFailedChange(), other.getFailedChange())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Boolean.hashCode(this.isApplied());
        if (this.getFailureReason() != null) {
            hash = 53 * hash + Objects.hashCode(this.getFailureReason());
        }
        if (this.getFailedChange() != null) {
            hash = 53 * hash + Integer.hashCode(this.getFailedChange());
        }
        return hash;
    }

    public static ApplyWorkspaceEditResponse create(Boolean applied) {
        final JSONObject json = new JSONObject();
        json.put("applied", applied);
        return new ApplyWorkspaceEditResponse(json);
    }
}
