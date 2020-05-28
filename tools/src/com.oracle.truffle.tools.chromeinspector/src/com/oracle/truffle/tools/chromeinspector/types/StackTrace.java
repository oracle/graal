/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.debug.DebugStackTraceElement;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.ScriptsHandler;

public final class StackTrace {

    private final JSONObject jsonObject;

    public StackTrace(InspectorExecutionContext context, List<List<DebugStackTraceElement>> stacks) {
        jsonObject = new JSONObject();
        JSONObject jsonStack = jsonObject;
        JSONObject jsonParentStack = null;
        for (List<DebugStackTraceElement> frames : stacks) {
            if (jsonParentStack != null) {
                jsonStack.put("parent", jsonParentStack);
                jsonStack = jsonParentStack;
            }
            JSONArray callFrames = new JSONArray();
            for (DebugStackTraceElement frame : frames) {
                SourceSection sourceSection = frame.getSourceSection();
                if (sourceSection == null) {
                    continue;
                }
                if (!context.isInspectInternal() && frame.isInternal()) {
                    continue;
                }
                Source source = sourceSection.getSource();
                if (!context.isInspectInternal() && source.isInternal()) {
                    // should not be, double-check
                    continue;
                }
                JSONObject callFrame = new JSONObject();
                callFrame.put("functionName", frame.getName());
                ScriptsHandler sch = context.acquireScriptsHandler();
                try {
                    int scriptId = sch.assureLoaded(source);
                    if (scriptId != -1) {
                        callFrame.put("scriptId", Integer.toString(scriptId));
                        callFrame.put("url", sch.getScript(scriptId).getUrl());
                        callFrame.put("lineNumber", sourceSection.getStartLine() - 1);
                        callFrame.put("columnNumber", sourceSection.getStartColumn() - 1);
                        callFrames.put(callFrame);
                    }
                } finally {
                    context.releaseScriptsHandler();
                }
            }
            jsonStack.put("callFrames", callFrames);
            jsonParentStack = new JSONObject();
        }
    }

    public JSONObject toJSON() {
        return jsonObject;
    }
}
