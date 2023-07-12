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
 * A StepInTarget can be used in the 'stepIn' request and determines into which single target the
 * stepIn request should step.
 */
public class StepInTarget extends JSONBase {

    StepInTarget(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Unique identifier for a stepIn target.
     */
    public int getId() {
        return jsonData.getInt("id");
    }

    public StepInTarget setId(int id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * The name of the stepIn target (shown in the UI).
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public StepInTarget setLabel(String label) {
        jsonData.put("label", label);
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
        StepInTarget other = (StepInTarget) obj;
        if (this.getId() != other.getId()) {
            return false;
        }
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Integer.hashCode(this.getId());
        hash = 79 * hash + Objects.hashCode(this.getLabel());
        return hash;
    }

    public static StepInTarget create(Integer id, String label) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("label", label);
        return new StepInTarget(json);
    }
}
