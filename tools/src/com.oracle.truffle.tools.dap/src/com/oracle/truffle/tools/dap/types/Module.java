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
 * A Module object represents a row in the modules view. Two attributes are mandatory: an id
 * identifies a module in the modules view and is used in a ModuleEvent for identifying a module for
 * adding, updating or deleting. The name is used to minimally render the module in the UI.
 *
 * Additional attributes can be added to the module. They will show up in the module View if they
 * have a corresponding ColumnDescriptor.
 *
 * To avoid an unnecessary proliferation of additional attributes with similar semantics but
 * different names we recommend to re-use attributes from the 'recommended' list below first, and
 * only introduce new attributes if nothing appropriate could be found.
 */
public class Module extends JSONBase {

    Module(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Unique identifier for the module.
     */
    public Object getId() {
        return jsonData.get("id");
    }

    public Module setId(Object id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * A name of the module.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public Module setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * optional but recommended attributes. always try to use these first before introducing
     * additional attributes.
     *
     * Logical full path to the module. The exact definition is implementation defined, but usually
     * this would be a full path to the on-disk file for the module.
     */
    public String getPath() {
        return jsonData.optString("path", null);
    }

    public Module setPath(String path) {
        jsonData.putOpt("path", path);
        return this;
    }

    /**
     * True if the module is optimized.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getIsOptimized() {
        return jsonData.has("isOptimized") ? jsonData.getBoolean("isOptimized") : null;
    }

    public Module setIsOptimized(Boolean isOptimized) {
        jsonData.putOpt("isOptimized", isOptimized);
        return this;
    }

    /**
     * True if the module is considered 'user code' by a debugger that supports 'Just My Code'.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getIsUserCode() {
        return jsonData.has("isUserCode") ? jsonData.getBoolean("isUserCode") : null;
    }

    public Module setIsUserCode(Boolean isUserCode) {
        jsonData.putOpt("isUserCode", isUserCode);
        return this;
    }

    /**
     * Version of Module.
     */
    public String getVersion() {
        return jsonData.optString("version", null);
    }

    public Module setVersion(String version) {
        jsonData.putOpt("version", version);
        return this;
    }

    /**
     * User understandable description of if symbols were found for the module (ex: 'Symbols
     * Loaded', 'Symbols not found', etc.
     */
    public String getSymbolStatus() {
        return jsonData.optString("symbolStatus", null);
    }

    public Module setSymbolStatus(String symbolStatus) {
        jsonData.putOpt("symbolStatus", symbolStatus);
        return this;
    }

    /**
     * Logical full path to the symbol file. The exact definition is implementation defined.
     */
    public String getSymbolFilePath() {
        return jsonData.optString("symbolFilePath", null);
    }

    public Module setSymbolFilePath(String symbolFilePath) {
        jsonData.putOpt("symbolFilePath", symbolFilePath);
        return this;
    }

    /**
     * Module created or modified.
     */
    public String getDateTimeStamp() {
        return jsonData.optString("dateTimeStamp", null);
    }

    public Module setDateTimeStamp(String dateTimeStamp) {
        jsonData.putOpt("dateTimeStamp", dateTimeStamp);
        return this;
    }

    /**
     * Address range covered by this module.
     */
    public String getAddressRange() {
        return jsonData.optString("addressRange", null);
    }

    public Module setAddressRange(String addressRange) {
        jsonData.putOpt("addressRange", addressRange);
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
        Module other = (Module) obj;
        if (!Objects.equals(this.getId(), other.getId())) {
            return false;
        }
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getPath(), other.getPath())) {
            return false;
        }
        if (!Objects.equals(this.getIsOptimized(), other.getIsOptimized())) {
            return false;
        }
        if (!Objects.equals(this.getIsUserCode(), other.getIsUserCode())) {
            return false;
        }
        if (!Objects.equals(this.getVersion(), other.getVersion())) {
            return false;
        }
        if (!Objects.equals(this.getSymbolStatus(), other.getSymbolStatus())) {
            return false;
        }
        if (!Objects.equals(this.getSymbolFilePath(), other.getSymbolFilePath())) {
            return false;
        }
        if (!Objects.equals(this.getDateTimeStamp(), other.getDateTimeStamp())) {
            return false;
        }
        if (!Objects.equals(this.getAddressRange(), other.getAddressRange())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.getId());
        hash = 29 * hash + Objects.hashCode(this.getName());
        if (this.getPath() != null) {
            hash = 29 * hash + Objects.hashCode(this.getPath());
        }
        if (this.getIsOptimized() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getIsOptimized());
        }
        if (this.getIsUserCode() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getIsUserCode());
        }
        if (this.getVersion() != null) {
            hash = 29 * hash + Objects.hashCode(this.getVersion());
        }
        if (this.getSymbolStatus() != null) {
            hash = 29 * hash + Objects.hashCode(this.getSymbolStatus());
        }
        if (this.getSymbolFilePath() != null) {
            hash = 29 * hash + Objects.hashCode(this.getSymbolFilePath());
        }
        if (this.getDateTimeStamp() != null) {
            hash = 29 * hash + Objects.hashCode(this.getDateTimeStamp());
        }
        if (this.getAddressRange() != null) {
            hash = 29 * hash + Objects.hashCode(this.getAddressRange());
        }
        return hash;
    }

    public static Module create(Object id, String name) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        return new Module(json);
    }
}
