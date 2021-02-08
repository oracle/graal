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
 * Arguments for 'disconnect' request.
 */
public class DisconnectArguments extends JSONBase {

    DisconnectArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * A value of true indicates that this 'disconnect' request is part of a restart sequence.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getRestart() {
        return jsonData.has("restart") ? jsonData.getBoolean("restart") : null;
    }

    public DisconnectArguments setRestart(Boolean restart) {
        jsonData.putOpt("restart", restart);
        return this;
    }

    /**
     * Indicates whether the debuggee should be terminated when the debugger is disconnected. If
     * unspecified, the debug adapter is free to do whatever it thinks is best. The attribute is
     * only honored by a debug adapter if the capability 'supportTerminateDebuggee' is true.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getTerminateDebuggee() {
        return jsonData.has("terminateDebuggee") ? jsonData.getBoolean("terminateDebuggee") : null;
    }

    public DisconnectArguments setTerminateDebuggee(Boolean terminateDebuggee) {
        jsonData.putOpt("terminateDebuggee", terminateDebuggee);
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
        DisconnectArguments other = (DisconnectArguments) obj;
        if (!Objects.equals(this.getRestart(), other.getRestart())) {
            return false;
        }
        if (!Objects.equals(this.getTerminateDebuggee(), other.getTerminateDebuggee())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getRestart() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getRestart());
        }
        if (this.getTerminateDebuggee() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getTerminateDebuggee());
        }
        return hash;
    }

    public static DisconnectArguments create() {
        final JSONObject json = new JSONObject();
        return new DisconnectArguments(json);
    }
}
