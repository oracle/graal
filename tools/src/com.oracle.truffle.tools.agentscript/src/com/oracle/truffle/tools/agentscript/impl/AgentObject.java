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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.agentscript.AgentScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"unused", "static-method"})
@ExportLibrary(InteropLibrary.class)
final class AgentObject implements TruffleObject {
    private final TruffleInstrument.Env env;
    private final AtomicBoolean initializationFinished;
    private Object closeFn;

    AgentObject(TruffleInstrument.Env env) {
        this.env = env;
        this.initializationFinished = new AtomicBoolean(false);
    }

    private static final class ExcludeAgentScriptsFilter implements SourceSectionFilter.SourcePredicate {
        private final AtomicBoolean initializationFinished;
        private final Map<Source, Boolean> ignore = new WeakHashMap<>();

        ExcludeAgentScriptsFilter(AtomicBoolean initializationFinished) {
            this.initializationFinished = initializationFinished;
        }

        @Override
        public boolean test(Source source) {
            if (initializationFinished.get()) {
                if (Boolean.TRUE.equals(ignore.get(source))) {
                    return false;
                }
                return true;
            } else {
                ignore.put(source, Boolean.TRUE);
                return false;
            }
        }
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
        return new Object[0];
    }

    @ExportMessage
    Object readMember(String name) throws UnknownIdentifierException {
        switch (name) {
            case "version":
                return AgentScript.VERSION;
        }
        throw UnknownIdentifierException.create(name);
    }

    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static Object invokeMember(AgentObject obj, String member, Object[] args,
                    @CachedLibrary(limit = "0") InteropLibrary interop) throws UnknownIdentifierException, UnsupportedMessageException {
        Instrumenter instrumenter = obj.env.getInstrumenter();
        switch (member) {
            case "on":
                AgentType type = AgentType.find((String) args[0]);
                switch (type) {
                    case SOURCE: {
                        SourceFilter filter = SourceFilter.newBuilder().sourceIs(new ExcludeAgentScriptsFilter(obj.initializationFinished)).includeInternal(false).build();
                        instrumenter.attachLoadSourceListener(filter, new LoadSourceListener() {
                            @Override
                            public void onLoad(LoadSourceEvent event) {
                                try {
                                    interop.execute(args[1], new SourceEventObject(event.getSource()));
                                } catch (InteropException ex) {
                                    throw AgentException.raise(ex);
                                }
                            }
                        }, true);
                        break;
                    }
                    case ENTER: {
                        CompilerDirectives.transferToInterpreter();
                        SourceSectionFilter filter = createFilter(obj, args);
                        instrumenter.attachExecutionEventFactory(filter, AgentExecutionNode.factory(obj.env, args[1], null));
                        break;
                    }
                    case RETURN: {
                        CompilerDirectives.transferToInterpreter();
                        SourceSectionFilter filter = createFilter(obj, args);
                        instrumenter.attachExecutionEventFactory(filter, AgentExecutionNode.factory(obj.env, null, args[1]));
                        break;
                    }
                    case CLOSE: {
                        obj.registerOnClose(args[1]);
                        break;
                    }

                    default:
                        throw new IllegalStateException();
                }
                break;
            default:
                throw UnknownIdentifierException.create(member);
        }
        return obj;
    }

    private static SourceSectionFilter createFilter(AgentObject obj, Object[] args) throws IllegalArgumentException, UnsupportedMessageException {
        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder().sourceIs(new ExcludeAgentScriptsFilter(obj.initializationFinished)).includeInternal(false);
        List<Class<? extends Tag>> allTags = new ArrayList<>();
        if (args.length > 2) {
            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            final Object config = args[2];

            if (isSet(iop, config, "expressions")) {
                allTags.add(StandardTags.ExpressionTag.class);
            }
            if (isSet(iop, config, "statements")) {
                allTags.add(StandardTags.StatementTag.class);
            }
            if (isSet(iop, config, "roots")) {
                allTags.add(StandardTags.RootBodyTag.class);
            }
            try {
                Object rootNameFilter = iop.readMember(config, "rootNameFilter");
                if (rootNameFilter != null && !iop.isNull(rootNameFilter)) {
                    if (!iop.isExecutable(rootNameFilter)) {
                        throw new IllegalArgumentException("rootNameFilter has to be a function!");
                    }
                    builder.rootNameIs(new RootNameFilter(rootNameFilter, obj.initializationFinished));
                }
            } catch (UnknownIdentifierException ex) {
                // OK
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

    void initializationFinished() {
        this.initializationFinished.set(true);
    }

    @CompilerDirectives.TruffleBoundary
    void onClosed() {
        if (closeFn == null) {
            return;
        }
        final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
        try {
            iop.execute(closeFn);
        } catch (InteropException ex) {
            throw AgentException.raise(ex);
        } finally {
            closeFn = null;
        }
    }

    void registerOnClose(Object fn) {
        closeFn = fn;
    }

    private static boolean isSet(InteropLibrary iop, Object obj, String property) {
        try {
            Object value = iop.readMember(obj, property);
            return Boolean.TRUE.equals(value);
        } catch (UnknownIdentifierException ex) {
            return false;
        } catch (InteropException ex) {
            throw AgentException.raise(ex);
        }
    }
}
