/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.source.SourceSection;

public final class CallFrame {

    private final DebugStackFrame frame;
    private final int depth;
    private final Location location;
    private final Location functionLocation;
    private final String url;
    private final RemoteObject thisObject;
    private final RemoteObject returnObject;
    private final Scope[] scopes;

    public CallFrame(DebugStackFrame frame, int depth, Script script, SourceSection sourceSection, SuspendAnchor anchor,
                    SourceSection functionSourceSection, RemoteObject thisObject, RemoteObject returnObject, Scope... scopes) {
        this.frame = frame;
        this.depth = depth;
        if (anchor == SuspendAnchor.BEFORE) {
            this.location = new Location(script.getId(), sourceSection.getStartLine(), sourceSection.getStartColumn());
        } else {
            this.location = new Location(script.getId(), sourceSection.getEndLine(), sourceSection.getEndColumn());
        }
        if (functionSourceSection != null) {
            this.functionLocation = new Location(script.getId(), functionSourceSection.getStartLine(), functionSourceSection.getStartColumn());
        } else {
            this.functionLocation = null;
        }
        this.url = script.getUrl();
        this.thisObject = thisObject;
        this.returnObject = returnObject;
        this.scopes = scopes;
    }

    public DebugStackFrame getFrame() {
        return frame;
    }

    public String getFunctionName() {
        return frame.getName();
    }

    public Location getLocation() {
        return location;
    }

    public Scope[] getScopeChain() {
        return scopes;
    }

    public RemoteObject getThis() {
        return thisObject;
    }

    public RemoteObject getReturnValue() {
        return returnObject;
    }

    private JSONObject createJSON() {
        JSONObject json = new JSONObject();
        json.put("callFrameId", Integer.toString(depth));
        json.put("functionName", frame.getName());
        json.put("location", location.toJSON());
        json.putOpt("functionLocation", (functionLocation != null) ? functionLocation.toJSON() : null);
        json.put("url", url);
        json.put("scopeChain", Scope.createScopesJSON(scopes));
        json.put("this", thisObject.toJSON());
        if (returnObject != null) {
            json.put("returnValue", returnObject.toJSON());
        }
        return json;
    }

    public JSONObject toJSON() {
        return createJSON();
    }
}
