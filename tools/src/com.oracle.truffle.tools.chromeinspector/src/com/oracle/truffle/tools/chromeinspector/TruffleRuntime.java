/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.json.JSONArray;
import org.json.JSONObject;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.source.Source;

import com.oracle.truffle.tools.chromeinspector.TruffleExecutionContext.NoSuspendedThreadException;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.domains.RuntimeDomain;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.instrument.Enabler;
import com.oracle.truffle.tools.chromeinspector.instrument.OutputConsumerInstrument;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.types.ExceptionDetails;
import com.oracle.truffle.tools.chromeinspector.types.InternalPropertyDescriptor;
import com.oracle.truffle.tools.chromeinspector.types.PropertyDescriptor;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

public final class TruffleRuntime extends RuntimeDomain {

    private final TruffleExecutionContext context;
    private TruffleExecutionContext.Listener contextListener;

    public TruffleRuntime(TruffleExecutionContext context) {
        this.context = context;
    }

    @Override
    public void enable() {
        if (contextListener == null) {
            contextListener = new ContextListener();
            context.addListener(contextListener);
            InstrumentInfo instrumentInfo = context.getEnv().getInstruments().get(OutputConsumerInstrument.ID);
            context.getEnv().lookup(instrumentInfo, Enabler.class).enable();
            OutputHandler oh = context.getEnv().lookup(instrumentInfo, OutputHandler.Provider.class).getOutputHandler();
            oh.setOutListener(new ConsoleOutputListener("log"));
            oh.setErrListener(new ConsoleOutputListener("error"));
        }
    }

    @Override
    public void disable() {
        if (contextListener != null) {
            InstrumentInfo instrumentInfo = context.getEnv().getInstruments().get(OutputConsumerInstrument.ID);
            context.getEnv().lookup(instrumentInfo, Enabler.class).disable();
            context.removeListener(contextListener);
            contextListener = null;
        }
    }

