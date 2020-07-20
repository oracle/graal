/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugStackTraceElement;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.ScriptsHandler;

public final class ExceptionDetails {

    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final DebugException debugException;
    private final String errorMessage;
    private final long exceptionId;

    public ExceptionDetails(DebugException debugException) {
        this.debugException = debugException;
        this.errorMessage = debugException.getLocalizedMessage();
        this.exceptionId = LAST_ID.incrementAndGet();
    }

    public ExceptionDetails(String errorMessage) {
        this.debugException = null;
        this.errorMessage = errorMessage;
        this.exceptionId = LAST_ID.incrementAndGet();
    }

    public JSONObject createJSON(InspectorExecutionContext context, boolean generatePreview) {
        JSONObject json = new JSONObject();
        json.put("exceptionId", exceptionId);
        if (debugException == null || debugException.getCatchLocation() != null) {
            json.put("text", "Caught");
        } else {
            json.put("text", "Uncaught");
        }
        SourceSection throwLocation = (debugException != null) ? debugException.getThrowLocation() : null;
        if (throwLocation != null) {
            json.put("lineNumber", throwLocation.getStartLine() - 1);
            json.put("columnNumber", throwLocation.getStartColumn() - 1);
            int scriptId;
            ScriptsHandler sch = context.acquireScriptsHandler();
            try {
                scriptId = sch.getScriptId(throwLocation.getSource());
            } finally {
                context.releaseScriptsHandler();
            }
            if (scriptId >= 0) {
                json.put("scriptId", Integer.toString(scriptId));
            } else {
                ScriptsHandler scriptsHandler = context.acquireScriptsHandler();
                try {
                    json.put("url", scriptsHandler.getSourceURL(throwLocation.getSource()));
                } finally {
                    context.releaseScriptsHandler();
                }
            }
        }
        if (debugException != null) {
            List<DebugStackTraceElement> stack = debugException.getDebugStackTrace();
            List<List<DebugStackTraceElement>> asyncStacks = debugException.getDebugAsynchronousStacks();
            List<List<DebugStackTraceElement>> stacks;
            if (asyncStacks.isEmpty()) {
                stacks = Collections.singletonList(stack);
            } else {
                stacks = new ArrayList<>();
                stacks.add(stack);
                stacks.addAll(asyncStacks);
            }
            StackTrace stackTrace = new StackTrace(context, stacks);
            json.put("stackTrace", stackTrace.toJSON());
        }
        DebugValue exceptionObject = (debugException != null) ? debugException.getExceptionObject() : null;
        if (exceptionObject != null) {
            RemoteObject ro = context.createAndRegister(exceptionObject, generatePreview);
            json.put("exception", ro.toJSON());
        } else {
            JSONObject ex = new JSONObject();
            ex.put("description", errorMessage);
            ex.put("value", errorMessage);
            ex.put("type", "string");
            json.put("exception", ex);
        }
        json.put("executionContextId", context.getId());
        return json;
    }

    /**
     * For test purposes only. Do not call from production code.
     */
    public static void resetIDs() {
        LAST_ID.set(0);
    }
}
