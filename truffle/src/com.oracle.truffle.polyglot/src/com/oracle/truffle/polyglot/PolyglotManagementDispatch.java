/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractManagementDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.ManagementAccess;
import org.graalvm.polyglot.management.ExecutionEvent;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.RootNode;

final class PolyglotManagementDispatch extends AbstractManagementDispatch {

    static final Object[] EMPTY_ARRAY = new Object[0];

    PolyglotManagementDispatch(PolyglotImpl engineImpl) {
        super(engineImpl);
    }

    // implementation for org.graalvm.polyglot.management.ExecutionListener

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
    public List<Value> getExecutionEventInputValues(Object impl) {
        try {
            return ((Event) impl).getInputValues();
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    @Override
    public String getExecutionEventRootName(Object impl) {
        try {
            return ((Event) impl).getRootName();
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    @Override
    public Value getExecutionEventReturnValue(Object impl) {
        try {
            return ((Event) impl).getReturnValue();
        } catch (Throwable t) {
            throw wrapException(impl, t);
        }
    }

    @Override
    public SourceSection getExecutionEventLocation(Object impl) {
        return ((Event) impl).getLocation();
    }

    @Override
    public PolyglotException getExecutionEventException(Object impl) {
        return ((Event) impl).getException();
    }

    @Override
    public boolean isExecutionEventExpression(Object impl) {
        return hasTag(impl, StandardTags.ExpressionTag.class);
    }

    @Override
    public boolean isExecutionEventStatement(Object impl) {
        return hasTag(impl, StandardTags.StatementTag.class);
    }

    @Override
    public boolean isExecutionEventRoot(Object impl) {
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
        return PolyglotImpl.guestToHostException(engine, t);
    }

    private static RuntimeException wrapException(Object impl, Throwable t) {
        return wrapException(((DefaultNode) impl).config.engine, t);
    }

    static class ListenerImpl {

        final AbstractManagementDispatch managementDispatch;
        final PolyglotEngineImpl engine;
        final Consumer<ExecutionEvent> onEnter;
        final Consumer<ExecutionEvent> onReturn;
        final ManagementAccess management;
        final boolean collectInputValues;
        final boolean collectReturnValues;
        final boolean collectExceptions;

        volatile EventBinding<?> binding;
        volatile boolean closing;

        ListenerImpl(AbstractManagementDispatch managementDispatch, PolyglotEngineImpl engine, Consumer<ExecutionEvent> onEnter,
                        Consumer<ExecutionEvent> onReturn,
                        boolean collectInputValues,
                        boolean collectReturnValues,
                        boolean collectExceptions) {
            this.managementDispatch = managementDispatch;
            this.engine = engine;
            this.onEnter = onEnter;
            this.onReturn = onReturn;
            // monitoring is not final so we need to pull it out into a final field
            this.management = engine.impl.getManagement();
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
            return node.getLocation();
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
            com.oracle.truffle.api.source.SourceSection location = context.getInstrumentedSourceSection();
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
            PolyglotException ex = e != null ? PolyglotImpl.guestToHostException(language.getCurrentLanguageContext(), e, true) : null;
            config.onReturn.accept(config.management.newExecutionEvent(config.managementDispatch, new DynamicEvent(this, inputValues, null, ex)));
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
        final ExecutionEvent cachedEvent;

        AbstractNode(ListenerImpl config, EventContext context) {
            this.config = config;
            this.context = context;
            this.cachedEvent = config.management.newExecutionEvent(config.managementDispatch, this);
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
            assert !config.engine.host.isHostException(t);
            throw config.engine.host.toHostException(null, t);
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
            config.onReturn.accept(config.management.newExecutionEvent(config.managementDispatch, new DynamicEvent(this, inputValues, returnValue, null)));
        }

        public final SourceSection getLocation() {
            return PolyglotImpl.getPolyglotSourceSection(config.engine.impl, context.getInstrumentedSourceSection());
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
