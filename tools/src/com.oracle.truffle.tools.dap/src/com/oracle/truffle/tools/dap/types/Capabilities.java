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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Information about the capabilities of a debug adapter.
 */
public class Capabilities extends JSONBase {

    Capabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The debug adapter supports the 'configurationDone' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsConfigurationDoneRequest() {
        return jsonData.has("supportsConfigurationDoneRequest") ? jsonData.getBoolean("supportsConfigurationDoneRequest") : null;
    }

    public Capabilities setSupportsConfigurationDoneRequest(Boolean supportsConfigurationDoneRequest) {
        jsonData.putOpt("supportsConfigurationDoneRequest", supportsConfigurationDoneRequest);
        return this;
    }

    /**
     * The debug adapter supports function breakpoints.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsFunctionBreakpoints() {
        return jsonData.has("supportsFunctionBreakpoints") ? jsonData.getBoolean("supportsFunctionBreakpoints") : null;
    }

    public Capabilities setSupportsFunctionBreakpoints(Boolean supportsFunctionBreakpoints) {
        jsonData.putOpt("supportsFunctionBreakpoints", supportsFunctionBreakpoints);
        return this;
    }

    /**
     * The debug adapter supports conditional breakpoints.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsConditionalBreakpoints() {
        return jsonData.has("supportsConditionalBreakpoints") ? jsonData.getBoolean("supportsConditionalBreakpoints") : null;
    }

    public Capabilities setSupportsConditionalBreakpoints(Boolean supportsConditionalBreakpoints) {
        jsonData.putOpt("supportsConditionalBreakpoints", supportsConditionalBreakpoints);
        return this;
    }

    /**
     * The debug adapter supports breakpoints that break execution after a specified number of hits.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsHitConditionalBreakpoints() {
        return jsonData.has("supportsHitConditionalBreakpoints") ? jsonData.getBoolean("supportsHitConditionalBreakpoints") : null;
    }

    public Capabilities setSupportsHitConditionalBreakpoints(Boolean supportsHitConditionalBreakpoints) {
        jsonData.putOpt("supportsHitConditionalBreakpoints", supportsHitConditionalBreakpoints);
        return this;
    }

    /**
     * The debug adapter supports a (side effect free) evaluate request for data hovers.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsEvaluateForHovers() {
        return jsonData.has("supportsEvaluateForHovers") ? jsonData.getBoolean("supportsEvaluateForHovers") : null;
    }

    public Capabilities setSupportsEvaluateForHovers(Boolean supportsEvaluateForHovers) {
        jsonData.putOpt("supportsEvaluateForHovers", supportsEvaluateForHovers);
        return this;
    }

    /**
     * Available filters or options for the setExceptionBreakpoints request.
     */
    public List<ExceptionBreakpointsFilter> getExceptionBreakpointFilters() {
        final JSONArray json = jsonData.optJSONArray("exceptionBreakpointFilters");
        if (json == null) {
            return null;
        }
        final List<ExceptionBreakpointsFilter> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new ExceptionBreakpointsFilter(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Capabilities setExceptionBreakpointFilters(List<ExceptionBreakpointsFilter> exceptionBreakpointFilters) {
        if (exceptionBreakpointFilters != null) {
            final JSONArray json = new JSONArray();
            for (ExceptionBreakpointsFilter exceptionBreakpointsFilter : exceptionBreakpointFilters) {
                json.put(exceptionBreakpointsFilter.jsonData);
            }
            jsonData.put("exceptionBreakpointFilters", json);
        }
        return this;
    }

    /**
     * The debug adapter supports stepping back via the 'stepBack' and 'reverseContinue' requests.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsStepBack() {
        return jsonData.has("supportsStepBack") ? jsonData.getBoolean("supportsStepBack") : null;
    }

    public Capabilities setSupportsStepBack(Boolean supportsStepBack) {
        jsonData.putOpt("supportsStepBack", supportsStepBack);
        return this;
    }

    /**
     * The debug adapter supports setting a variable to a value.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsSetVariable() {
        return jsonData.has("supportsSetVariable") ? jsonData.getBoolean("supportsSetVariable") : null;
    }

    public Capabilities setSupportsSetVariable(Boolean supportsSetVariable) {
        jsonData.putOpt("supportsSetVariable", supportsSetVariable);
        return this;
    }

    /**
     * The debug adapter supports restarting a frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsRestartFrame() {
        return jsonData.has("supportsRestartFrame") ? jsonData.getBoolean("supportsRestartFrame") : null;
    }

    public Capabilities setSupportsRestartFrame(Boolean supportsRestartFrame) {
        jsonData.putOpt("supportsRestartFrame", supportsRestartFrame);
        return this;
    }

    /**
     * The debug adapter supports the 'gotoTargets' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsGotoTargetsRequest() {
        return jsonData.has("supportsGotoTargetsRequest") ? jsonData.getBoolean("supportsGotoTargetsRequest") : null;
    }

    public Capabilities setSupportsGotoTargetsRequest(Boolean supportsGotoTargetsRequest) {
        jsonData.putOpt("supportsGotoTargetsRequest", supportsGotoTargetsRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'stepInTargets' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsStepInTargetsRequest() {
        return jsonData.has("supportsStepInTargetsRequest") ? jsonData.getBoolean("supportsStepInTargetsRequest") : null;
    }

    public Capabilities setSupportsStepInTargetsRequest(Boolean supportsStepInTargetsRequest) {
        jsonData.putOpt("supportsStepInTargetsRequest", supportsStepInTargetsRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'completions' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsCompletionsRequest() {
        return jsonData.has("supportsCompletionsRequest") ? jsonData.getBoolean("supportsCompletionsRequest") : null;
    }

    public Capabilities setSupportsCompletionsRequest(Boolean supportsCompletionsRequest) {
        jsonData.putOpt("supportsCompletionsRequest", supportsCompletionsRequest);
        return this;
    }

    /**
     * The set of characters that should trigger completion in a REPL. If not specified, the UI
     * should assume the '.' character.
     */
    public List<String> getCompletionTriggerCharacters() {
        final JSONArray json = jsonData.optJSONArray("completionTriggerCharacters");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public Capabilities setCompletionTriggerCharacters(List<String> completionTriggerCharacters) {
        if (completionTriggerCharacters != null) {
            final JSONArray json = new JSONArray();
            for (String string : completionTriggerCharacters) {
                json.put(string);
            }
            jsonData.put("completionTriggerCharacters", json);
        }
        return this;
    }

    /**
     * The debug adapter supports the 'modules' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsModulesRequest() {
        return jsonData.has("supportsModulesRequest") ? jsonData.getBoolean("supportsModulesRequest") : null;
    }

    public Capabilities setSupportsModulesRequest(Boolean supportsModulesRequest) {
        jsonData.putOpt("supportsModulesRequest", supportsModulesRequest);
        return this;
    }

    /**
     * The set of additional module information exposed by the debug adapter.
     */
    public List<ColumnDescriptor> getAdditionalModuleColumns() {
        final JSONArray json = jsonData.optJSONArray("additionalModuleColumns");
        if (json == null) {
            return null;
        }
        final List<ColumnDescriptor> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new ColumnDescriptor(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Capabilities setAdditionalModuleColumns(List<ColumnDescriptor> additionalModuleColumns) {
        if (additionalModuleColumns != null) {
            final JSONArray json = new JSONArray();
            for (ColumnDescriptor columnDescriptor : additionalModuleColumns) {
                json.put(columnDescriptor.jsonData);
            }
            jsonData.put("additionalModuleColumns", json);
        }
        return this;
    }

    /**
     * Checksum algorithms supported by the debug adapter.
     */
    public List<String> getSupportedChecksumAlgorithms() {
        final JSONArray json = jsonData.optJSONArray("supportedChecksumAlgorithms");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public Capabilities setSupportedChecksumAlgorithms(List<String> supportedChecksumAlgorithms) {
        if (supportedChecksumAlgorithms != null) {
            final JSONArray json = new JSONArray();
            for (String string : supportedChecksumAlgorithms) {
                json.put(string);
            }
            jsonData.put("supportedChecksumAlgorithms", json);
        }
        return this;
    }

    /**
     * The debug adapter supports the 'restart' request. In this case a client should not implement
     * 'restart' by terminating and relaunching the adapter but by calling the RestartRequest.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsRestartRequest() {
        return jsonData.has("supportsRestartRequest") ? jsonData.getBoolean("supportsRestartRequest") : null;
    }

    public Capabilities setSupportsRestartRequest(Boolean supportsRestartRequest) {
        jsonData.putOpt("supportsRestartRequest", supportsRestartRequest);
        return this;
    }

    /**
     * The debug adapter supports 'exceptionOptions' on the setExceptionBreakpoints request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsExceptionOptions() {
        return jsonData.has("supportsExceptionOptions") ? jsonData.getBoolean("supportsExceptionOptions") : null;
    }

    public Capabilities setSupportsExceptionOptions(Boolean supportsExceptionOptions) {
        jsonData.putOpt("supportsExceptionOptions", supportsExceptionOptions);
        return this;
    }

    /**
     * The debug adapter supports a 'format' attribute on the stackTraceRequest, variablesRequest,
     * and evaluateRequest.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsValueFormattingOptions() {
        return jsonData.has("supportsValueFormattingOptions") ? jsonData.getBoolean("supportsValueFormattingOptions") : null;
    }

    public Capabilities setSupportsValueFormattingOptions(Boolean supportsValueFormattingOptions) {
        jsonData.putOpt("supportsValueFormattingOptions", supportsValueFormattingOptions);
        return this;
    }

    /**
     * The debug adapter supports the 'exceptionInfo' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsExceptionInfoRequest() {
        return jsonData.has("supportsExceptionInfoRequest") ? jsonData.getBoolean("supportsExceptionInfoRequest") : null;
    }

    public Capabilities setSupportsExceptionInfoRequest(Boolean supportsExceptionInfoRequest) {
        jsonData.putOpt("supportsExceptionInfoRequest", supportsExceptionInfoRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'terminateDebuggee' attribute on the 'disconnect' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportTerminateDebuggee() {
        return jsonData.has("supportTerminateDebuggee") ? jsonData.getBoolean("supportTerminateDebuggee") : null;
    }

    public Capabilities setSupportTerminateDebuggee(Boolean supportTerminateDebuggee) {
        jsonData.putOpt("supportTerminateDebuggee", supportTerminateDebuggee);
        return this;
    }

    /**
     * The debug adapter supports the delayed loading of parts of the stack, which requires that
     * both the 'startFrame' and 'levels' arguments and the 'totalFrames' result of the 'StackTrace'
     * request are supported.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsDelayedStackTraceLoading() {
        return jsonData.has("supportsDelayedStackTraceLoading") ? jsonData.getBoolean("supportsDelayedStackTraceLoading") : null;
    }

    public Capabilities setSupportsDelayedStackTraceLoading(Boolean supportsDelayedStackTraceLoading) {
        jsonData.putOpt("supportsDelayedStackTraceLoading", supportsDelayedStackTraceLoading);
        return this;
    }

    /**
     * The debug adapter supports the 'loadedSources' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsLoadedSourcesRequest() {
        return jsonData.has("supportsLoadedSourcesRequest") ? jsonData.getBoolean("supportsLoadedSourcesRequest") : null;
    }

    public Capabilities setSupportsLoadedSourcesRequest(Boolean supportsLoadedSourcesRequest) {
        jsonData.putOpt("supportsLoadedSourcesRequest", supportsLoadedSourcesRequest);
        return this;
    }

    /**
     * The debug adapter supports logpoints by interpreting the 'logMessage' attribute of the
     * SourceBreakpoint.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsLogPoints() {
        return jsonData.has("supportsLogPoints") ? jsonData.getBoolean("supportsLogPoints") : null;
    }

    public Capabilities setSupportsLogPoints(Boolean supportsLogPoints) {
        jsonData.putOpt("supportsLogPoints", supportsLogPoints);
        return this;
    }

    /**
     * The debug adapter supports the 'terminateThreads' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsTerminateThreadsRequest() {
        return jsonData.has("supportsTerminateThreadsRequest") ? jsonData.getBoolean("supportsTerminateThreadsRequest") : null;
    }

    public Capabilities setSupportsTerminateThreadsRequest(Boolean supportsTerminateThreadsRequest) {
        jsonData.putOpt("supportsTerminateThreadsRequest", supportsTerminateThreadsRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'setExpression' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsSetExpression() {
        return jsonData.has("supportsSetExpression") ? jsonData.getBoolean("supportsSetExpression") : null;
    }

    public Capabilities setSupportsSetExpression(Boolean supportsSetExpression) {
        jsonData.putOpt("supportsSetExpression", supportsSetExpression);
        return this;
    }

    /**
     * The debug adapter supports the 'terminate' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsTerminateRequest() {
        return jsonData.has("supportsTerminateRequest") ? jsonData.getBoolean("supportsTerminateRequest") : null;
    }

    public Capabilities setSupportsTerminateRequest(Boolean supportsTerminateRequest) {
        jsonData.putOpt("supportsTerminateRequest", supportsTerminateRequest);
        return this;
    }

    /**
     * The debug adapter supports data breakpoints.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsDataBreakpoints() {
        return jsonData.has("supportsDataBreakpoints") ? jsonData.getBoolean("supportsDataBreakpoints") : null;
    }

    public Capabilities setSupportsDataBreakpoints(Boolean supportsDataBreakpoints) {
        jsonData.putOpt("supportsDataBreakpoints", supportsDataBreakpoints);
        return this;
    }

    /**
     * The debug adapter supports the 'readMemory' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsReadMemoryRequest() {
        return jsonData.has("supportsReadMemoryRequest") ? jsonData.getBoolean("supportsReadMemoryRequest") : null;
    }

    public Capabilities setSupportsReadMemoryRequest(Boolean supportsReadMemoryRequest) {
        jsonData.putOpt("supportsReadMemoryRequest", supportsReadMemoryRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'disassemble' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsDisassembleRequest() {
        return jsonData.has("supportsDisassembleRequest") ? jsonData.getBoolean("supportsDisassembleRequest") : null;
    }

    public Capabilities setSupportsDisassembleRequest(Boolean supportsDisassembleRequest) {
        jsonData.putOpt("supportsDisassembleRequest", supportsDisassembleRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'cancel' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsCancelRequest() {
        return jsonData.has("supportsCancelRequest") ? jsonData.getBoolean("supportsCancelRequest") : null;
    }

    public Capabilities setSupportsCancelRequest(Boolean supportsCancelRequest) {
        jsonData.putOpt("supportsCancelRequest", supportsCancelRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'breakpointLocations' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsBreakpointLocationsRequest() {
        return jsonData.has("supportsBreakpointLocationsRequest") ? jsonData.getBoolean("supportsBreakpointLocationsRequest") : null;
    }

    public Capabilities setSupportsBreakpointLocationsRequest(Boolean supportsBreakpointLocationsRequest) {
        jsonData.putOpt("supportsBreakpointLocationsRequest", supportsBreakpointLocationsRequest);
        return this;
    }

    /**
     * The debug adapter supports the 'clipboard' context value in the 'evaluate' request.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSupportsClipboardContext() {
        return jsonData.has("supportsClipboardContext") ? jsonData.getBoolean("supportsClipboardContext") : null;
    }

    public Capabilities setSupportsClipboardContext(Boolean supportsClipboardContext) {
        jsonData.putOpt("supportsClipboardContext", supportsClipboardContext);
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
        Capabilities other = (Capabilities) obj;
        if (!Objects.equals(this.getSupportsConfigurationDoneRequest(), other.getSupportsConfigurationDoneRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsFunctionBreakpoints(), other.getSupportsFunctionBreakpoints())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsConditionalBreakpoints(), other.getSupportsConditionalBreakpoints())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsHitConditionalBreakpoints(), other.getSupportsHitConditionalBreakpoints())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsEvaluateForHovers(), other.getSupportsEvaluateForHovers())) {
            return false;
        }
        if (!Objects.equals(this.getExceptionBreakpointFilters(), other.getExceptionBreakpointFilters())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsStepBack(), other.getSupportsStepBack())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsSetVariable(), other.getSupportsSetVariable())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsRestartFrame(), other.getSupportsRestartFrame())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsGotoTargetsRequest(), other.getSupportsGotoTargetsRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsStepInTargetsRequest(), other.getSupportsStepInTargetsRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsCompletionsRequest(), other.getSupportsCompletionsRequest())) {
            return false;
        }
        if (!Objects.equals(this.getCompletionTriggerCharacters(), other.getCompletionTriggerCharacters())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsModulesRequest(), other.getSupportsModulesRequest())) {
            return false;
        }
        if (!Objects.equals(this.getAdditionalModuleColumns(), other.getAdditionalModuleColumns())) {
            return false;
        }
        if (!Objects.equals(this.getSupportedChecksumAlgorithms(), other.getSupportedChecksumAlgorithms())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsRestartRequest(), other.getSupportsRestartRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsExceptionOptions(), other.getSupportsExceptionOptions())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsValueFormattingOptions(), other.getSupportsValueFormattingOptions())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsExceptionInfoRequest(), other.getSupportsExceptionInfoRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportTerminateDebuggee(), other.getSupportTerminateDebuggee())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsDelayedStackTraceLoading(), other.getSupportsDelayedStackTraceLoading())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsLoadedSourcesRequest(), other.getSupportsLoadedSourcesRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsLogPoints(), other.getSupportsLogPoints())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsTerminateThreadsRequest(), other.getSupportsTerminateThreadsRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsSetExpression(), other.getSupportsSetExpression())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsTerminateRequest(), other.getSupportsTerminateRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsDataBreakpoints(), other.getSupportsDataBreakpoints())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsReadMemoryRequest(), other.getSupportsReadMemoryRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsDisassembleRequest(), other.getSupportsDisassembleRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsCancelRequest(), other.getSupportsCancelRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsBreakpointLocationsRequest(), other.getSupportsBreakpointLocationsRequest())) {
            return false;
        }
        if (!Objects.equals(this.getSupportsClipboardContext(), other.getSupportsClipboardContext())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getSupportsConfigurationDoneRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsConfigurationDoneRequest());
        }
        if (this.getSupportsFunctionBreakpoints() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsFunctionBreakpoints());
        }
        if (this.getSupportsConditionalBreakpoints() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsConditionalBreakpoints());
        }
        if (this.getSupportsHitConditionalBreakpoints() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsHitConditionalBreakpoints());
        }
        if (this.getSupportsEvaluateForHovers() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsEvaluateForHovers());
        }
        if (this.getExceptionBreakpointFilters() != null) {
            hash = 97 * hash + Objects.hashCode(this.getExceptionBreakpointFilters());
        }
        if (this.getSupportsStepBack() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsStepBack());
        }
        if (this.getSupportsSetVariable() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsSetVariable());
        }
        if (this.getSupportsRestartFrame() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsRestartFrame());
        }
        if (this.getSupportsGotoTargetsRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsGotoTargetsRequest());
        }
        if (this.getSupportsStepInTargetsRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsStepInTargetsRequest());
        }
        if (this.getSupportsCompletionsRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsCompletionsRequest());
        }
        if (this.getCompletionTriggerCharacters() != null) {
            hash = 97 * hash + Objects.hashCode(this.getCompletionTriggerCharacters());
        }
        if (this.getSupportsModulesRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsModulesRequest());
        }
        if (this.getAdditionalModuleColumns() != null) {
            hash = 97 * hash + Objects.hashCode(this.getAdditionalModuleColumns());
        }
        if (this.getSupportedChecksumAlgorithms() != null) {
            hash = 97 * hash + Objects.hashCode(this.getSupportedChecksumAlgorithms());
        }
        if (this.getSupportsRestartRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsRestartRequest());
        }
        if (this.getSupportsExceptionOptions() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsExceptionOptions());
        }
        if (this.getSupportsValueFormattingOptions() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsValueFormattingOptions());
        }
        if (this.getSupportsExceptionInfoRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsExceptionInfoRequest());
        }
        if (this.getSupportTerminateDebuggee() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportTerminateDebuggee());
        }
        if (this.getSupportsDelayedStackTraceLoading() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsDelayedStackTraceLoading());
        }
        if (this.getSupportsLoadedSourcesRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsLoadedSourcesRequest());
        }
        if (this.getSupportsLogPoints() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsLogPoints());
        }
        if (this.getSupportsTerminateThreadsRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsTerminateThreadsRequest());
        }
        if (this.getSupportsSetExpression() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsSetExpression());
        }
        if (this.getSupportsTerminateRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsTerminateRequest());
        }
        if (this.getSupportsDataBreakpoints() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsDataBreakpoints());
        }
        if (this.getSupportsReadMemoryRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsReadMemoryRequest());
        }
        if (this.getSupportsDisassembleRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsDisassembleRequest());
        }
        if (this.getSupportsCancelRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsCancelRequest());
        }
        if (this.getSupportsBreakpointLocationsRequest() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsBreakpointLocationsRequest());
        }
        if (this.getSupportsClipboardContext() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSupportsClipboardContext());
        }
        return hash;
    }

    public static Capabilities create() {
        final JSONObject json = new JSONObject();
        return new Capabilities(json);
    }
}
