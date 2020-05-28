/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.tools.insight.Insight;

@SuppressWarnings({"unused", "static-method"})
@ExportLibrary(InteropLibrary.class)
final class AgentObject implements TruffleObject {
    private final TruffleInstrument.Env env;
    private final IgnoreSources excludeSources;
    private final Data data;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private byte[] msg;

    AgentObject(String msg, TruffleInstrument.Env env, IgnoreSources excludeSources, Data data) {
        this.msg = msg == null ? null : msg.getBytes();
        this.env = env;
        this.excludeSources = excludeSources;
        this.data = data;
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        return true;
    }

    @ExportMessage
    static boolean hasMembers(AgentObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(AgentObject obj, boolean includeInternal) {
        return ArrayObject.array("id", "version");
    }

    @ExportMessage
    Object readMember(String name) throws UnknownIdentifierException {
        warnMsg();
        switch (name) {
            case "id":
                return Insight.ID;
            case "version":
                return Insight.VERSION;
        }
        throw UnknownIdentifierException.create(name);
    }

    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static Object invokeMember(AgentObject obj, String member, Object[] args,
                    @CachedLibrary(limit = "0") InteropLibrary interop) throws UnknownIdentifierException, UnsupportedMessageException {
        obj.warnMsg();
        Instrumenter instrumenter = obj.env.getInstrumenter();
        switch (member) {
            case "on": {
                AgentType type = AgentType.find(convertToString(interop, args[0]));
                switch (type) {
                    case SOURCE: {
                        SourceFilter filter = SourceFilter.newBuilder().sourceIs(obj.excludeSources).includeInternal(false).build();
                        EventBinding<LoadSourceListener> handle = instrumenter.attachLoadSourceListener(filter, new LoadSourceListener() {
                            @Override
                            public void onLoad(LoadSourceEvent event) {
                                final Source source = event.getSource();
                                try {
                                    interop.execute(args[1], new SourceEventObject(source));
                                } catch (RuntimeException ex) {
                                    if (ex instanceof TruffleException) {
                                        InsightException.throwWhenExecuted(instrumenter, source, ex);
                                    } else {
                                        throw ex;
                                    }
                                } catch (InteropException ex) {
                                    InsightException.throwWhenExecuted(instrumenter, source, ex);
                                }
                            }
                        }, false);
                        obj.data.registerHandle(type, handle, args[1]);
                        break;
                    }
                    case ENTER: {
                        CompilerDirectives.transferToInterpreter();
                        SourceSectionFilter filter = createFilter(obj, args);
                        EventBinding<ExecutionEventNodeFactory> handle = instrumenter.attachExecutionEventFactory(filter, AgentExecutionNode.factory(obj.env, args[1], null));
                        obj.data.registerHandle(type, handle, args[1]);
                        break;
                    }
                    case RETURN: {
                        CompilerDirectives.transferToInterpreter();
                        SourceSectionFilter filter = createFilter(obj, args);
                        EventBinding<ExecutionEventNodeFactory> handle = instrumenter.attachExecutionEventFactory(filter, AgentExecutionNode.factory(obj.env, null, args[1]));
                        obj.data.registerHandle(type, handle, args[1]);
                        break;
                    }
                    case CLOSE: {
                        obj.data.registerOnClose(args[1]);
                        break;
                    }

                    default:
                        throw new IllegalStateException();
                }
                break;
            }
            case "off": {
                CompilerDirectives.transferToInterpreter();
                AgentType type = AgentType.find(convertToString(interop, args[0]));
                obj.data.removeHandle(type, args[1]);
                break;
            }
            default:
                throw UnknownIdentifierException.create(member);
        }
        return obj;
    }

    private void warnMsg() {
        byte[] arr = msg;
        if (arr != null) {
            try {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                env.err().write(arr);
                msg = null;
            } catch (IOException ex) {
                // go on
            }
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static String convertToString(InteropLibrary interop, Object obj) throws UnsupportedMessageException {
        return interop.asString(obj);
    }

    private static SourceSectionFilter createFilter(AgentObject obj, Object[] args) throws IllegalArgumentException, UnsupportedMessageException {
        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder().sourceIs(obj.excludeSources).includeInternal(false);
        List<Class<? extends Tag>> allTags = new ArrayList<>();
        if (args.length > 2) {
            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            final Object config = args[2];
            Object allMembers = iop.getMembers(config, false);
            long allMembersSize = iop.getArraySize(allMembers);
            for (int i = 0; i < allMembersSize; i++) {
                Object atI;
                try {
                    atI = iop.readArrayElement(allMembers, i);
                } catch (InvalidArrayIndexException ex) {
                    continue;
                }
                String type = iop.asString(atI);
                switch (type) {
                    case "expressions":
                        if (isSet(iop, config, "expressions")) {
                            allTags.add(StandardTags.ExpressionTag.class);
                        }
                        break;
                    case "statements":
                        if (isSet(iop, config, "statements")) {
                            allTags.add(StandardTags.StatementTag.class);
                        }
                        break;
                    case "roots":
                        if (isSet(iop, config, "roots")) {
                            allTags.add(StandardTags.RootBodyTag.class);
                        }
                        break;
                    case "rootNameFilter":
                        try {
                            Object fn = iop.readMember(config, "rootNameFilter");
                            if (fn != null && !iop.isNull(fn)) {
                                if (iop.isString(fn)) {
                                    builder.rootNameIs(new RegexNameFilter(iop.asString(fn)));
                                } else {
                                    if (!iop.isExecutable(fn)) {
                                        throw new IllegalArgumentException("rootNameFilter should be a string, a regular expression!");
                                    }
                                    builder.rootNameIs(new RootNameFilter(fn));
                                }
                            }
                        } catch (UnknownIdentifierException ex) {
                            // OK
                        }
                        break;
                    case "sourceFilter":
                        try {
                            Object fn = iop.readMember(config, "sourceFilter");
                            if (fn != null && !iop.isNull(fn)) {
                                if (!iop.isExecutable(fn)) {
                                    throw new IllegalArgumentException("sourceFilter has to be a function!");
                                }
                                SourceFilter filter = SourceFilter.newBuilder().sourceIs(new AgentSourceFilter(fn)).build();
                                builder.sourceFilter(filter);
                            }
                        } catch (UnknownIdentifierException ex) {
                            // OK
                        }
                        break;
                    default:
                        throw InsightException.unknownAttribute(type);
                }
            }

        }
        if (allTags.isEmpty()) {
            throw new IllegalArgumentException(
                            "No elements specified to listen to for execution listener. Need to specify at least one element kind: expressions, statements or roots.");
        }
        builder.tagIs(allTags.toArray(new Class<?>[0]));
        final SourceSectionFilter filter = builder.build();
        return filter;
    }

    @ExportMessage
    static boolean isMemberInvocable(AgentObject obj, String member) {
        return false;
    }

    private static boolean isSet(InteropLibrary iop, Object obj, String property) {
        try {
            Object value = iop.readMember(obj, property);
            return Boolean.TRUE.equals(value);
        } catch (UnknownIdentifierException ex) {
            return false;
        } catch (InteropException ex) {
            throw InsightException.raise(ex);
        }
    }

    @CompilerDirectives.TruffleBoundary
    void onClosed() {
        data.onClosed();
    }

    static class Data {

        final Map<AgentType, Map<Object, EventBinding<?>>> listeners = new EnumMap<>(AgentType.class);
        Object closeFn;

        synchronized void registerHandle(AgentType at, EventBinding<?> handle, Object arg) {
            Map<Object, EventBinding<?>> listenersForType = listeners.get(at);
            if (listenersForType == null) {
                listenersForType = new LinkedHashMap<>();
                listeners.put(at, listenersForType);
            }
            listenersForType.put(arg, handle);
        }

        void removeHandle(AgentType type, Object arg) {
            EventBinding<?> remove;
            synchronized (this) {
                Map<Object, EventBinding<?>> listenersForType = listeners.get(type);
                remove = listenersForType == null ? null : listenersForType.get(arg);
            }
            if (remove != null) {
                remove.dispose();
            }
        }

        @CompilerDirectives.TruffleBoundary
        void onClosed() {
            synchronized (this) {
                for (Map.Entry<AgentType, Map<Object, EventBinding<?>>> entry : listeners.entrySet()) {
                    Map<Object, EventBinding<?>> val = entry.getValue();
                    for (EventBinding<?> binding : val.values()) {
                        binding.dispose();
                    }
                }
            }
            if (closeFn == null) {
                return;
            }
            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            try {
                iop.execute(closeFn);
            } catch (InteropException ex) {
                throw InsightException.raise(ex);
            } finally {
                closeFn = null;
            }
        }

        void registerOnClose(Object fn) {
            closeFn = fn;
        }
    }
}
