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
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ExportLibrary(InteropLibrary.class)
final class AgentObject implements TruffleObject {
    private final TruffleInstrument.Env env;
    private final LanguageInfo language;
    private final AtomicBoolean initializationFinished;

    AgentObject(TruffleInstrument.Env env, Source script, LanguageInfo language) {
        this.env = env;
        this.language = language;
        this.initializationFinished = new AtomicBoolean(false);
    }

    private static final class ExcludeAgentScriptsFilter implements SourceSectionFilter.SourcePredicate {
        private final AtomicBoolean initializationFinished;

        ExcludeAgentScriptsFilter(AtomicBoolean initializationFinished) {
            this.initializationFinished = initializationFinished;
        }

        @Override
        public boolean test(Source source) {
            return initializationFinished.get();
        }
    }

    @ExportMessage
    static boolean hasMembers(AgentObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(AgentObject obj, boolean includeInternal) {
        return new Object[0];
    }

    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static Object invokeMember(AgentObject obj, String member, Object[] args,
                    @CachedLibrary(limit = "1") InteropLibrary interop) throws UnknownIdentifierException, UnsupportedMessageException {
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
                                    throw raise(RuntimeException.class, ex);
                                }
                            }
                        }, true);
                        break;
                    }
                    case ENTER: {
                        CompilerDirectives.transferToInterpreter();
                        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder().sourceIs(new ExcludeAgentScriptsFilter(obj.initializationFinished)).includeInternal(false);
                        List<Class<? extends Tag>> allTags = new ArrayList<>();
                        if (args.length > 2) {
                            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
                            if (isSet(iop, args[2], "expressions")) {
                                allTags.add(StandardTags.ExpressionTag.class);
                            }
                            if (isSet(iop, args[2], "statements")) {
                                allTags.add(StandardTags.StatementTag.class);
                            }
                            if (isSet(iop, args[2], "roots")) {
                                allTags.add(StandardTags.RootTag.class);
                            }
                        }
                        if (allTags.isEmpty()) {
                            throw new IllegalArgumentException(
                                            "No elements specified to listen to for execution listener. Need to specify at least one element kind: expressions, statements or roots.");
                        }
                        builder.tagIs(allTags.toArray(new Class<?>[0]));

                        final SourceSectionFilter filter = builder.build();
                        instrumenter.attachExecutionEventFactory(filter, AgentExecutionNode.factory(obj.env, args[1]));
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

    @ExportMessage
    static boolean isMemberInvocable(AgentObject obj, String member) {
        return false;
    }

    void initializationFinished() {
        this.initializationFinished.set(true);
    }

    private static boolean isSet(InteropLibrary iop, Object obj, String property) {
        try {
            Object value = iop.readMember(obj, property);
            return Boolean.TRUE.equals(value);
        } catch (UnknownIdentifierException ex) {
            return false;
        } catch (InteropException ex) {
            throw raise(RuntimeException.class, ex);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Exception> T raise(Class<T> type, Exception ex) throws T {
        throw (T) ex;
    }
}
