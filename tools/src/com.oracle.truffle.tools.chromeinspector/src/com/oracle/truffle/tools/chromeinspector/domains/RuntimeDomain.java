/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.domains;

import com.oracle.truffle.tools.utils.json.JSONArray;

import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession.CommandPostProcessor;

public abstract class RuntimeDomain extends Domain {

    protected RuntimeDomain() {
    }

    public abstract Params compileScript(String expression, String sourceURL, boolean persistScript, long executionContextId) throws CommandProcessException;

    public abstract Params evaluate(String expression, String objectGroup, boolean includeCommandLineAPI, boolean silent, int contextId, boolean returnByValue, boolean generatePreview,
                    boolean awaitPromise)
                    throws CommandProcessException;

    public abstract Params getProperties(String objectId, boolean ownProperties, boolean accessorPropertiesOnly, boolean generatePreview) throws CommandProcessException;

    public abstract Params callFunctionOn(String objectId, String functionDeclaration, JSONArray arguments, boolean silent, boolean returnByValue, boolean generatePreview, boolean awaitPromise,
                    int executionContextId, String objectGroup) throws CommandProcessException;

    public abstract void runIfWaitingForDebugger(CommandPostProcessor postProcessor);

    public abstract void releaseObject(String objectId);

    public abstract void releaseObjectGroup(String objectGroup);

    public abstract void notifyConsoleAPICalled(String type, Object text);

    protected void executionContextCreated(long id, String name) {
        eventHandler.event(new Event("Runtime.executionContextCreated", Params.createContext(id, name)));
    }

    protected void executionContextDestroyed(long id) {
        eventHandler.event(new Event("Runtime.executionContextDestroyed", Params.createContextId(id)));
    }

    public abstract void setCustomObjectFormatterEnabled(boolean enabled);

}
