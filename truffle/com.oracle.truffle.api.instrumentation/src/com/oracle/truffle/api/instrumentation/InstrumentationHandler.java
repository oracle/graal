/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode.EventChainNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Central coordinator class for the Truffle instrumentation framework. Allocated once per engine.
 */
final class InstrumentationHandler {

    /* Enable trace output to stdout. */
    private static final boolean TRACE = Boolean.getBoolean("truffle.instrumentation.trace");

    /* All roots that were initialized (executed at least once) */
    private final Map<RootNode, Void> roots = Collections.synchronizedMap(new WeakHashMap<RootNode, Void>());

    /* All bindings that where globally created by instrumenters. */
    private final List<EventBinding<?>> bindings = new ArrayList<>();

    /* Cached instance for reuse for newly installed root nodes. */
    private final AddBindingsVisitor addAllBindingsVisitor = new AddBindingsVisitor(bindings);

    /*
     * Fast lookup of instrumenters based on a key provided by the accessor.
     */
    private final Map<Object, AbstractInstrumenter> instrumentations = new HashMap<>();

    private final Env env;

    private boolean initialized;

    private InstrumentationHandler(Env env) {
        this.env = env;
    }

    void installRootNode(RootNode root) {
        if (!ACCESSOR.isInstrumentable(root)) {
            return;
        }
        if (!initialized) {
            initialize();
        }
        roots.put(root, null);
        visitRoot(root, addAllBindingsVisitor);
    }

    void addInstrumentation(Object key, Class<?> clazz) {
        addInstrumenter(key, new InstrumentationInstrumenter(clazz));
    }

    void disposeInstrumentation(Object key, boolean cleanupRequired) {
        if (TRACE) {
            trace("Dispose instrumenter %n", key);
        }
        AbstractInstrumenter disposedInstrumenter = instrumentations.get(key);
        List<EventBinding<?>> disposedBindings = new ArrayList<>();
        for (Iterator<EventBinding<?>> iterator = bindings.listIterator(); iterator.hasNext();) {
            EventBinding<?> binding = iterator.next();
            if (binding.getInstrumenter() == disposedInstrumenter) {
                iterator.remove();
                disposedBindings.add(binding);
            }
        }
        disposedInstrumenter.dispose();
        instrumentations.remove(key);

        if (cleanupRequired) {
            DisposeBindingsVisitor disposeVisitor = new DisposeBindingsVisitor(disposedBindings);
            for (RootNode root : roots.keySet()) {
                visitRoot(root, disposeVisitor);
            }
        }

        if (TRACE) {
            trace("Disposed instrumenter %n", key);
        }
    }

    void attachLanguage(Object context, InstrumentationLanguage<Object> language) {
        addInstrumenter(context, new LanguageInstrumenter<>(language, context));
    }

    void detachLanguage(Object context) {
        if (instrumentations.containsKey(context)) {
            /*
             * TODO (chumer): do we need cleanup/invalidate here? With shared CallTargets we
             * probably will.
             */
            disposeInstrumentation(context, false);
        }
    }

