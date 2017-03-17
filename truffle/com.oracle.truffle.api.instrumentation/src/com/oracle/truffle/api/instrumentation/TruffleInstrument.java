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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.LanguageInfo;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AccessorInstrumentHandler;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * The service provider interface (SPI) for Truffle
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Instrument Instruments}: clients of Truffle
 * instrumentation that may observe and inject behavior into interpreters written using the Truffle
 * framework.
 * <p>
 * Each registered instrument can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Instrument#setEnabled(boolean)
 * enabled/disabled} multiple times during the lifetime of a
 * {@link com.oracle.truffle.api.vm.PolyglotEngine PolyglotEngine}, but there is never more than one
 * instance per engine. A new {@link TruffleInstrument} instance is created each time the instrument
 * is enabled, and the currently enabled instance is disposed when the instrument is disabled.
 * </p>
 * <h4>Registration</h4>
 * <p>
 * Instrument implementation classes must use the {@link Registration} annotation to provide
 * required metadata and to enable automatic discovery of the implementation.
 * </p>
 * <h4>Instrument Creation</h4>
 * <ul>
 * <li>When an instrument becomes
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Instrument#setEnabled(boolean) enabled}, a
 * new instance is created and notified once via {@link #onCreate(Env)}.</li>
 * <li>The {@link Instrumenter} available in the provided {@linkplain Env environment} allows the
 * instrument instance to bind listeners for {@linkplain ExecutionEventListener execution} and
 * {@linkplain LoadSourceListener source} events, as well as {@linkplain ExecutionEventNodeFactory
 * node factories} for code injection at guest language code locations.</li>
 * </ul>
 * <h4>Instrument Disposal</h4>
 * <ul>
 * <li>When an instrument becomes
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Instrument#setEnabled(boolean) disabled},
 * the current instance is notified once via {@link #onDispose(Env)}.</li>
 * <li>All active bindings created by a disposed instrument become disposed automatically.</li>
 * <li>The {@link Instrumenter} instance available in the provided {@linkplain Env environment} may
 * not be used after disposal.</li>
 * <li>All enabled instruments in an engine become disabled automatically when the engine is
 * disposed.</li>
 * </ul>
 * <h4>Example for a simple expression coverage instrument:</h4>
 * {@codesnippet com.oracle.truffle.api.instrumentation.test.examples.CoverageExample}
 *
 * @since 0.12
 */
public abstract class TruffleInstrument {
    /**
     * Constructor for subclasses.
     *
     * @since 0.12
     */
    protected TruffleInstrument() {
    }

    /**
     * Invoked once on each newly allocated {@link TruffleInstrument} instance.
     * <p>
     * This method is an opportunity for the instrument to
     * {@link Env#registerService(java.lang.Object) register} additional <em>services</em>: objects
     * exposed via {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup} query.
     * <p>
     * For example, the following code defines an abstract debugger controller that could be made
     * available as a service:
     * </p>
     *
     * {@codesnippet DebuggerController}
     *
     * The following code {@link Env#registerService(java.lang.Object) registers} a
     * {@link TruffleInstrument} class that implements the controller (details not shown) and
     * instantiates the controller during a call to
     * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) onCreate(Env)}
     * .
     *
     * {@codesnippet DebuggerExample}
     *
     * @param env environment information for the instrument
     *
     * @see Env#getInstrumenter()
     * @since 0.12
     */
    protected abstract void onCreate(Env env);

    /**
     * Invoked once on an {@linkplain TruffleInstrument instance} when it becomes
     * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Instrument#setEnabled(boolean) disabled}
     * , possibly because the underlying {@linkplain com.oracle.truffle.api.vm.PolyglotEngine
     * engine} has been disposed. A disposed instance is no longer usable. If the instrument is
     * re-enabled, the engine will create a new instance.
     *
     * @param env environment information for the instrument
     * @since 0.12
     */
    protected void onDispose(Env env) {
        // default implementation does nothing
    }

    /**
     * Access to instrumentation services as well as input, output, and error streams.
     *
     * @since 0.12
     */
    public static final class Env {

        private final Object vm;
        private final Instrumenter instrumenter;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private List<Object> services;

        Env(Object vm, Instrumenter instrumenter, OutputStream out, OutputStream err, InputStream in) {
            this.vm = vm;
            this.instrumenter = instrumenter;
            this.in = in;
            this.err = err;
            this.out = out;
        }

        /**
         * Returns the instrumenter which lets you instrument guest language ASTs.
         *
         * @see Instrumenter
         * @since 0.12
         */
        public Instrumenter getInstrumenter() {
            return instrumenter;
        }

        /**
         * Input associated with {@link com.oracle.truffle.api.vm.PolyglotEngine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return reader, never <code>null</code>
         * @since 0.12
         */
        public InputStream in() {
            return in;
        }

        /**
         * Standard output writer for {@link com.oracle.truffle.api.vm.PolyglotEngine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.12
         */
        public OutputStream out() {
            return out;
        }

        /**
         * Standard error writer for {@link com.oracle.truffle.api.vm.PolyglotEngine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.12
         */
        public OutputStream err() {
            return err;
        }

        /**
         * Registers additional service. This method can be called multiple time, but only during
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
         * initialization of the instrument}. These services are made available to users via
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup} query method.
         *
         * This method can only be called from
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)} method -
         * then the services are collected and cannot be changed anymore.
         *
         * @param service a service to be returned from associated
         *            {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup}
         * @throws IllegalStateException if the method is called later than from
         *             {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) }
         *             method
         * @since 0.12
         */
        public void registerService(Object service) {
            if (services == null) {
                throw new IllegalStateException();
            }
            services.add(service);
        }

        Object[] onCreate(TruffleInstrument instrument) {
            List<Object> arr = new ArrayList<>();
            services = arr;
            try {
                instrument.onCreate(this);
            } finally {
                services = null;
            }
            return arr.toArray();
        }

        /**
         * Evaluates source of (potentially different) language using the current context.The names
         * of arguments are parameters for the resulting {#link CallTarget} that allow the
         * <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @throws IOException if the parsing or evaluation fails for some reason
         * @since 0.12
         */
        public CallTarget parse(Source source, String... argumentNames) throws IOException {
            TruffleLanguage.Env env = AccessorInstrumentHandler.engineAccess().getEnvForInstrument(vm, source.getMimeType());
            return AccessorInstrumentHandler.langAccess().parse(env, source, null, argumentNames);
        }

        /**
         * Returns <code>true</code> if the given root node is considered an engine evaluation root
         * for the current execution context. Multiple such root nodes can appear on stack frames
         * returned by
         * {@link TruffleRuntime#iterateFrames(com.oracle.truffle.api.frame.FrameInstanceVisitor)}.
         * A debugger implementation might use this information to hide stack frames of other
         * engines.
         *
         * @param root the root node to check
         * @return <code>true</code> if engine root else <code>false</code>
         * @since 0.17
         */
        @SuppressWarnings("static-method")
        public boolean isEngineRoot(RootNode root) {
            return AccessorInstrumentHandler.engineAccess().isEvalRoot(root);
        }

        /**
         * Uses the original language of the node to print a string representation of this value.
         * The behavior of this method is undefined if a type unknown to the language is passed as
         * value.
         *
         * @param node a node
         * @param value a known value of that language
         * @return a human readable string representation of the value.
         * @since 0.17
         */
        @SuppressWarnings("static-method")
        public String toString(Node node, Object value) {
            final TruffleLanguage.Env env = getLangEnv(node);
            return AccessorInstrumentHandler.langAccess().toStringIfVisible(env, value, false);
        }

        /**
         * Find a meta-object of a value, if any. The meta-object represents a description of the
         * object, reveals it's kind and it's features. Some information that a meta-object might
         * define includes the base object's type, interface, class, methods, attributes, etc. When
         * no meta-object is known, <code>null</code> is returned.
         *
         * @param node a node
         * @param value a value to find the meta-object of
         * @return the meta-object, or <code>null</code>
         * @since 0.22
         */
        @SuppressWarnings("static-method")
        public Object findMetaObject(Node node, Object value) {
            final TruffleLanguage.Env env = getLangEnv(node);
            return AccessorInstrumentHandler.langAccess().findMetaObject(env, value);
        }

        /**
         * Find a source location where a value is declared, if any.
         *
         * @param node a node
         * @param value a value to get the source location for
         * @return a source location of the object, or <code>null</code>
         * @since 0.22
         */
        @SuppressWarnings("static-method")
        public SourceSection findSourceLocation(Node node, Object value) {
            final TruffleLanguage.Env env = getLangEnv(node);
            return AccessorInstrumentHandler.langAccess().findSourceLocation(env, value);
        }

        private static TruffleLanguage.Env getLangEnv(Node node) {
            LanguageInfo languageInfo = node.getRootNode().getLanguageInfo();
            if (languageInfo == null) {
                throw new IllegalArgumentException("No language available for given node.");
            }
            return AccessorInstrumentHandler.engineAccess().getEnvForInstrument(languageInfo);
        }

    }

    /**
     * Annotation that registers an {@link TruffleInstrument instrument} implementations for
     * automatic discovery.
     *
     * @since 0.12
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {

        /**
         * A custom machine identifier for this instrument. If not defined then the fully qualified
         * class name is used.
         */
        String id() default "";

        /**
         * The name of the instrument in an arbitrary format for humans.
         */
        String name() default "";

        /**
         * The version for instrument in an arbitrary format.
         */
        String version() default "";

    }

    static {
        try {
            // Instrument is loaded by PolyglotEngine which should load InstrumentationHandler
            // this is important to load the accessors properly.
            Class.forName(InstrumentationHandler.class.getName(), true, InstrumentationHandler.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
