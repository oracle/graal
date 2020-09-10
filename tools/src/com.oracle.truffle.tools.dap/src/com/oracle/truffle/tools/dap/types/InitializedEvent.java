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
 * Event message for 'initialized' event type. This event indicates that the debug adapter is ready
 * to accept configuration requests (e.g. SetBreakpointsRequest, SetExceptionBreakpointsRequest). A
 * debug adapter is expected to send this event when it is ready to accept configuration requests
 * (but not before the 'initialize' request has finished). The sequence of events/requests is as
 * follows: - adapters sends 'initialized' event (after the 'initialize' request has returned) -
 * frontend sends zero or more 'setBreakpoints' requests - frontend sends one
 * 'setFunctionBreakpoints' request (if capability 'supportsFunctionBreakpoints' is true) - frontend
 * sends a 'setExceptionBreakpoints' request if one or more 'exceptionBreakpointFilters' have been
 * defined (or if 'supportsConfigurationDoneRequest' is not defined or false) - frontend sends other
 * future configuration requests - frontend sends one 'configurationDone' request to indicate the
 * end of the configuration.
 */
public class InitializedEvent extends Event {

    InitializedEvent(JSONObject jsonData) {
        super(jsonData);
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
        InitializedEvent other = (InitializedEvent) obj;
        if (!Objects.equals(this.getEvent(), other.getEvent())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (!Objects.equals(this.getBody(), other.getBody())) {
            return false;
        }
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.getEvent());
        hash = 47 * hash + Objects.hashCode(this.getType());
        if (this.getBody() != null) {
            hash = 47 * hash + Objects.hashCode(this.getBody());
        }
        hash = 47 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static InitializedEvent create(Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "initialized");
        json.put("type", "event");
        json.put("seq", seq);
        return new InitializedEvent(json);
    }
}