    <T> EventBinding<T> addBinding(EventBinding<T> binding) {
        if (TRACE) {
            trace("Adding binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.bindings.add(binding);

        if (initialized) {
            AddBindingVisitor addBindingsVisitor = new AddBindingVisitor(binding);
            for (RootNode root : roots.keySet()) {
                visitRoot(root, addBindingsVisitor);
            }
        }

        if (TRACE) {
            trace("Added binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    void disposeBinding(EventBinding<?> binding) {
        if (TRACE) {
            trace("Dispose binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.bindings.remove(binding);
        DisposeBindingVisitor disposeVisitor = new DisposeBindingVisitor(binding);
        for (RootNode root : roots.keySet()) {
            visitRoot(root, disposeVisitor);
        }

        if (TRACE) {
            trace("Disposed binding %s, %s%n", binding.getFilter(), binding.getElement());
        }
    }

    EventChainNode installBindings(ProbeNode probeNodeImpl) {
        EventContext context = probeNodeImpl.getContext();
        SourceSection sourceSection = context.getInstrumentedSourceSection();
        if (TRACE) {
            trace("Lazy update for %s, tags %s%n", sourceSection, Arrays.toString(probeNodeImpl.getContext().getInstrumentedSourceSection().getTags()));
        }
        EventChainNode root = null;
        EventChainNode parent = null;
        for (int i = 0; i < bindings.size(); i++) {
            EventBinding<?> binding = bindings.get(i);
            if (isInstrumented(probeNodeImpl, binding, sourceSection)) {
                if (TRACE) {
                    trace("Found binding %s, %s%n", binding.getFilter(), binding.getElement());
                }
                EventChainNode next = probeNodeImpl.createEventChainCallback(binding);
                if (next == null) {
                    continue;
                }

                if (root == null) {
                    root = next;
                } else {
                    assert parent != null;
                    parent.setNext(next);
                }
                parent = next;
            }
        }

        if (TRACE) {
            trace("Lazy updated for %s, tags %s%n", sourceSection, Arrays.toString(probeNodeImpl.getContext().getInstrumentedSourceSection().getTags()));
        }
        return root;
    }

    private void initialize() {
        synchronized (this) {
            if (!initialized) {
                initialized = true;
                if (TRACE) {
                    trace("Initialize instrumentation%n");
                }
                for (AbstractInstrumenter instrumenter : instrumentations.values()) {
                    instrumenter.initialize();
                }
                if (TRACE) {
                    trace("Initialized instrumentation%n");
                }
            }
        }
    }

    private void addInstrumenter(Object key, AbstractInstrumenter instrumenter) throws AssertionError {
        if (instrumentations.containsKey(key)) {
            throw new AssertionError("Instrument already added.");
        }

        if (initialized) {
            instrumenter.initialize();
            List<EventBinding<?>> addedBindings = new ArrayList<>();
            for (EventBinding<?> binding : bindings) {
                if (binding.getInstrumenter() == instrumenter) {
                    addedBindings.add(binding);
                }
            }

            AddBindingsVisitor visitor = new AddBindingsVisitor(addedBindings);
            for (RootNode root : roots.keySet()) {
                visitRoot(root, visitor);
            }
        }
        instrumentations.put(key, instrumenter);
    }

    @SuppressWarnings("unchecked")
    private void insertWrapper(Node instrumentableNode, SourceSection sourceSection) {
        Node node = instrumentableNode;
        Node parent = node.getParent();
        if (parent instanceof WrapperNode) {
            // already wrapped, need to invalidate the wrapper something changed
            invalidateWrapperImpl((WrapperNode) parent, node);
            return;
        }
        ProbeNode probe = new ProbeNode(InstrumentationHandler.this, sourceSection);
        WrapperNode wrapper;
        try {
            Class<?> factory = null;
            Class<?> currentClass = instrumentableNode.getClass();
            while (currentClass != null) {
                Instrumentable instrumentable = currentClass.getAnnotation(Instrumentable.class);
                if (instrumentable != null) {
                    factory = instrumentable.factory();
                    break;
                }
                currentClass = currentClass.getSuperclass();
            }

            if (factory == null) {
                if (TRACE) {
                    trace("No wrapper inserted for %s, section %s. Not annotated with @Instrumentable.%n", node, sourceSection);
                }
                // node or superclass is not annotated with @Instrumentable
                return;
            }

            if (TRACE) {
                trace("Insert wrapper for %s, section %s%n", node, sourceSection);
            }

            wrapper = ((InstrumentableFactory<Node>) factory.newInstance()).createWrapper(instrumentableNode, probe);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create wrapper node. ", e);
        }

        if (!(wrapper instanceof Node)) {
            throw new IllegalStateException(String.format("Implementation of %s must be a subclass of %s.", WrapperNode.class.getSimpleName(), Node.class.getSimpleName()));
        }

        Node wrapperNode = (Node) wrapper;
        if (wrapperNode.getParent() != null) {
            throw new IllegalStateException(String.format("Instance of provided %s is already adopted by another parent.", WrapperNode.class.getSimpleName()));
        }
        if (parent == null) {
            throw new IllegalStateException(String.format("Instance of instrumentable %s is not adopted by a parent.", Node.class.getSimpleName()));
        }

        if (!node.isSafelyReplaceableBy(wrapperNode)) {
            throw new IllegalStateException(String.format("WrapperNode implementation %s cannot be safely replaced in parent node class %s.", wrapperNode.getClass().getName(),
                            parent.getClass().getName()));
        }
        node.replace(wrapperNode);
        if (node.getParent() != wrapperNode) {
            throw new IllegalStateException("InstrumentableNode must have a WrapperNode as parent after createInstrumentationWrappwer is invoked.");
        }
    }

    private <T extends EventNodeFactory> EventBinding<T> attachFactory(AbstractInstrumenter instrumenter, SourceSectionFilter filter, T factory) {
        return addBinding(new EventBinding<>(instrumenter, filter, factory));
    }

    private <T extends EventListener> EventBinding<T> attachListener(AbstractInstrumenter instrumenter, SourceSectionFilter filter, T listener) {
        return addBinding(new EventBinding<>(instrumenter, filter, listener));
    }

    private static boolean isInstrumentableNode(Node node) {
        return !(node instanceof WrapperNode) && !(node instanceof RootNode);
    }

    private static boolean isInstrumented(Node node, EventBinding<?> binding, SourceSection section) {
        return binding.getInstrumenter().isInstrumentable(node) && binding.getFilter().isInstrumented(section);
    }

    private static boolean isInstrumentedRoot(RootNode node, EventBinding<?> binding, SourceSection section) {
        return binding.getInstrumenter().isInstrumentable(node) && binding.getFilter().isInstrumentedRoot(section);
    }

    private static boolean isInstrumentedLeaf(Node node, EventBinding<?> binding, SourceSection section) {
        if (binding.getFilter().isInstrumentedNode(section)) {
            assert isInstrumented(node, binding, section);
            return true;
        }
        return false;
    }

    private static void trace(String message, Object... args) {
        PrintStream out = System.out;
        out.printf(message, args);
    }

    private static void visitRoot(final RootNode root, final AbstractNodeVisitor visitor) {
        if (TRACE) {
            trace("Visit root %s wrappers for %s%n", visitor, root.toString());
        }

        if (visitor.shouldVisit(root)) {
            // found a filter that matched
            root.atomic(new Runnable() {
                public void run() {
                    root.accept(visitor);
                }
            });
        }
        if (TRACE) {
            trace("Visited root %s wrappers for %s%n", visitor, root.toString());
        }
    }

    static void removeWrapper(ProbeNode node) {
        if (TRACE) {
            trace("Remove wrapper for %s%n", node.getContext().getInstrumentedSourceSection());
        }
        WrapperNode wrapperNode = node.findWrapper();
        ((Node) wrapperNode).replace(wrapperNode.getDelegateNode());
    }

    private static void invalidateWrapper(Node node) {
        Node parent = node.getParent();
        if (!(parent instanceof WrapperNode)) {
            // not yet wrapped
            return;
        }
        invalidateWrapperImpl((WrapperNode) parent, node);
    }

    private static void invalidateWrapperImpl(WrapperNode parent, Node node) {
        ProbeNode probeNode = parent.getProbeNode();
        if (TRACE) {
            SourceSection section = probeNode.getContext().getInstrumentedSourceSection();
            trace("Invalidate wrapper for %s, section %s tags %s%n", node, section, Arrays.toString(section.getTags()));
        }
        if (probeNode != null) {
            probeNode.invalidate();
        }
    }

    static Instrumentable getInstrumentable(Node node) {
        Instrumentable instrumentable = node.getClass().getAnnotation(Instrumentable.class);
        if (instrumentable != null && !(node instanceof WrapperNode)) {
            return instrumentable;
        }
        return null;
    }

    private abstract class AbstractNodeVisitor implements NodeVisitor {

        abstract boolean shouldVisit(RootNode root);

    }

    private abstract class AbstractBindingVisitor extends AbstractNodeVisitor {

        protected final EventBinding<?> binding;

        AbstractBindingVisitor(EventBinding<?> binding) {
            this.binding = binding;
        }

        @Override
        boolean shouldVisit(RootNode root) {
            return isInstrumentedRoot(root, binding, root.getSourceSection());
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                if (isInstrumentedLeaf(node, binding, sourceSection) && isInstrumentableNode(node)) {
                    if (TRACE) {
                        trace("Filter hit section:%s tags:%s%n", sourceSection, Arrays.toString(sourceSection.getTags()));
                    }
                    visitInstrumented(node, sourceSection);
                }
            }
            return true;
        }

        protected abstract void visitInstrumented(Node node, SourceSection section);

    }

    private abstract class AbstractBindingsVisitor extends AbstractNodeVisitor {

        private final List<EventBinding<?>> bindings;

        AbstractBindingsVisitor(List<EventBinding<?>> bindings) {
            this.bindings = bindings;
        }

        @Override
        boolean shouldVisit(RootNode root) {
            SourceSection sourceSection = root.getSourceSection();
            for (int i = 0; i < bindings.size(); i++) {
                EventBinding<?> binding = bindings.get(i);
                if (isInstrumentedRoot(root, binding, sourceSection)) {
                    return true;
                }
            }
            return false;
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                List<EventBinding<?>> b = bindings;
                for (int i = 0; i < b.size(); i++) {
                    EventBinding<?> binding = b.get(i);
                    if (isInstrumented(node, binding, sourceSection) && isInstrumentableNode(node)) {
                        if (TRACE) {
                            trace("Filter hit section:%s", sourceSection);
                        }
                        visitInstrumented(node, sourceSection);
                        break;
                    }
                }
            }
            return true;
        }

        protected abstract void visitInstrumented(Node node, SourceSection section);

    }

    /* Insert wrappers for a single bindings. */
    private final class AddBindingVisitor extends AbstractBindingVisitor {

        AddBindingVisitor(EventBinding<?> filter) {
            super(filter);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            insertWrapper(node, section);
        }

    }

    private final class DisposeBindingVisitor extends AbstractBindingVisitor {

        DisposeBindingVisitor(EventBinding<?> binding) {
            super(binding);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            invalidateWrapper(node);
        }
    }

    private final class AddBindingsVisitor extends AbstractBindingsVisitor {

        AddBindingsVisitor(List<EventBinding<?>> bindings) {
            super(bindings);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            insertWrapper(node, section);
        }
    }

    private final class DisposeBindingsVisitor extends AbstractBindingsVisitor {

        DisposeBindingsVisitor(List<EventBinding<?>> bindings) {
            super(bindings);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            invalidateWrapper(node);
        }

    }

    /**
     * Instrumenter implementation for {@link TruffleInstrument}.
     */
    final class InstrumentationInstrumenter extends AbstractInstrumenter {

        private final Class<?> instrumentationClass;
        private TruffleInstrument instrumentation;

        InstrumentationInstrumenter(Class<?> instrumentationClass) {
            this.instrumentationClass = instrumentationClass;
        }

        @Override
        boolean isInstrumentable(Node rootNode) {
            return true;
        }

        Env getEnv() {
            return env;
        }

        Class<?> getInstrumentationClass() {
            return instrumentationClass;
        }

        @Override
        void initialize() {
            if (TRACE) {
                trace("Initialize instrumentation %s class %s %n", instrumentation, instrumentationClass);
            }
            assert instrumentation == null;
            try {
                this.instrumentation = (TruffleInstrument) instrumentationClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                failInstrumentationInitialization(String.format("Failed to create new instrumenter class %s", instrumentationClass.getName()), e);
                return;
            }
            try {
                this.instrumentation.onCreate(env, this);
            } catch (Throwable e) {
                failInstrumentationInitialization(String.format("Failed calling onCreate of instrumentation class %s", instrumentationClass.getName()), e);
                return;
            }
            if (TRACE) {
                trace("Initialized instrumentation %s class %s %n", instrumentation, instrumentationClass);
            }
        }

        private void failInstrumentationInitialization(String message, Throwable t) {
            Exception exception = new Exception(message, t);
            PrintStream stream = new PrintStream(env.err());
            exception.printStackTrace(stream);
        }

        boolean isInitialized() {
            return instrumentation != null;
        }

        TruffleInstrument getInstrumentation() {
            return instrumentation;
        }

        @Override
        void dispose() {
            if (isInitialized()) {
                instrumentation.onDispose(env);
            }
        }

    }

    /**
     * Instrumenter implementation for use in {@link TruffleLanguage}.
     */
    final class LanguageInstrumenter<T> extends AbstractInstrumenter {

        private final T context;
        private final InstrumentationLanguage<T> language;

        LanguageInstrumenter(InstrumentationLanguage<T> language, T context) {
            this.language = language;
            this.context = context;
        }

        @Override
        boolean isInstrumentable(Node node) {
            if (ACCESSOR.findLanguage(node.getRootNode()) != language.getClass()) {
                return false;
            }
            // TODO (chumer) check for the context instance
            return true;
        }

        @Override
        void initialize() {
            language.installInstrumentations(context, this);
        }

        @Override
        void dispose() {
            // nothing todo
        }
    }

    /**
     * We have two APIs both need an Instrumenter implementation they slightly differ in their
     * behavior depending on the context in which they are used {@link TruffleInstrument} or
     * {@link TruffleLanguage}.
     */
    abstract class AbstractInstrumenter extends Instrumenter {

        abstract void initialize();

        abstract void dispose();

        void disposeBinding(EventBinding<?> binding) {
            InstrumentationHandler.this.disposeBinding(binding);
        }

        abstract boolean isInstrumentable(Node rootNode);

        @Override
        public final <T extends EventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter filter, T factory) {
            return InstrumentationHandler.this.attachFactory(this, filter, factory);
        }

        @Override
        public final <T extends EventListener> EventBinding<T> attachListener(SourceSectionFilter filter, T listener) {
            return InstrumentationHandler.this.attachListener(this, filter, listener);
        }

    }

    static final AccessorInstrumentHandler ACCESSOR = new AccessorInstrumentHandler();

    static final class AccessorInstrumentHandler extends Accessor {

        @SuppressWarnings("rawtypes")
        @Override
        protected Class<? extends TruffleLanguage> findLanguage(RootNode n) {
            return super.findLanguage(n);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected CallTarget parse(Class<? extends TruffleLanguage> languageClass, Source code, Node context, String... argumentNames) throws IOException {
            return super.parse(languageClass, code, context, argumentNames);
        }

        @Override
        protected Object createInstrumentationHandler(Object vm, OutputStream out, OutputStream err, InputStream in) {
            return new InstrumentationHandler(new Env(out, err, in));
        }

        @Override
        protected void addInstrumentation(Object instrumentationHandler, Object key, Class<?> instrumentationClass) {
            ((InstrumentationHandler) instrumentationHandler).addInstrumentation(key, instrumentationClass);
        }

        @Override
        protected void disposeInstrumentation(Object instrumentationHandler, Object key, boolean cleanupRequired) {
            ((InstrumentationHandler) instrumentationHandler).disposeInstrumentation(key, cleanupRequired);
        }

        @Override
        protected boolean isInstrumentable(RootNode rootNode) {
            return super.isInstrumentable(rootNode);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void attachToInstrumentation(Object vm, TruffleLanguage<?> impl, com.oracle.truffle.api.TruffleLanguage.Env env) {
            if (impl instanceof InstrumentationLanguage) {
                InstrumentationHandler instrumentationHandler = (InstrumentationHandler) ACCESSOR.getInstrumentationHandler(vm);
                instrumentationHandler.attachLanguage(findContext(env), (InstrumentationLanguage<Object>) impl);
            }
        }

        @Override
        protected void detachFromInstrumentation(Object vm, com.oracle.truffle.api.TruffleLanguage.Env env) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) ACCESSOR.getInstrumentationHandler(vm);
            instrumentationHandler.detachLanguage(findContext(env));
        }

        @Override
        protected void initializeCallTarget(RootCallTarget target) {
            Object instrumentationHandler = ACCESSOR.getInstrumentationHandler(null);
            // we want to still support cases where call targets are executed without an enclosing
            // engine.
            if (instrumentationHandler != null) {
                ((InstrumentationHandler) instrumentationHandler).installRootNode(target.getRootNode());
            }
        }
    }

}
