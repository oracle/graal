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
package com.oracle.truffle.tools.chromeinspector;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.LiteralBuilder;
import com.oracle.truffle.api.source.SourceSection;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext.NoSuspendedThreadException;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.domains.RuntimeDomain;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.instrument.Enabler;
import com.oracle.truffle.tools.chromeinspector.instrument.OutputConsumerInstrument;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession.CommandPostProcessor;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.CustomPreview;
import com.oracle.truffle.tools.chromeinspector.types.ExceptionDetails;
import com.oracle.truffle.tools.chromeinspector.types.InternalPropertyDescriptor;
import com.oracle.truffle.tools.chromeinspector.types.Location;
import com.oracle.truffle.tools.chromeinspector.types.PropertyDescriptor;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

import org.graalvm.collections.Pair;

public final class InspectorRuntime extends RuntimeDomain {

    private static final Pattern WHITESPACES_PATTERN = Pattern.compile("\\s+");
    private static final String FUNCTION_COMPLETION = eliminateWhiteSpaces("function getCompletions(");
    private static final String FUNCTION_SET_PROPERTY = eliminateWhiteSpaces("function(a, b) { this[a] = b; }");
    private static final String FUNCTION_GET_ARRAY_NUM_PROPS = eliminateWhiteSpaces("function() { return [this.length, Object.keys(this).length - this.length + 2]; }");
    private static final String FUNCTION_GET_BUFFER_NUM_PROPS = eliminateWhiteSpaces("function() { return [this.length, 0]; }");
    private static final String FUNCTION_GET_COLLECTION_NUM_PROPS = eliminateWhiteSpaces("function() { return [0, Object.keys(this).length + 1]; }");
    // Generic matcher of following function:
    // function invokeGetter(arrayStr){let result=this;const properties=JSON.parse(arrayStr);
    // for(let i=0,n=properties.length;i<n;++i)
    // result=result[properties[i]];return result;}
    private static final Pattern FUNCTION_GETTER_PATTERN1 = Pattern.compile(
                    "function\\s+(?<invokeGetter>\\w+)\\((?<arrayStr>\\w+)\\)\\s*\\{\\s*\\w+\\s+(?<result>\\w+)\\s*=\\s*this;\\s*\\w*\\s*(?<properties>\\w+)\\s*=\\s*JSON.parse\\(\\k<arrayStr>\\);" +
                                    "\\s*for\\s*\\(\\w+\\s+(?<i>\\w+)\\s*=.*(\\+\\+\\k<i>|\\k<i>\\+\\+|\\-\\-\\k<i>|\\k<i>\\-\\-)\\)\\s*\\{?\\s*\\k<result>\\s*=\\s*\\k<result>\\[\\k<properties>\\[\\k<i>\\]\\];\\s*\\}?" +
                                    "\\s*return\\s+\\k<result>;\\s*\\}");
    // Generic matcher of following function:
    // function remoteFunction(propName) { return this[propName]; }
    private static final Pattern FUNCTION_GETTER_PATTERN2 = Pattern.compile(
                    "function\\s+(?<invokeGetter>\\w+)\\((?<propName>\\w+)\\)\\s*\\{\\s*return\\s+this\\[\\k<propName>\\];\\s*\\}");
    // Generic matcher of following function:
    // function getIndexedVariables(start, count) {var result = [];
    // for (var i = start; i < (start + count); i++)
    // result[i] = this[i];
    // return result;}
    private static final Pattern FUNCTION_GET_INDEXED_VARS_PATTERN = Pattern.compile(
                    "function\\s+(?<getIndexedVariables>\\w+)\\((?<start>\\w+),\\s*(?<count>\\w+)\\)\\s*\\{\\s*\\w+\\s+(?<result>\\w+)\\s*=\\s*\\[\\];" +
                                    "\\s*for\\s*\\(\\w+\\s+(?<i>\\w+)\\s*=\\s*\\k<start>;\\s*\\k<i>\\s*\\<\\s*\\(\\k<start>\\s*\\+\\s*\\k<count>\\);\\s*(\\+\\+\\k<i>|\\k<i>\\+\\+)\\)" +
                                    "\\s*\\{?\\s*\\k<result>\\[\\k<i>\\]\\s*=\\s*this\\[\\k<i>\\];\\s*\\}?" +
                                    "\\s*return\\s+\\k<result>;\\s*\\}");
    // Generic matcher of following function:
    // function getNamedVariablesFn(start, count) {var result = [];
    // var ownProps = Object.getOwnPropertyNames(this);
    // for (var i = start; i < (start + count); i++)
    // result[i] = ownProps[i];
    // return result;}
    private static final Pattern FUNCTION_GET_NAMED_VARS_PATTERN = Pattern.compile(
                    "function\\s+(?<getNamedVariables>\\w+)\\((?<start>\\w+),\\s*(?<count>\\w+)\\)\\s*\\{\\s*\\w+\\s+(?<result>\\w+)\\s*=\\s*\\[\\];" +
                                    "\\s*\\w+\\s+(?<ownProps>\\w+)\\s*=\\s*Object.getOwnPropertyNames\\s*\\(this\\);" +
                                    "\\s*for\\s*\\(\\w+\\s+(?<i>\\w+)\\s*=\\s*\\k<start>;\\s*\\k<i>\\s*\\<\\s*\\(\\k<start>\\s*\\+\\s*\\k<count>\\);\\s*(\\+\\+\\k<i>|\\k<i>\\+\\+)\\)" +
                                    "\\s*\\{?\\s*\\k<result>\\[\\k<i>\\]\\s*=\\s*\\k<ownProps>\\[\\k<i>\\];\\s*\\}?" +
                                    "\\s*return\\s+\\k<result>;\\s*\\}");

