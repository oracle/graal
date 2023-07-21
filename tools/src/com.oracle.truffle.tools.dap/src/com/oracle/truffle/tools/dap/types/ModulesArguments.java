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
 * Arguments for 'modules' request.
 */
public class ModulesArguments extends JSONBase {

    ModulesArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The index of the first module to return; if omitted modules start at 0.
     */
    public Integer getStartModule() {
        return jsonData.has("startModule") ? jsonData.getInt("startModule") : null;
    }

    public ModulesArguments setStartModule(Integer startModule) {
        jsonData.putOpt("startModule", startModule);
        return this;
    }

    /**
     * The number of modules to return. If moduleCount is not specified or 0, all modules are
     * returned.
     */
    public Integer getModuleCount() {
        return jsonData.has("moduleCount") ? jsonData.getInt("moduleCount") : null;
    }

    public ModulesArguments setModuleCount(Integer moduleCount) {
        jsonData.putOpt("moduleCount", moduleCount);
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
        ModulesArguments other = (ModulesArguments) obj;
        if (!Objects.equals(this.getStartModule(), other.getStartModule())) {
            return false;
        }
        if (!Objects.equals(this.getModuleCount(), other.getModuleCount())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getStartModule() != null) {
            hash = 23 * hash + Integer.hashCode(this.getStartModule());
        }
        if (this.getModuleCount() != null) {
            hash = 23 * hash + Integer.hashCode(this.getModuleCount());
        }
        return hash;
    }

    public static ModulesArguments create() {
        final JSONObject json = new JSONObject();
        return new ModulesArguments(json);
    }
}
