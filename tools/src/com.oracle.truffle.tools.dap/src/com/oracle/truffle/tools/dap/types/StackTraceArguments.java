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
 * Arguments for 'stackTrace' request.
 */
public class StackTraceArguments extends JSONBase {

    StackTraceArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Retrieve the stacktrace for this thread.
     */
    public int getThreadId() {
        return jsonData.getInt("threadId");
    }

    public StackTraceArguments setThreadId(int threadId) {
        jsonData.put("threadId", threadId);
        return this;
    }

    /**
     * The index of the first frame to return; if omitted frames start at 0.
     */
    public Integer getStartFrame() {
        return jsonData.has("startFrame") ? jsonData.getInt("startFrame") : null;
    }

    public StackTraceArguments setStartFrame(Integer startFrame) {
        jsonData.putOpt("startFrame", startFrame);
        return this;
    }

    /**
     * The maximum number of frames to return. If levels is not specified or 0, all frames are
     * returned.
     */
    public Integer getLevels() {
        return jsonData.has("levels") ? jsonData.getInt("levels") : null;
    }

    public StackTraceArguments setLevels(Integer levels) {
        jsonData.putOpt("levels", levels);
        return this;
    }

    /**
     * Specifies details on how to format the stack frames. The attribute is only honored by a debug
     * adapter if the capability 'supportsValueFormattingOptions' is true.
     */
    public StackFrameFormat getFormat() {
        return jsonData.has("format") ? new StackFrameFormat(jsonData.optJSONObject("format")) : null;
    }

    public StackTraceArguments setFormat(StackFrameFormat format) {
        jsonData.putOpt("format", format != null ? format.jsonData : null);
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
        StackTraceArguments other = (StackTraceArguments) obj;
        if (this.getThreadId() != other.getThreadId()) {
            return false;
        }
        if (!Objects.equals(this.getStartFrame(), other.getStartFrame())) {
            return false;
        }
        if (!Objects.equals(this.getLevels(), other.getLevels())) {
            return false;
        }
        if (!Objects.equals(this.getFormat(), other.getFormat())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Integer.hashCode(this.getThreadId());
        if (this.getStartFrame() != null) {
            hash = 97 * hash + Integer.hashCode(this.getStartFrame());
        }
        if (this.getLevels() != null) {
            hash = 97 * hash + Integer.hashCode(this.getLevels());
        }
        if (this.getFormat() != null) {
            hash = 97 * hash + Objects.hashCode(this.getFormat());
        }
        return hash;
    }

    public static StackTraceArguments create(Integer threadId) {
        final JSONObject json = new JSONObject();
        json.put("threadId", threadId);
        return new StackTraceArguments(json);
    }
}
