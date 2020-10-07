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

/**
 * Arguments for 'goto' request.
 */
public class GotoArguments extends JSONBase {

    GotoArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Set the goto target for this thread.
     */
    public int getThreadId() {
        return jsonData.getInt("threadId");
    }

    public GotoArguments setThreadId(int threadId) {
        jsonData.put("threadId", threadId);
        return this;
    }

    /**
     * The location where the debuggee will continue to run.
     */
    public int getTargetId() {
        return jsonData.getInt("targetId");
    }

    public GotoArguments setTargetId(int targetId) {
        jsonData.put("targetId", targetId);
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
        GotoArguments other = (GotoArguments) obj;
        if (this.getThreadId() != other.getThreadId()) {
            return false;
        }
        if (this.getTargetId() != other.getTargetId()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Integer.hashCode(this.getThreadId());
        hash = 97 * hash + Integer.hashCode(this.getTargetId());
        return hash;
    }

    public static GotoArguments create(Integer threadId, Integer targetId) {
        final JSONObject json = new JSONObject();
        json.put("threadId", threadId);
        json.put("targetId", targetId);
        return new GotoArguments(json);
    }
}
