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
package com.oracle.truffle.polyglot;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExecutionListenerImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.MonitoringAccess;
import org.graalvm.polyglot.management.ExecutionEvent;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.RootNode;

final class PolyglotExecutionListener extends AbstractExecutionListenerImpl {

    static final Object[] EMPTY_ARRAY = new Object[0];
    private final PolyglotImpl engineImpl;

    PolyglotExecutionListener(PolyglotImpl engineImpl) {
        super(engineImpl);
        this.engineImpl = engineImpl;
    }

    @Override
    public Object attachExecutionListener(Engine engineAPI, Consumer<ExecutionEvent> onEnter, Consumer<ExecutionEvent> onReturn, boolean expressions, boolean statements,
                    boolean roots,
                    Predicate<Source> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions) {
        PolyglotEngineImpl engine = (PolyglotEngineImpl) engineImpl.getAPIAccess().getImpl(engineAPI);
        Instrumenter instrumenter = (Instrumenter) VMAccessor.INSTRUMENT.getEngineInstrumenter(engine.instrumentationHandler);

        List<Class<? extends Tag>> tags = new ArrayList<>();
        if (expressions) {
            tags.add(StandardTags.ExpressionTag.class);
        }
        if (statements) {
            tags.add(StandardTags.StatementTag.class);
        }
        if (roots) {
            tags.add(StandardTags.RootTag.class);
        }

        if (tags.isEmpty()) {
            throw new IllegalArgumentException("No elements specified to listen to for execution listener. Need to specify at least one element kind: expressions, statements or roots.");
        }
        if (onReturn == null && onEnter == null) {
            throw new IllegalArgumentException("At least one event consumer must be provided for onEnter or onReturn.");
        }

        SourceSectionFilter.Builder filterBuilder = SourceSectionFilter.newBuilder().tagIs(tags.toArray(new Class<?>[0]));
        filterBuilder.includeInternal(false);

        ListenerImpl config = new ListenerImpl(engine, onEnter, onReturn, collectInputValues, collectReturnValues, collectExceptions);

        filterBuilder.sourceIs(new SourcePredicate() {
            public boolean test(com.oracle.truffle.api.source.Source s) {
                String language = s.getLanguage();
                if (language == null) {
                    return false;
                } else if (!engine.idToLanguage.containsKey(language)) {
                    return false;
                } else if (sourceFilter != null) {
                    try {
                        return sourceFilter.test(engineImpl.getPolyglotSource(s));
                    } catch (Throwable e) {
                        if (config.closing) {
                            // configuration is closing ignore errors.
                            return false;
                        }
                        throw new HostException(e);
                    }
                } else {
                    return true;
                }
            }
        });

        if (rootFilter != null) {
            filterBuilder.rootNameIs(new Predicate<String>() {
                public boolean test(String s) {
                    try {
                        return rootFilter.test(s);
                    } catch (Throwable e) {
                        if (config.closing) {
                            // configuration is closing ignore errors.
                            return false;
                        }
                        throw new HostException(e);
                    }
                }
            });
        }

        SourceSectionFilter filter = filterBuilder.build();
        EventBinding<?> binding;
        try {
            boolean mayNeedInputValues = config.collectInputValues && config.onReturn != null;
            boolean mayNeedReturnValue = config.collectReturnValues && config.onReturn != null;
            boolean mayNeedExceptions = config.collectExceptions;

            if (mayNeedInputValues || mayNeedReturnValue || mayNeedExceptions) {
                binding = instrumenter.attachExecutionEventFactory(filter, mayNeedInputValues ? filter : null, new ExecutionEventNodeFactory() {
                    public ExecutionEventNode create(EventContext context) {
                        return new ProfilingNode(config, context);
                    }
                });
            } else {
                // fast path no collection of additional profiles
                binding = instrumenter.attachExecutionEventFactory(filter, null, new ExecutionEventNodeFactory() {
                    public ExecutionEventNode create(EventContext context) {
                        return new DefaultNode(config, context);
                    }
                });
            }
        } catch (Throwable t) {
            throw wrapException(engine, t);
        }
        config.binding = binding;
        return config;
    }

    @Override
    public void closeExecutionListener(Object impl) {
        try {
            ((ListenerImpl) impl).closing = true;
            ((ListenerImpl) impl).binding.dispose();
        } catch (Throwable t) {
            throw wrapException(((ListenerImpl) impl).engine, t);
        }
    }

