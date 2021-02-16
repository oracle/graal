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
 * Arguments for 'launch' request. Additional attributes are implementation specific.
 */
public class LaunchRequestArguments extends JSONBase {

    LaunchRequestArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * If noDebug is true the launch request should launch the program without enabling debugging.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getNoDebug() {
        return jsonData.has("noDebug") ? jsonData.getBoolean("noDebug") : null;
    }

    public LaunchRequestArguments setNoDebug(Boolean noDebug) {
        jsonData.putOpt("noDebug", noDebug);
        return this;
    }

    /**
     * Optional data from the previous, restarted session. The data is sent as the 'restart'
     * attribute of the 'terminated' event. The client should leave the data intact.
     */
    public Object getRestart() {
        return jsonData.opt("__restart");
    }

    public LaunchRequestArguments setRestart(Object restart) {
        jsonData.putOpt("__restart", restart);
        return this;
    }

    /**
     * Additional implementation specific attributes.
     *
     * @param attrName Attribute name.
     */
    public Object get(String attrName) {
        return jsonData.opt(attrName);
    }

    public LaunchRequestArguments set(String attrName, Object value) {
        jsonData.putOpt(attrName, value);
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
        LaunchRequestArguments other = (LaunchRequestArguments) obj;
        if (!Objects.equals(this.getNoDebug(), other.getNoDebug())) {
            return false;
        }
        if (!Objects.equals(this.getRestart(), other.getRestart())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        if (this.getNoDebug() != null) {
            hash = 41 * hash + Boolean.hashCode(this.getNoDebug());
        }
        if (this.getRestart() != null) {
            hash = 41 * hash + Objects.hashCode(this.getRestart());
        }
        return hash;
    }

    public static LaunchRequestArguments create() {
        final JSONObject json = new JSONObject();
        return new LaunchRequestArguments(json);
    }
}