    private final InspectorExecutionContext context;
    private InspectorExecutionContext.Listener contextListener;
    private ScriptsHandler slh;
    private Enabler enabler;

    public InspectorRuntime(InspectorExecutionContext context) {
        this.context = context;
    }

    @Override
    public void doEnable() {
        assert contextListener == null;
        slh = context.acquireScriptsHandler();
        contextListener = new ContextListener();
        context.addListener(contextListener);
        InstrumentInfo instrumentInfo = context.getEnv().getInstruments().get(OutputConsumerInstrument.ID);
        enabler = context.getEnv().lookup(instrumentInfo, Enabler.class);
        enabler.enable();
        OutputHandler oh = context.getEnv().lookup(instrumentInfo, OutputHandler.Provider.class).getOutputHandler();
        oh.setOutListener(new ConsoleOutputListener("log"));
        oh.setErrListener(new ConsoleOutputListener("error"));
    }

    @Override
    public void doDisable() {
        assert contextListener != null;
        context.removeListener(contextListener);
        contextListener = null;
        enabler.disable();
        enabler = null;
        slh = null;
        context.releaseScriptsHandler();
    }

    private Source createSource(String expression, String sourceURL) {
        String language = context.getLastLanguage();
        String mimeType = context.getLastMimeType();
        String name = (sourceURL != null) ? sourceURL : "eval";
        if (language == null) {
            // legacy support where language may be null
            language = Source.findLanguage(mimeType);
        }
        LiteralBuilder builder = Source.newBuilder(language, expression, name).name(name).mimeType(mimeType);
        if (sourceURL != null && !sourceURL.isEmpty()) {
            URI ownUri = null;
            try {
                ownUri = new URI(sourceURL);
            } catch (URISyntaxException usex) {
            }
            if (ownUri != null) {
                builder.uri(ownUri);
            }
        }
        return builder.build();
    }

    @Override
    public Params compileScript(String expression, String sourceURL, boolean persistScript, long executionContextId) throws CommandProcessException {
        if (expression == null) {
            throw new CommandProcessException("An expression required.");
        }
        JSONObject ret = new JSONObject();
        Source source = createSource(expression, sourceURL);
        boolean parsed = false;
        String[] exceptionText = new String[1];
        if (context.getSuspendedInfo() != null) {
            try {
                parsed = context.executeInSuspendThread(new SuspendThreadExecutable<Boolean>() {
                    @Override
                    public Boolean executeCommand() throws CommandProcessException {
                        LanguageInfo languageInfo = context.getSuspendedInfo().getSuspendedEvent().getTopStackFrame().getLanguage();
                        if (languageInfo == null || !languageInfo.isInteractive()) {
                            exceptionText[0] = InspectorDebugger.getEvalNonInteractiveMessage();
                            return false;
                        }
                        try {
                            context.getEnv().parse(source);
                            return true;
                        } catch (ThreadDeath td) {
                            throw td;
                        } catch (Throwable ex) {
                            // Didn't manage to parse this
                            exceptionText[0] = ex.getLocalizedMessage();
                            return false;
                        }
                    }

                    @Override
                    public Boolean processException(DebugException ex) {
                        fillExceptionDetails(ret, ex, false);
                        return false;
                    }
                });
            } catch (NoSuspendedThreadException ex) {
                exceptionText[0] = ex.getLocalizedMessage();
            }
        } else {
            // Parse on the current thread will fail most likely due to a lack of context
            parsed = false;
            exceptionText[0] = "<Not suspended>";
        }
        if (parsed && persistScript) {
            int id = slh.assureLoaded(source);
            if (id != -1) {
                ret.put("scriptId", Integer.toString(id));
            }
        }
        if (exceptionText[0] != null) {
            fillExceptionDetails(ret, exceptionText[0], false);
        }
        return new Params(ret);
    }

