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

import java.util.Optional;

import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession.CommandPostProcessor;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.Location;

public abstract class DebuggerDomain extends Domain {

    protected DebuggerDomain() {
    }

    public abstract void setAsyncCallStackDepth(int maxDepth) throws CommandProcessException;

    public abstract void setBlackboxPatterns(String[] patterns);

    public abstract void setPauseOnExceptions(String state) throws CommandProcessException;

    public abstract Params getPossibleBreakpoints(Location start, Location end, boolean restrictToFunction) throws CommandProcessException;

    public abstract Params getScriptSource(String scriptId) throws CommandProcessException;

    public abstract void pause();

    public abstract void resume(CommandPostProcessor postProcessor);

    public abstract void stepInto(CommandPostProcessor postProcessor);

    public abstract void stepOver(CommandPostProcessor postProcessor);

    public abstract void stepOut(CommandPostProcessor postProcessor);

    public abstract Params searchInContent(String scriptId, String query, boolean caseSensitive, boolean isRegex) throws CommandProcessException;

    public abstract void setBreakpointsActive(Optional<Boolean> breakpointsActive) throws CommandProcessException;

    public abstract void setSkipAllPauses(Optional<Boolean> skip) throws CommandProcessException;

    public abstract Params setBreakpointByUrl(String url, String urlRegex, int line, int column, String condition) throws CommandProcessException;

    public abstract Params setBreakpoint(Location location, String condition) throws CommandProcessException;

    public abstract Params setBreakpointOnFunctionCall(String functionObjectId, String condition) throws CommandProcessException;

    public abstract void removeBreakpoint(String id) throws CommandProcessException;

    public abstract void continueToLocation(Location location, CommandPostProcessor postProcessor) throws CommandProcessException;

    public abstract Params evaluateOnCallFrame(String callFrameId, String expression, String objectGroup,
                    boolean includeCommandLineAPI, boolean silent, boolean returnByValue, boolean generatePreview,
                    boolean throwOnSideEffect) throws CommandProcessException;

    public abstract Params restartFrame(long cmdId, String callFrameId, CommandPostProcessor postProcessor) throws CommandProcessException;

    public abstract void setVariableValue(int scopeNumber, String variableName, CallArgument newValue, String callFrameId) throws CommandProcessException;

    public abstract void setReturnValue(CallArgument newValue) throws CommandProcessException;

    protected final void resumed() {
        eventHandler.event(new Event("Debugger.resumer", null));
    }
}
