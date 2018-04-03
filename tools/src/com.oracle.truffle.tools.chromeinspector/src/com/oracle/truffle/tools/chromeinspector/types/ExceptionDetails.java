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

import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.chromeinspector.ScriptsHandler;
import com.oracle.truffle.tools.chromeinspector.TruffleExecutionContext;

public final class ExceptionDetails {

    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final DebugException debugException;
    private final long exceptionId;

    public ExceptionDetails(DebugException debugException) {
        this.debugException = debugException;
        this.exceptionId = LAST_ID.incrementAndGet();
    }

    public JSONObject createJSON(TruffleExecutionContext context) {
        JSONObject json = new JSONObject();
        json.put("exceptionId", exceptionId);
        json.put("text", debugException.getLocalizedMessage());
        SourceSection throwLocation = debugException.getThrowLocation();
        if (throwLocation != null) {
            json.put("lineNumber", throwLocation.getStartLine() - 1);
            json.put("columnNumber", throwLocation.getStartColumn() - 1);
            ScriptsHandler sch = context.getScriptsHandler();
            int scriptId = sch.getScriptId(throwLocation.getSource());
            if (scriptId >= 0) {
                json.put("scriptId", Integer.toString(scriptId));
            } else {
                json.put("url", ScriptsHandler.getNiceStringFromURI(throwLocation.getSource().getURI()));
            }
        }
        StackTrace stackTrace = new StackTrace(context, debugException.getDebugStackTrace());
        json.put("stackTrace", stackTrace);
        DebugValue exceptionObject = debugException.getExceptionObject();
        RemoteObject ro = context.createAndRegister(exceptionObject);
        json.put("exception", ro.toJSON());
        json.put("executionContextId", context.getId());
        return json;
    }
}
