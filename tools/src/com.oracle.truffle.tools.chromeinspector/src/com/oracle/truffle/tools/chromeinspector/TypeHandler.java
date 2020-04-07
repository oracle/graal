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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class TypeHandler {

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    private final TruffleInstrument.Env env;
    private final AtomicReference<EventBinding<TypeProfileEventFactory>> currentBinding;

    public TypeHandler(TruffleInstrument.Env env) {
        this.env = env;
        this.currentBinding = new AtomicReference<>();
    }

    public boolean isStarted() {
        return currentBinding.get() != null;
    }

    public boolean start(boolean inspectInternal) {
        if (currentBinding.get() == null) {
            final SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).includeInternal(inspectInternal).build();
            final Instrumenter instrumenter = env.getInstrumenter();
            final EventBinding<TypeProfileEventFactory> binding = instrumenter.attachExecutionEventFactory(filter, new TypeProfileEventFactory());
            if (currentBinding.compareAndSet(null, binding)) {
                return true;
            } else {
                binding.dispose();
            }
        }
        return false;
    }

    public void stop() {
        final EventBinding<TypeProfileEventFactory> binding = currentBinding.get();
        if (binding != null && currentBinding.compareAndSet(binding, null)) {
            binding.dispose();
        }
    }

    public void clearData() {
        final EventBinding<TypeProfileEventFactory> binding = currentBinding.get();
        if (binding != null) {
            binding.getElement().profileMap.clear();
        }
    }

    public Collection<SectionTypeProfile> getSectionTypeProfiles() {
        EventBinding<TypeProfileEventFactory> binding = currentBinding.get();
        List<SectionTypeProfile> profiles = new ArrayList<>(binding.getElement().profileMap.values());
        profiles.sort((p1, p2) -> Integer.compare(p1.sourceSection.getCharEndIndex(), p2.sourceSection.getCharEndIndex()));
        return profiles;
    }

    static String getMetaObjectString(TruffleInstrument.Env env, final LanguageInfo language, Object argument) {
        Object view = env.getLanguageView(language, argument);
        InteropLibrary viewLib = InteropLibrary.getFactory().getUncached(view);
        String retType = null;
        if (viewLib.hasMetaObject(view)) {
            try {
                retType = INTEROP.asString(INTEROP.getMetaQualifiedName(viewLib.getMetaObject(view)));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
        return retType;
    }

    public static final class SectionTypeProfile {

        private final SourceSection sourceSection;
        private final Collection<String> types = new HashSet<>();

        private SectionTypeProfile(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public Collection<String> getTypes() {
            return types;
        }
    }

    public interface Provider {

        TypeHandler getTypeHandler();
    }

    private final class TypeProfileEventFactory implements ExecutionEventNodeFactory {

        private final Map<SourceSection, SectionTypeProfile> profileMap;

        private TypeProfileEventFactory() {
            this.profileMap = new ConcurrentHashMap<>();
        }

        @Override
        public ExecutionEventNode create(final EventContext context) {
            return new ExecutionEventNode() {

                @Override
                protected void onEnter(VirtualFrame frame) {
                    super.onEnter(frame);
                    final Node rootNode = context.getInstrumentedNode();
                    final SourceSection section = context.getInstrumentedSourceSection();
                    processArguments(frame.materialize(), rootNode, section);
                }

                @Override
                protected void onReturnValue(VirtualFrame frame, Object result) {
                    super.onReturnValue(frame, result);
                    final Node rootNode = context.getInstrumentedNode();
                    final SourceSection section = context.getInstrumentedSourceSection();
                    processReturnValue(result, rootNode, section);
                }

                @CompilerDirectives.TruffleBoundary
                private void processArguments(final MaterializedFrame frame, final Node node, final SourceSection section) {
                    final Iterator<Scope> scopes = env.findLocalScopes(node, frame).iterator();
                    if (!scopes.hasNext()) {
                        return;
                    }
                    final Scope functionScope = scopes.next();
                    final Object argsObject = functionScope.getArguments();
                    if (argsObject instanceof TruffleObject) {
                        final LanguageInfo language = node.getRootNode().getLanguageInfo();
                        try {
                            Object keys = INTEROP.getMembers(argsObject);
                            long size = INTEROP.getArraySize(keys);
                            for (long i = 0; i < size; i++) {
                                String key = INTEROP.asString(INTEROP.readArrayElement(keys, i));
                                Object argument = INTEROP.readMember(argsObject, key);

                                String retType = getMetaObjectString(env, language, argument);
                                SourceSection argSection = getArgSection(section, key);
                                if (argSection != null) {
                                    profileMap.computeIfAbsent(argSection, s -> new SectionTypeProfile(s)).types.add(retType);
                                }
                            }
                        } catch (UnsupportedMessageException | UnknownIdentifierException | InvalidArrayIndexException e) {
                            throw new AssertionError(e);
                        }
                    }
                }

            };
        }

        @CompilerDirectives.TruffleBoundary
        private void processReturnValue(final Object result, final Node node, final SourceSection section) {
            if (result != null) {
                final LanguageInfo language = node.getRootNode().getLanguageInfo();
                final String retType = getMetaObjectString(env, language, result);
                profileMap.computeIfAbsent(section, s -> new SectionTypeProfile(s)).types.add(retType);
            }
        }

        @CompilerDirectives.TruffleBoundary
        private SourceSection getArgSection(SourceSection function, Object argName) {
            // TODO: Create API for obtaining local variable/parameter declaration position
            int idx = function.getCharacters().toString().indexOf(argName.toString());
            return idx < 0 ? null : function.getSource().createSection(function.getCharIndex() + idx, argName.toString().length());
        }
    }
}
