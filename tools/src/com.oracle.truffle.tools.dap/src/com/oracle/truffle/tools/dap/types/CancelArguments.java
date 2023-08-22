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

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * Arguments for 'cancel' request.
 */
public class CancelArguments extends JSONBase {

    CancelArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The ID (attribute 'seq') of the request to cancel. If missing no request is cancelled. Both a
     * 'requestId' and a 'progressId' can be specified in one request.
     */
    public Integer getRequestId() {
        return jsonData.has("requestId") ? jsonData.getInt("requestId") : null;
    }

    public CancelArguments setRequestId(Integer requestId) {
        jsonData.putOpt("requestId", requestId);
        return this;
    }

    /**
     * The ID (attribute 'progressId') of the progress to cancel. If missing no progress is
     * cancelled. Both a 'requestId' and a 'progressId' can be specified in one request.
     */
    public String getProgressId() {
        return jsonData.optString("progressId", null);
    }

    public CancelArguments setProgressId(String progressId) {
        jsonData.putOpt("progressId", progressId);
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
        CancelArguments other = (CancelArguments) obj;
        if (!Objects.equals(this.getRequestId(), other.getRequestId())) {
            return false;
        }
        if (!Objects.equals(this.getProgressId(), other.getProgressId())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getRequestId() != null) {
            hash = 13 * hash + Integer.hashCode(this.getRequestId());
        }
        if (this.getProgressId() != null) {
            hash = 13 * hash + Objects.hashCode(this.getProgressId());
        }
        return hash;
    }

    public static CancelArguments create() {
        final JSONObject json = new JSONObject();
        return new CancelArguments(json);
    }
}