    @Override
    public Params evaluate(String expression, String objectGroup, boolean includeCommandLineAPI, boolean silent, int contextId, boolean returnByValue, boolean generatePreview, boolean awaitPromise)
                    throws CommandProcessException {
        if (expression == null) {
            throw new CommandProcessException("An expression required.");
        }
        JSONObject json = new JSONObject();
        DebuggerSuspendedInfo suspendedInfo = context.getSuspendedInfo();
        if (suspendedInfo != null) {
            try {
                context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                    @Override
                    public Void executeCommand() throws CommandProcessException {
                        suspendedInfo.lastEvaluatedValue.set(null);
                        LanguageInfo languageInfo = context.getSuspendedInfo().getSuspendedEvent().getTopStackFrame().getLanguage();
                        if (languageInfo == null || !languageInfo.isInteractive()) {
                            fillExceptionDetails(json, InspectorDebugger.getEvalNonInteractiveMessage(), generatePreview);
                            return null;
                        }
                        JSONObject result;
                        DebugValue value = null;
                        if (suspendedInfo.getCallFrames().length > 0) {
                            value = InspectorDebugger.getVarValue(expression, suspendedInfo.getCallFrames()[0]);
                        }
                        if (value == null) {
                            value = suspendedInfo.getSuspendedEvent().getTopStackFrame().eval(expression);
                            suspendedInfo.refreshFrames();
                        }
                        if (returnByValue) {
                            result = RemoteObject.createJSONResultValue(value, context.areToStringSideEffectsAllowed(), context.getErr());
                        } else {
                            RemoteObject ro = new RemoteObject(value, generatePreview, context);
                            context.getRemoteObjectsHandler().register(ro, objectGroup);
                            result = ro.toJSON();
                            if (!ro.isReplicable()) {
                                suspendedInfo.lastEvaluatedValue.set(Pair.create(value, ro.getRawValue()));
                            }
                        }
                        json.put("result", result);
                        return null;
                    }

                    @Override
                    public Void processException(DebugException ex) {
                        fillExceptionDetails(json, ex, generatePreview);
                        return null;
                    }
                });
            } catch (NoSuspendedThreadException ex) {
                fillExceptionDetails(json, ex.getLocalizedMessage(), generatePreview);
            }
        } else {
            fillExceptionDetails(json, "<Not suspended>", generatePreview);
        }
        return new Params(json);
    }

    @Override
    public Params getProperties(String objectId, boolean ownProperties, boolean accessorPropertiesOnly, boolean generatePreview) throws CommandProcessException {
        if (objectId == null) {
            throw new CommandProcessException("An objectId required.");
        }
        RemoteObject object = context.getRemoteObjectsHandler().getRemote(objectId);
        String objectGroup = context.getRemoteObjectsHandler().getObjectGroupOf(objectId);
        JSONObject json = new JSONObject();
        if (object != null) {
            DebugValue value = object.getDebugValue();
            RemoteObject.IndexRange indexRange = object.getIndexRange();
            try {
                if (value != null) {
                    context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                        @Override
                        public Void executeCommand() throws CommandProcessException {
                            Collection<DebugValue> properties = value.getProperties();
                            if (properties == null) {
                                properties = Collections.emptyList();
                            } else if (indexRange != null && indexRange.isNamed()) {
                                List<DebugValue> list = new ArrayList<>(properties);
                                properties = list.subList(indexRange.start(), indexRange.end());
                            }
                            Collection<DebugValue> array;
                            if (!value.isArray()) {
                                array = Collections.emptyList();
                            } else if (indexRange != null && !indexRange.isNamed()) {
                                List<DebugValue> arr = value.getArray();
                                array = arr.subList(indexRange.start(), indexRange.end());
                            } else {
                                array = value.getArray();
                            }
                            putResultProperties(json, value, properties, array, generatePreview, objectGroup);
                            return null;
                        }

                        @Override
                        public Void processException(DebugException ex) {
                            fillExceptionDetails(json, ex, generatePreview);
                            return null;
                        }
                    });
                } else {
                    final DebugScope scope = object.getScope();
                    context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                        @Override
                        public Void executeCommand() throws CommandProcessException {
                            Collection<DebugValue> properties = new ArrayList<>();
                            for (DebugValue p : scope.getDeclaredValues()) {
                                properties.add(p);
                            }
                            putResultProperties(json, null, properties, Collections.emptyList(), generatePreview, objectGroup);
                            return null;
                        }

                        @Override
                        public Void processException(DebugException ex) {
                            fillExceptionDetails(json, ex, generatePreview);
                            return null;
                        }
                    });
                }
            } catch (NoSuspendedThreadException ex) {
                // Not suspended, no properties
                json.put("result", new JSONArray());
            }
        }
        return new Params(json);
    }

    private void putResultProperties(JSONObject json, DebugValue value, Collection<DebugValue> properties, Collection<DebugValue> arrayElements, boolean generatePreview, String objectGroup) {
        final String functionLocation = "[[FunctionLocation]]";
        JSONArray result = new JSONArray();
        JSONArray internals = new JSONArray();
        boolean hasArray = !arrayElements.isEmpty();
        HashSet<String> storedPropertyNames = (hasArray && properties != null) ? new HashSet<>(properties.size()) : null;
        DebugException exception = null;
        String nameExc = null;
        // Test functionLocation for executable values only
        boolean hasFunctionLocation = value == null || !value.canExecute();
        try {
            boolean isJS = false;
            if (properties != null) {
                LanguageInfo language = (value != null) ? value.getOriginalLanguage() : null;
                isJS = LanguageChecks.isJS(language);
                Iterator<DebugValue> propertiesIterator = properties.iterator();
                while (propertiesIterator.hasNext()) {
                    DebugValue v = null;
                    try {
                        v = propertiesIterator.next();
                        if (v.isReadable()) {
                            if (!v.isInternal()) {
                                result.put(createPropertyJSON(v, generatePreview, objectGroup));
                                if (storedPropertyNames != null) {
                                    storedPropertyNames.add(v.getName());
                                }
                            } else {
                                internals.put(createPropertyJSON(v, generatePreview, objectGroup));
                            }
                            if (!hasFunctionLocation && functionLocation.equals(v.getName())) {
                                hasFunctionLocation = true;
                            }
                        }
                    } catch (DebugException ex) {
                        if (exception == null) {
                            exception = ex;
                            nameExc = (v != null) ? v.getName() : "<unknown>";
                        }
                    }
                }
            }
            int i = 0;
            for (DebugValue v : arrayElements) {
                String name = Integer.toString(i++);
                try {
                    if (v.isReadable() && (storedPropertyNames == null || !storedPropertyNames.contains(name))) {
                        result.put(createPropertyJSON(v, name, generatePreview, objectGroup));
                    }
                } catch (DebugException ex) {
                    if (exception == null) {
                        exception = ex;
                        nameExc = name;
                    }
                }
            }
            if (isJS) {
                // Add __proto__ when in JavaScript:
                DebugValue prototype = value.getProperty("__proto__");
                if (prototype != null && !prototype.isNull()) {
                    result.put(createPropertyJSON(prototype, null, generatePreview, true, false, objectGroup));
                }
            }
        } catch (DebugException ex) {
            // From property iterators, etc.
            if (exception == null) {
                exception = ex;
            }
        }
        if (!hasFunctionLocation) {
            SourceSection sourceLocation = null;
            try {
                sourceLocation = value.getSourceLocation();
            } catch (DebugException ex) {
                // From property iterators, etc.
                if (exception == null) {
                    exception = ex;
                }
            }
            if (sourceLocation != null) {
                int scriptId = slh.getScriptId(sourceLocation.getSource());
                if (scriptId >= 0) {
                    // {"name":"[[FunctionLocation]]","value":{"type":"object","subtype":"internal#location","value":{"scriptId":"87","lineNumber":17,"columnNumber":26},"description":"Object"}}
                    JSONObject location = new JSONObject();
                    location.put("name", functionLocation);
                    JSONObject locationValue = new JSONObject();
                    locationValue.put("type", "object");
                    locationValue.put("subtype", "internal#location");
                    locationValue.put("description", "Object");
                    locationValue.put("value", new Location(scriptId, sourceLocation.getStartLine(), sourceLocation.getStartColumn()).toJSON());
                    location.put("value", locationValue);
                    internals.put(location);
                }
            }
        }
        json.put("result", result);
        json.put("internalProperties", internals);
        if (exception != null) {
            fillExceptionDetails(json, exception, generatePreview);
            if (exception.isInternalError()) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    err.println("Exception while retrieving variable " + nameExc);
                    exception.printStackTrace(err);
                }
            }
        }
    }

    @Override
    public Params callFunctionOn(String objectId, String functionDeclaration, JSONArray arguments, boolean silent, boolean returnByValue, boolean generatePreview, boolean awaitPromise,
                    int executionContextId, String objectGroup) throws CommandProcessException {
        if (objectId == null) {
            throw new CommandProcessException("An objectId required.");
        }
        RemoteObject object = context.getRemoteObjectsHandler().getRemote(objectId);
        JSONObject json = new JSONObject();
        if (object != null) {
            DebugValue value = object.getDebugValue();
            DebugScope scope = object.getScope();
            RemoteObject.IndexRange indexRange = object.getIndexRange();
            DebuggerSuspendedInfo suspendedInfo = context.getSuspendedInfo();
            if (suspendedInfo != null) {
                try {
                    String functionTrimmed = functionDeclaration.trim();
                    String functionNoWS = eliminateWhiteSpaces(functionDeclaration);
                    context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                        @Override
                        public Void executeCommand() throws CommandProcessException {
                            JSONObject result;
                            if (functionNoWS.startsWith(FUNCTION_COMPLETION)) {
                                result = createCodecompletion(value, scope, generatePreview, context, true);
                            } else if (functionNoWS.equals(FUNCTION_SET_PROPERTY)) {
                                // Set of an array element, or object property
                                if (arguments == null || arguments.length() < 2) {
                                    throw new CommandProcessException("Insufficient number of arguments: " + (arguments != null ? arguments.length() : 0) + ", expecting: 2");
                                }
                                Object property = ((JSONObject) arguments.get(0)).get("value");
                                CallArgument newValue = CallArgument.get((JSONObject) arguments.get(1));
                                setPropertyValue(value, scope, property, newValue, suspendedInfo.lastEvaluatedValue.getAndSet(null));
                                result = new JSONObject();
                            } else if (functionNoWS.equals(FUNCTION_GET_ARRAY_NUM_PROPS)) {
                                if (!value.isArray()) {
                                    throw new CommandProcessException("Expecting an Array the function is called on.");
                                }
                                JSONArray arr = new JSONArray();
                                if (indexRange != null && !indexRange.isNamed()) {
                                    List<DebugValue> array = value.getArray();
                                    if (indexRange.start() < 0 || indexRange.end() > array.size()) {
                                        throw new CommandProcessException("Array range out of bounds.");
                                    }
                                    arr.put(indexRange.end() - indexRange.start());
                                } else {
                                    arr.put(value.getArray().size());
                                }
                                Collection<DebugValue> props = value.getProperties();
                                if (props == null) {
                                    arr.put(0);
                                } else if (indexRange != null && indexRange.isNamed()) {
                                    ArrayList<DebugValue> list = new ArrayList<>(props);
                                    if (indexRange.start() < 0 || indexRange.end() > list.size()) {
                                        throw new CommandProcessException("Named range out of bounds.");
                                    }
                                    arr.put(indexRange.end() - indexRange.start());
                                } else if (LanguageChecks.isJS(value.getOriginalLanguage())) {
                                    arr.put(props.size() + 1); // +1 for __proto__
                                } else {
                                    arr.put(props.size());
                                }
                                result = new JSONObject();
                                result.put("value", arr);
                            } else if (functionNoWS.equals(FUNCTION_GET_BUFFER_NUM_PROPS)) {
                                if (!value.isArray()) {
                                    throw new CommandProcessException("Expecting a Buffer the function is called on.");
                                }
                                JSONArray arr = new JSONArray();
                                if (indexRange != null && !indexRange.isNamed()) {
                                    List<DebugValue> array = value.getArray();
                                    if (indexRange.start() < 0 || indexRange.end() > array.size()) {
                                        throw new CommandProcessException("Array range out of bounds.");
                                    }
                                    arr.put(indexRange.end() - indexRange.start());
                                } else {
                                    arr.put(value.getArray().size());
                                }
                                if (LanguageChecks.isJS(value.getOriginalLanguage())) {
                                    arr.put(1); // +1 for __proto__
                                } else {
                                    arr.put(0);
                                }
                                result = new JSONObject();
                                result.put("value", arr);
                            } else if (functionNoWS.equals(FUNCTION_GET_COLLECTION_NUM_PROPS)) {
                                Collection<DebugValue> props = value.getProperties();
                                if (props == null) {
                                    throw new CommandProcessException("Expecting an Object the function is called on.");
                                }
                                JSONArray arr = new JSONArray();
                                arr.put(0);
                                if (indexRange != null && indexRange.isNamed()) {
                                    ArrayList<DebugValue> list = new ArrayList<>(props);
                                    if (indexRange.start() < 0 || indexRange.end() > list.size()) {
                                        throw new CommandProcessException("Named range out of bounds.");
                                    }
                                    arr.put(indexRange.end() - indexRange.start());
                                } else if (LanguageChecks.isJS(value.getOriginalLanguage())) {
                                    arr.put(props.size() + 1); // +1 for __proto__
                                } else {
                                    arr.put(props.size());
                                }
                                result = new JSONObject();
                                result.put("value", arr);
                            } else if (FUNCTION_GETTER_PATTERN1.matcher(functionTrimmed).matches()) {
                                if (arguments == null || arguments.length() < 1) {
                                    throw new CommandProcessException("Expecting an argument to invokeGetter function.");
                                }
                                String propertyNames = ((JSONObject) arguments.get(0)).getString("value");
                                JSONArray properties = new JSONArray(propertyNames);
                                DebugValue v = value;
                                for (int i = 0; i < properties.length() && (i == 0 || v != null); i++) {
                                    String propertyName = properties.getString(i);
                                    if (v != null) {
                                        v = v.getProperty(propertyName);
                                    } else {
                                        v = scope.getDeclaredValue(propertyName);
                                    }
                                }
                                result = asResult(v);
                            } else if (FUNCTION_GETTER_PATTERN2.matcher(functionTrimmed).matches()) {
                                if (arguments == null || arguments.length() < 1) {
                                    throw new CommandProcessException("Expecting an argument to invokeGetter function.");
                                }
                                String propertyName = ((JSONObject) arguments.get(0)).getString("value");
                                DebugValue p;
                                if (value != null) {
                                    p = value.getProperty(propertyName);
                                } else {
                                    p = scope.getDeclaredValue(propertyName);
                                }
                                result = asResult(p);
                            } else if (FUNCTION_GET_INDEXED_VARS_PATTERN.matcher(functionTrimmed).matches()) {
                                if (!value.isArray()) {
                                    throw new CommandProcessException("Expecting an Array the function is called on.");
                                }
                                if (arguments == null || arguments.length() < 2) {
                                    throw new CommandProcessException("Insufficient number of arguments: " + (arguments != null ? arguments.length() : 0) + ", expecting: 2");
                                }
                                int start = ((JSONObject) arguments.get(0)).getInt("value");
                                int count = ((JSONObject) arguments.get(1)).getInt("value");
                                RemoteObject ro = new RemoteObject(value, true, generatePreview, context, new RemoteObject.IndexRange(start, start + count, false));
                                context.getRemoteObjectsHandler().register(ro, objectGroup);
                                result = ro.toJSON();
                            } else if (FUNCTION_GET_NAMED_VARS_PATTERN.matcher(functionTrimmed).matches()) {
                                Collection<DebugValue> props = value.getProperties();
                                if (props == null) {
                                    throw new CommandProcessException("Expecting an Object the function is called on.");
                                }
                                if (arguments == null || arguments.length() < 2) {
                                    throw new CommandProcessException("Insufficient number of arguments: " + (arguments != null ? arguments.length() : 0) + ", expecting: 2");
                                }
                                int start = ((JSONObject) arguments.get(0)).getInt("value");
                                int count = ((JSONObject) arguments.get(1)).getInt("value");
                                RemoteObject ro = new RemoteObject(value, true, generatePreview, context, new RemoteObject.IndexRange(start, start + count, true));
                                context.getRemoteObjectsHandler().register(ro, objectGroup);
                                result = ro.toJSON();
                            } else {
                                // Process CustomPreview body:
                                if (arguments != null && arguments.length() > 0) {
                                    Object arg0 = arguments.get(0);
                                    if (arg0 instanceof JSONObject) {
                                        JSONObject argObj = (JSONObject) arg0;
                                        Object id = argObj.opt("objectId");
                                        if (id instanceof String) {
                                            DebugValue body = context.getRemoteObjectsHandler().getCustomPreviewBody((String) id);
                                            if (body != null) {
                                                // The config is not provided as an argument.
                                                // Get the cached config:
                                                DebugValue config = context.getRemoteObjectsHandler().getCustomPreviewConfig(objectId);
                                                DebugValue bodyML = (config != null) ? body.execute(object.getDebugValue(), config) : body.execute(object.getDebugValue());
                                                Object bodyjson = CustomPreview.value2JSON(bodyML, context);
                                                result = new JSONObject();
                                                result.put("type", "object");
                                                result.put("value", bodyjson);
                                                json.put("result", result);
                                                return null;
                                            }
                                        }
                                    }
                                }
                                StringBuilder code = new StringBuilder();
                                code.append("(").append(functionTrimmed).append(").apply(").append(value != null ? value.getName() : "null");
                                if (arguments != null) {
                                    code.append(",[");
                                    for (int i = 0; i < arguments.length(); i++) {
                                        JSONObject arg = arguments.getJSONObject(i);
                                        if (i > 0) {
                                            code.append(",");
                                        }
                                        Object id = arg.opt("objectId");
                                        if (id instanceof String) {
                                            RemoteObject remoteArg = context.getRemoteObjectsHandler().getRemote((String) id);
                                            if (remoteArg == null) {
                                                throw new CommandProcessException("Cannot resolve argument by its objectId: " + id);
                                            }
                                            code.append(remoteArg.getDebugValue().getName());
                                        } else {
                                            code.append(JSONObject.valueToString(arg.get("value")));
                                        }
                                    }
                                    code.append("]");
                                }
                                code.append(")");
                                DebugValue eval = suspendedInfo.getSuspendedEvent().getTopStackFrame().eval(code.toString());
                                suspendedInfo.refreshFrames();
                                result = asResult(eval);
                            }
                            json.put("result", result);
                            return null;
                        }

                        @Override
                        public Void processException(DebugException ex) {
                            fillExceptionDetails(json, ex, generatePreview);
                            return null;
                        }

                        private JSONObject asResult(DebugValue v) {
                            JSONObject result;
                            if (v == null) {
                                LanguageInfo language = suspendedInfo.getSuspendedEvent().getTopStackFrame().getLanguage();
                                result = RemoteObject.createNullObject(context.getEnv(), language).toJSON();
                            } else {
                                if (!returnByValue) {
                                    RemoteObject ro = new RemoteObject(v, true, generatePreview, context);
                                    context.getRemoteObjectsHandler().register(ro, objectGroup);
                                    result = ro.toJSON();
                                } else {
                                    result = RemoteObject.createJSONResultValue(v, context.areToStringSideEffectsAllowed(), context.getErr());
                                }
                            }
                            return result;
                        }
                    });
                } catch (NoSuspendedThreadException ex) {
                    json.put("result", new JSONObject());
                }
            }
        }
        return new Params(json);
    }

    @Override
    public void releaseObject(String objectId) {
        context.getRemoteObjectsHandler().releaseObject(objectId);
    }

    @Override
    public void releaseObjectGroup(String objectGroup) {
        context.getRemoteObjectsHandler().releaseObjectGroup(objectGroup);
    }

    private void setPropertyValue(DebugValue object, DebugScope scope, Object property, CallArgument newValue, Pair<DebugValue, Object> evaluatedValue) throws CommandProcessException {
        DebugValue propValue;
        Number index = null;
        if (object != null && object.isArray()) {
            if (property instanceof Number) {
                index = (Number) property;
            } else {
                try {
                    index = Integer.parseUnsignedInt(property.toString());
                } catch (NumberFormatException ex) {
                    // It's a String property
                }
            }
        }
        if (index != null) {
            List<DebugValue> array = object.getArray();
            int i = index.intValue();
            if (i < 0 || array.size() <= i) {
                throw new CommandProcessException("Bad array index: " + i + " array size = " + array.size());
            }
            propValue = array.get(i);
        } else {
            if (object != null) {
                propValue = object.getProperty(property.toString());
            } else {
                propValue = scope.getDeclaredValue(property.toString());
            }
            if (propValue == null) {
                throw new CommandProcessException("No property named " + property.toString() + " was found.");
            }
        }
        if (evaluatedValue != null && Objects.equals(evaluatedValue.getRight(), newValue.getPrimitiveValue())) {
            propValue.set(evaluatedValue.getLeft());
        } else {
            context.setValue(propValue, newValue);
        }
    }

    static JSONObject createCodecompletion(DebugValue value, DebugScope scope, boolean generatePreview, InspectorExecutionContext context, boolean resultItems) {
        JSONObject result = new JSONObject();
        Iterable<DebugValue> properties = null;
        try {
            if (value != null) {
                properties = value.getProperties();
            } else {
                properties = scope.getDeclaredValues();
            }
        } catch (DebugException ex) {
            fillExceptionDetails(result, ex, context, generatePreview);
            if (ex.isInternalError()) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    err.println("getProperties(" + ((value != null) ? value.getName() : scope.getName()) + ") has caused: " + ex);
                    ex.printStackTrace(err);
                }
            }
        }
        JSONArray valueArray = new JSONArray();
        JSONArray items = new JSONArray();
        if (properties != null) {
            for (DebugValue property : properties) {
                items.put(property.getName());
            }
        }
        if (resultItems) {
            JSONObject itemsObj = new JSONObject();
            itemsObj.put("items", items);
            valueArray.put(itemsObj);
        } else {
            valueArray.put(items);
        }
        result.put("type", "object");
        result.put("value", valueArray);
        return result;
    }

    private void fillExceptionDetails(JSONObject obj, DebugException ex, boolean generatePreview) {
        fillExceptionDetails(obj, ex, context, generatePreview);
    }

    static void fillExceptionDetails(JSONObject obj, DebugException ex, InspectorExecutionContext context, boolean generatePreview) {
        ExceptionDetails exceptionDetails = new ExceptionDetails(ex);
        obj.put("exceptionDetails", exceptionDetails.createJSON(context, generatePreview));
    }

    private void fillExceptionDetails(JSONObject obj, String errorMessage, boolean generatePreview) {
        ExceptionDetails exceptionDetails = new ExceptionDetails(errorMessage);
        obj.put("exceptionDetails", exceptionDetails.createJSON(context, generatePreview));
    }

    @Override
    public void runIfWaitingForDebugger(CommandPostProcessor postProcessor) {
        postProcessor.setPostProcessJob(() -> context.doRunIfWaitingForDebugger());
    }

    @Override
    public void notifyConsoleAPICalled(String type, Object text) {
        eventHandler.event(new Event("Runtime.consoleAPICalled", Params.createConsoleAPICalled(type, text, context.getId())));
    }

    @Override
    public void setCustomObjectFormatterEnabled(boolean enabled) {
        context.setCustomObjectFormatterEnabled(enabled);
    }

    private JSONObject createPropertyJSON(DebugValue v, boolean generatePreview, String objectGroup) {
        return createPropertyJSON(v, null, generatePreview, objectGroup);
    }

    private JSONObject createPropertyJSON(DebugValue v, String defaultName, boolean generatePreview, String objectGroup) {
        return createPropertyJSON(v, defaultName, generatePreview, false, true, objectGroup);
    }

    private JSONObject createPropertyJSON(DebugValue v, String defaultName, boolean generatePreview, boolean readEagerly, boolean enumerable, String objectGroup) {
        PropertyDescriptor pd;
        RemoteObject rv = new RemoteObject(v, readEagerly, generatePreview, context);
        context.getRemoteObjectsHandler().register(rv, objectGroup);
        String name = v.getName();
        if (name == null && defaultName != null) {
            name = defaultName;
        }
        if (!v.isInternal()) {
            RemoteObject getter;
            RemoteObject setter;
            if (readEagerly) {
                getter = setter = null;
            } else {
                getter = findGetter(v);
                setter = findSetter(v);
            }
            pd = new PropertyDescriptor(name, rv, v.isWritable(), getter, setter, true, enumerable, null, true, null);
            return pd.toJSON();
        } else {
            InternalPropertyDescriptor ipd = new InternalPropertyDescriptor(name, rv);
            return ipd.toJSON();
        }
    }

    private static RemoteObject findGetter(DebugValue v) {
        if (!v.hasReadSideEffects()) {
            return null;
        }
        return RemoteObject.createSimpleObject("function", "Function", "");
    }

    private static RemoteObject findSetter(DebugValue v) {
        if (!v.hasWriteSideEffects()) {
            return null;
        }
        return RemoteObject.createSimpleObject("function", "Function", "");
    }

    private static String eliminateWhiteSpaces(String str) {
        return WHITESPACES_PATTERN.matcher(str).replaceAll("");
    }

    private class ContextListener implements InspectorExecutionContext.Listener {

        @Override
        public void contextCreated(long id, String name) {
            executionContextCreated(id, name);
        }

        @Override
        public void contextDestroyed(long id, String name) {
            executionContextDestroyed(id);
        }

    }

    private class ConsoleOutputListener implements OutputHandler.Listener {

        private final String type;
        private final StringBuilder output = new StringBuilder();

        ConsoleOutputListener(String type) {
            this.type = type;
        }

        @Override
        public void outputText(String str) {
            output.append(str);
            do {
                int in = output.lastIndexOf("\n");
                int ir = output.lastIndexOf("\r");
                if (in < 0 && ir < 0) {
                    break;
                }
                int end = Math.max(in, ir);
                int endText = end;
                if (ir >= 0 && in == ir + 1) { // \r\n
                    endText--;
                }
                String text = output.substring(0, endText);
                notifyConsoleAPICalled(type, text);
                output.delete(0, end + 1);
            } while (output.length() > 0);
        }

    }
}