    @Override
    public List<Value> getInputValues(Object impl) {
        try {
            return ((Event) impl).getInputValues();
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    @Override
    public String getRootName(Object impl) {
        try {
            return ((Event) impl).getRootName();
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    @Override
    public Value getReturnValue(Object impl) {
        try {
            return ((Event) impl).getReturnValue();
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    @Override
    public SourceSection getLocation(Object impl) {
        return ((Event) impl).getLocation();
    }

    @Override
    public PolyglotException getException(Object impl) {
        return ((Event) impl).getException();
    }

    @Override
    public boolean isExpression(Object impl) {
        return hasTag(impl, StandardTags.ExpressionTag.class);
    }

    @Override
    public boolean isStatement(Object impl) {
        return hasTag(impl, StandardTags.StatementTag.class);
    }

    @Override
    public boolean isRoot(Object impl) {
        return hasTag(impl, StandardTags.RootTag.class);
    }

    private static boolean hasTag(Object impl, Class<? extends Tag> tag) {
        try {
            return ((Event) impl).getContext().hasTag(tag);
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    private static RuntimeException wrapException(PolyglotEngineImpl engine, Throwable t) {
        return PolyglotImpl.wrapGuestException(engine, t);
    }

    private static RuntimeException wrapException(Object impl, Throwable t) {
        return wrapException(((DefaultNode) impl).config.engine, t);
    }

    static class ListenerImpl {

        final PolyglotEngineImpl engine;
        final Consumer<ExecutionEvent> onEnter;
        final Consumer<ExecutionEvent> onReturn;
        final MonitoringAccess monitoring;
        final boolean collectInputValues;
        final boolean collectReturnValues;
        final boolean collectExceptions;

        volatile EventBinding<?> binding;
        volatile boolean closing;

        ListenerImpl(PolyglotEngineImpl engine, Consumer<ExecutionEvent> onEnter,
                        Consumer<ExecutionEvent> onReturn,
                        boolean collectInputValues,
                        boolean collectReturnValues,
                        boolean collectExceptions) {
            this.engine = engine;
            this.onEnter = onEnter;
            this.onReturn = onReturn;
            // monitoring is not final so we need to pull it out into a final field
            this.monitoring = engine.impl.getMonitoring();
            this.collectInputValues = collectInputValues;
            this.collectReturnValues = collectReturnValues;
            this.collectExceptions = collectExceptions;
        }
    }

    interface Event {

        String getRootName();

        SourceSection getLocation();

        List<Value> getInputValues();

        Value getReturnValue();

        EventContext getContext();

        PolyglotException getException();

    }

    static final class DynamicEvent implements Event {

        final AbstractNode node;
        final List<Value> inputValues;
        final Value returnValue;
        final PolyglotException exception;

        DynamicEvent(AbstractNode node, List<Value> inputValues, Value returnValue, PolyglotException ex) {
            this.node = node;
            this.inputValues = inputValues;
            this.returnValue = returnValue;
            this.exception = ex;
        }

        public String getRootName() {
            return node.getRootName();
        }

        public PolyglotException getException() {
            return exception;
        }

        public SourceSection getLocation() {
            return node.location;
        }

        public List<Value> getInputValues() {
            return inputValues;
        }

        public Value getReturnValue() {
            return returnValue;
        }

        public EventContext getContext() {
            return node.context;
        }

    }

    static class ProfilingNode extends AbstractNode implements Event {

        @CompilationFinal boolean seenInputValues;
        @CompilationFinal boolean seenReturnValue;
        final PolyglotLanguage language;

        ProfilingNode(ListenerImpl config, EventContext context) {
            super(config, context);
            PolyglotLanguage languageToUse = null;
            if (location != null) {
                languageToUse = config.engine.idToLanguage.get(location.getSource().getLanguage());
            }
            if (languageToUse == null) {
                // should not happen but just in case fallback to host language
                assert false;
                languageToUse = config.engine.hostLanguage;
            }
            this.language = languageToUse;
        }

        @Override
        protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
            // we shouldn't get input events otherwise if collecting is not turned on
            assert config.onReturn != null && config.collectInputValues;
            if (!seenInputValues) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenInputValues = true;
            }
            saveInputValue(frame, inputIndex, inputValue);
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            if (config.onReturn != null) {
                try {
                    if (config.collectReturnValues && !seenReturnValue && result != null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenReturnValue = true;
                    }
                    if (seenReturnValue || seenInputValues) {
                        Object[] inputValues;
                        if (seenInputValues) {
                            inputValues = getSavedInputValues(frame);
                        } else {
                            inputValues = EMPTY_ARRAY;
                        }
                        invokeReturnAllocate(inputValues, result);
                    } else {
                        invokeReturn();
                    }
                } catch (Throwable t) {
                    throw wrapHostError(t);
                }
            }
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (config.onReturn != null) {
                try {
                    if (seenReturnValue || seenInputValues) {
                        Object[] inputValues;
                        if (seenInputValues) {
                            inputValues = getSavedInputValues(frame);
                        } else {
                            inputValues = EMPTY_ARRAY;
                        }
                        invokeExceptionAllocate(inputValues, exception);
                    } else if (config.collectExceptions) {
                        invokeExceptionAllocate(config.collectInputValues ? ReadOnlyValueList.EMPTY : (List<Value>) null, exception);
                    } else {
                        invokeException();
                    }
                } catch (Throwable t) {
                    throw wrapHostError(t);
                }
            }
        }

        @TruffleBoundary
        private void invokeExceptionAllocate(Object[] inputValues, Throwable result) {
            boolean reportException = config.collectExceptions;
            boolean reportInputValues = config.collectInputValues && inputValues.length > 0;

            if (!reportException && !reportInputValues) {
                invokeException();
                return;
            } else {
                PolyglotLanguageContext languageContext = language.getCurrentLanguageContext();
                ReadOnlyValueList convertedInputValues;
                if (reportInputValues) {
                    Value[] hostValues = new Value[inputValues.length];
                    for (int i = 0; i < inputValues.length; i++) {
                        Object guestValue = inputValues[i];
                        if (guestValue != null) {
                            hostValues[i] = languageContext.asValue(inputValues[i]);
                        } else {
                            hostValues[i] = null;
                        }
                    }
                    convertedInputValues = new ReadOnlyValueList(hostValues);
                } else {
                    convertedInputValues = ReadOnlyValueList.EMPTY;
                }
                invokeExceptionAllocate(convertedInputValues, result);
            }
        }

        @TruffleBoundary
        private void invokeReturnAllocate(Object[] inputValues, Object result) {
            boolean reportResult = config.collectReturnValues && result != null;
            boolean reportInputValues = config.collectInputValues && inputValues.length > 0;

            if (!reportResult && !reportInputValues) {
                invokeReturn();
                return;
            } else {
                PolyglotLanguageContext languageContext = language.getCurrentLanguageContext();
                Value returnValue;
                if (reportResult) {
                    returnValue = languageContext.asValue(result);
                } else {
                    returnValue = null;
                }

                ReadOnlyValueList convertedInputValues;
                if (reportInputValues) {
                    convertedInputValues = new ReadOnlyValueList(languageContext.toHostValues(inputValues));
                } else {
                    convertedInputValues = ReadOnlyValueList.EMPTY;
                }
                invokeReturnAllocate(convertedInputValues, returnValue);
            }
        }

        @TruffleBoundary(allowInlining = true)
        protected final void invokeExceptionAllocate(List<Value> inputValues, Throwable e) {
            PolyglotException ex = e != null ? PolyglotImpl.wrapGuestException(language.getCurrentLanguageContext(), e) : null;
            config.onReturn.accept(config.monitoring.newExecutionEvent(new DynamicEvent(this, inputValues, null, ex)));
        }

    }

    static class DefaultNode extends AbstractNode implements Event {

        DefaultNode(ListenerImpl config, EventContext context) {
            super(config, context);
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            if (config.onReturn != null) {
                try {
                    invokeReturn();
                } catch (Throwable t) {
                    throw wrapHostError(t);
                }
            }
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (config.onReturn != null) {
                try {
                    invokeException();
                } catch (Throwable t) {
                    throw wrapHostError(t);
                }
            }
        }

    }

    abstract static class AbstractNode extends ExecutionEventNode implements Event {

        final ListenerImpl config;
        final EventContext context;
        final SourceSection location;
        final ExecutionEvent cachedEvent;

        AbstractNode(ListenerImpl config, EventContext context) {
            this.config = config;
            this.context = context;
            this.location = config.engine.impl.getPolyglotSourceSection(context.getInstrumentedSourceSection());
            this.cachedEvent = config.engine.impl.getMonitoring().newExecutionEvent(this);
        }

        public String getRootName() {
            RootNode rootNode = context.getInstrumentedNode().getRootNode();
            if (rootNode == null) {
                // defensive check
                return null;
            }
            try {
                return rootNode.getName();
            } catch (Throwable t) {
                throw wrapHostError(t);
            }
        }

        @Override
        protected final void onEnter(VirtualFrame frame) {
            if (config.onEnter != null) {
                try {
                    invokeOnEnter();
                } catch (Throwable t) {
                    throw wrapHostError(t);
                }
            }
        }

        protected RuntimeException wrapHostError(Throwable t) {
            assert !(t instanceof HostException);
            throw new HostException(t);
        }

        @TruffleBoundary(allowInlining = true)
        protected final void invokeOnEnter() {
            config.onEnter.accept(cachedEvent);
        }

        @TruffleBoundary(allowInlining = true)
        protected final void invokeReturn() {
            config.onReturn.accept(cachedEvent);
        }

        @TruffleBoundary(allowInlining = true)
        protected final void invokeException() {
            config.onReturn.accept(cachedEvent);
        }

        @TruffleBoundary(allowInlining = true)
        protected final void invokeReturnAllocate(List<Value> inputValues, Value returnValue) {
            config.onReturn.accept(config.monitoring.newExecutionEvent(new DynamicEvent(this, inputValues, returnValue, null)));
        }

        public final SourceSection getLocation() {
            return location;
        }

        public final List<Value> getInputValues() {
            if (config.collectInputValues) {
                return ReadOnlyValueList.EMPTY;
            } else {
                return null;
            }
        }

        public final PolyglotException getException() {
            return null;
        }

        public final Value getReturnValue() {
            return null;
        }

        public final EventContext getContext() {
            return context;
        }
    }

    static class ReadOnlyValueList extends AbstractList<Value> {

        static final ReadOnlyValueList EMPTY = new ReadOnlyValueList(new Value[0]);

        private final Value[] valueArray;

        ReadOnlyValueList(Value[] valueArray) {
            this.valueArray = valueArray;
        }

        @Override
        public Value get(int index) {
            return valueArray[index];
        }

        @Override
        public int size() {
            return valueArray.length;
        }

    }

}