    private Source createSource(String expression, String sourceURL) {
        String language = context.getLastLanguage();
        String mimeType = context.getLastMimeType();
        String name = (sourceURL != null) ? sourceURL : "eval";
        Source.Builder<RuntimeException, RuntimeException, RuntimeException> builder = Source.newBuilder(expression).name(name).mimeType(mimeType);
        if (language != null) {
            builder.language(language);
        }
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
        ScriptsHandler sh = context.acquireScriptsHandler();
        try {
            if (sh != null) {
                Source source = createSource(expression, sourceURL);
                boolean parsed = false;
                String[] exceptionText = new String[1];
                if (context.getSuspendedInfo() != null) {
                    try {
                        parsed = context.executeInSuspendThread(new SuspendThreadExecutable<Boolean>() {
                            @Override
                            public Boolean executeCommand() throws CommandProcessException {
                                try {
                                    context.getEnv().parse(source);
                                    return true;
                                } catch (Exception ex) {
                                    // Didn't manage to parse this
                                    exceptionText[0] = ex.getLocalizedMessage();
                                    return false;
                                }
                            }

                            @Override
                            public Boolean processException(DebugException ex) {
                                fillExceptionDetails(ret, ex);
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
                    int id = sh.assureLoaded(source);
                    ret.put("scriptId", Integer.toString(id));
                }
                if (exceptionText[0] != null) {
                    JSONObject exceptionDetails = new JSONObject();
                    exceptionDetails.put("text", exceptionText[0]);
                    ret.put("exceptionDetails", exceptionDetails);
                }
            }
        } finally {
            context.releaseScriptsHandler();
        }
        return new Params(ret);
    }

    @Override
    public Params evaluate(String expression, String objectGroup, boolean includeCommandLineAPI, boolean silent, int contextId, boolean returnByValue, boolean awaitPromise)
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
                        JSONObject result;
                        DebugValue value = suspendedInfo.getSuspendedEvent().getTopStackFrame().eval(expression);
                        if (returnByValue) {
                            result = RemoteObject.createJSONResultValue(value, context.getErr());
                        } else {
                            RemoteObject ro = new RemoteObject(value, context.getErr());
                            context.getRemoteObjectsHandler().register(ro);
                            result = ro.toJSON();
                        }
                        json.put("result", result);
                        return null;
                    }

                    @Override
                    public Void processException(DebugException ex) {
                        fillExceptionDetails(json, ex);
                        return null;
                    }
                });
            } catch (NoSuspendedThreadException ex) {
                JSONObject exceptionDetails = new JSONObject();
                exceptionDetails.put("text", ex.getLocalizedMessage());
                json.put("exceptionDetails", exceptionDetails);
            }
        } else {
            JSONObject exceptionDetails = new JSONObject();
            exceptionDetails.put("text", "<Not suspended>");
            json.put("exceptionDetails", exceptionDetails);
        }
        return new Params(json);
    }

    @Override
    public Params getProperties(String objectId, boolean ownProperties) throws CommandProcessException {
        if (objectId == null) {
            throw new CommandProcessException("An objectId required.");
        }
        RemoteObject object = context.getRemoteObjectsHandler().getRemote(objectId);
        JSONObject json = new JSONObject();
        if (object != null) {
            DebugValue value = object.getDebugValue();
            try {
                if (value != null) {
                    context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                        @Override
                        public Void executeCommand() throws CommandProcessException {
                            putResultProperties(json, value.getProperties(), value.isArray() ? value.getArray() : Collections.emptyList());
                            return null;
                        }

                        @Override
                        public Void processException(DebugException ex) {
                            fillExceptionDetails(json, ex);
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
                            putResultProperties(json, properties, Collections.emptyList());
                            return null;
                        }

                        @Override
                        public Void processException(DebugException ex) {
                            fillExceptionDetails(json, ex);
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

    private void putResultProperties(JSONObject json, Collection<DebugValue> properties, Collection<DebugValue> arrayElements) {
        JSONArray result = new JSONArray();
        JSONArray internals = new JSONArray();
        HashSet<String> storedPropertyNames = new HashSet<>(properties.size());
        for (DebugValue v : properties) {
            if (v.isReadable()) {
                if (!v.isInternal()) {
                    result.put(createPropertyJSON(v));
                    storedPropertyNames.add(v.getName());
                } else {
                    internals.put(createPropertyJSON(v));
                }
            }
        }
        int i = 0;
        for (DebugValue v : arrayElements) {
            String name = Integer.toString(i++);
            if (v.isReadable() && !storedPropertyNames.contains(name)) {
                result.put(createPropertyJSON(v, name));
            }
        }
        json.put("result", result);
        json.put("internalProperties", internals);
    }

    @Override
    public Params callFunctionOn(String objectId, String functionDeclaration, JSONArray arguments, boolean silent, boolean returnByValue, boolean awaitPromise) throws CommandProcessException {
        if (objectId == null) {
            throw new CommandProcessException("An objectId required.");
        }
        RemoteObject object = context.getRemoteObjectsHandler().getRemote(objectId);
        JSONObject json = new JSONObject();
        if (object != null) {
            DebugValue value = object.getDebugValue();
            DebuggerSuspendedInfo suspendedInfo = context.getSuspendedInfo();
            if (suspendedInfo != null) {
                try {
                    context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                        @Override
                        public Void executeCommand() throws CommandProcessException {
                            JSONObject result;
                            if (functionDeclaration.startsWith("function getCompletions(")) {
                                result = createCodecompletion(value);
                            } else {
                                String code = "(" + functionDeclaration + ")(" + value.getName() + ")";
                                DebugValue eval = suspendedInfo.getSuspendedEvent().getTopStackFrame().eval(code);
                                if (!returnByValue) {
                                    RemoteObject ro = new RemoteObject(eval, context.getErr());
                                    context.getRemoteObjectsHandler().register(ro);
                                    result = ro.toJSON();
                                } else {
                                    result = RemoteObject.createJSONResultValue(eval, context.getErr());
                                }
                            }
                            json.put("result", result);
                            return null;
                        }

                        @Override
                        public Void processException(DebugException ex) {
                            fillExceptionDetails(json, ex);
                            return null;
                        }
                    });
                } catch (NoSuspendedThreadException ex) {
                    json.put("result", new JSONObject());
                }
            }
        }
        return new Params(json);
    }

    private JSONObject createCodecompletion(DebugValue value) {
        Collection<DebugValue> properties = null;
        try {
            properties = value.getProperties();
        } catch (Exception ex) {
            PrintWriter err = context.getErr();
            if (err != null) {
                err.println("getProperties(" + value.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
        }
        JSONObject result = new JSONObject();
        JSONArray valueArray = new JSONArray();
        JSONObject itemsObj = new JSONObject();
        JSONArray items = new JSONArray();
        if (properties != null) {
            for (DebugValue property : properties) {
                items.put(property.getName());
            }
        }
        itemsObj.put("items", items);
        valueArray.put(itemsObj);
        result.put("type", "object");
        result.put("value", valueArray);
        return result;
    }

    private void fillExceptionDetails(JSONObject obj, DebugException ex) {
        fillExceptionDetails(obj, ex, context);
    }

    static void fillExceptionDetails(JSONObject obj, DebugException ex, TruffleExecutionContext context) {
        ExceptionDetails exceptionDetails = new ExceptionDetails(ex);
        obj.put("exceptionDetails", exceptionDetails.createJSON(context));
    }

    @Override
    public void runIfWaitingForDebugger() {
        context.doRunIfWaitingForDebugger();
    }

    private JSONObject createPropertyJSON(DebugValue v) {
        return createPropertyJSON(v, null);
    }

    private JSONObject createPropertyJSON(DebugValue v, String defaultName) {
        PropertyDescriptor pd;
        RemoteObject rv = new RemoteObject(v, context.getErr());
        context.getRemoteObjectsHandler().register(rv);
        String name = v.getName();
        if (name == null && defaultName != null) {
            name = defaultName;
        }
        if (!v.isInternal()) {
            pd = new PropertyDescriptor(name, rv, v.isWritable(), null, null, true, true, null, true, null);
            return pd.toJSON();
        } else {
            InternalPropertyDescriptor ipd = new InternalPropertyDescriptor(name, rv);
            return ipd.toJSON();
        }
    }

    private class ContextListener implements TruffleExecutionContext.Listener {

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

        ConsoleOutputListener(String type) {
            this.type = type;
        }

        @Override
        public void outputText(String str) {
            eventHandler.event(new Event("Runtime.consoleAPICalled", Params.createConsoleAPICalled(type, str, context.getId())));
        }

    }
}
