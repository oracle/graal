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

/**
 * Arguments for 'stepInTargets' request.
 */
public class StepInTargetsArguments extends JSONBase {

    StepInTargetsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The stack frame for which to retrieve the possible stepIn targets.
     */
    public int getFrameId() {
        return jsonData.getInt("frameId");
    }

    public StepInTargetsArguments setFrameId(int frameId) {
        jsonData.put("frameId", frameId);
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
        StepInTargetsArguments other = (StepInTargetsArguments) obj;
        if (this.getFrameId() != other.getFrameId()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Integer.hashCode(this.getFrameId());
        return hash;
    }

    public static StepInTargetsArguments create(Integer frameId) {
        final JSONObject json = new JSONObject();
        json.put("frameId", frameId);
        return new StepInTargetsArguments(json);
    }
}
