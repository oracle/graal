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
import java.util.Map;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AccessorInstrumentHandler;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import com.oracle.truffle.api.nodes.LanguageInfo;
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
     * The method may {@link Env#registerService(java.lang.Object) register} additional
     * {@link Registration#services() services} - e.g. objects to be exposed via
     * {@link com.oracle.truffle.api.vm.PolyglotRuntime.Instrument#lookup lookup query}. For example
     * to expose a debugger one could define an abstract debugger controller:
     * </p>
     *
     * {@codesnippet DebuggerController}
     *
     * and declare it as a {@link Registration#services() service} associated with the instrument,
     * implement it, instantiate and {@link Env#registerService(java.lang.Object) register} in own's
     * instrument {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
     * onCreate} method:
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
     * @since 0.27
     * @deprecated in 0.27 use {@link #getOptionDescriptors()} instead.
     */
    @Deprecated
    protected List<OptionDescriptor> describeOptions() {
        return null;
    }

    /**
     * Returns a set of option descriptors that are supported by this instrument. Option values are
     * accessible using the {@link Env#getOptions() environment} when the instrument is
     * {@link #onCreate(Env) created}. By default no options are available for an instrument.
     * Options returned by this method must specifiy the {@link Registration#id() instrument id} as
     * {@link OptionDescriptor#getName() name} prefix for each option. For example if the id of the
     * instrument is "debugger" then a valid option name would be "debugger.Enabled". The instrument
     * will automatically be {@link #onCreate(Env) created} if one of the specified options was
     * provided by the engine. To construct option descriptors from a list then
     * {@link OptionDescriptors#create(List)} can be used.
     *
     * @see Option For an example of declaring the option descriptor using an annotation.
     * @since 0.27
     */
    protected OptionDescriptors getOptionDescriptors() {
        return OptionDescriptors.create(describeOptions());
    }

    /**
     * Access to instrumentation services as well as input, output, and error streams.
     *
     * @since 0.12
     */
    @SuppressWarnings("static-method")
    public static final class Env {

        private final Object vmObject; // PolyglotRuntime.Instrument
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        OptionValues options;
        InstrumentClientInstrumenter instrumenter;
        private List<Object> services;

        Env(Object vm, OutputStream out, OutputStream err, InputStream in) {
            this.vmObject = vm;
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

        /**
         * Queries a {@link TruffleLanguage language implementation} for a special service. The
         * services can be provided by the language by directly implementing them when subclassing
         * {@link TruffleLanguage}.
         *
         * @param <S> the requested type
         * @param language identification of the language to query
         * @param type the class of the requested type
         * @return the registered service or <code>null</code> if none is found
         * @since 0.26
         */
        public <S> S lookup(LanguageInfo language, Class<S> type) {
            return AccessorInstrumentHandler.engineAccess().lookup(language, type);
        }

        /**
         * Returns an additional service provided by this instrument, specified by type. If an
         * instrument is not enabled, it will be enabled automatically by requesting a supported
         * service. If the instrument does not provide a service for a given type it will not be
         * enabled automatically. An {@link IllegalArgumentException} is thrown if a service is
         * looked up from the current instrument.
         *
         * @param <S> the requested type
         * @param instrument identification of the instrument to query
         * @param type the class of the requested type
         * @return the registered service or <code>null</code> if none is found
         * @since 0.26
         */
        public <S> S lookup(InstrumentInfo instrument, Class<S> type) {
            Object vm = AccessorInstrumentHandler.langAccess().getVMObject(instrument);
            if (vm == this.vmObject) {
                throw new IllegalArgumentException("Not allowed to lookup services from the currrent instrument.");
            }
            return AccessorInstrumentHandler.engineAccess().lookup(instrument, type);
        }

        /**
         * Returns a map mime-type to language identifier of all languages that are installed in the
         * environment.
         *
         * @since 0.26
         */
        public Map<String, LanguageInfo> getLanguages() {
            return AccessorInstrumentHandler.engineAccess().getLanguages(vmObject);
        }

        /**
         * Returns a map mime-type to instrument identifier of all instruments that are installed in
         * the environment.
         *
         * @since 0.26
         */
        public Map<String, InstrumentInfo> getInstruments() {
            return AccessorInstrumentHandler.engineAccess().getInstruments(vmObject);
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
         * Returns option values for the options described in
         * {@link TruffleLanguage#getOptionDescriptors()}. The returned options are never
         * <code>null</code>.
         *
         * @since 0.27
         */
        public OptionValues getOptions() {
            return options;
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
            TruffleLanguage.Env env = AccessorInstrumentHandler.engineAccess().getEnvForInstrument(vmObject, source.getMimeType());
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
         * @deprecated use
         *             {@link #toString(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object)}
         *             and retrieve {@link LanguageInfo} from
         *             <code>node.getRootNode().getLanguageInfo()</code>.
         */
        @Deprecated
        public String toString(Node node, Object value) {
            final TruffleLanguage.Env env = getLangEnv(node);
            return AccessorInstrumentHandler.langAccess().toStringIfVisible(env, value, false);
        }

        /**
         * Uses the provided language to print a string representation of this value. The behavior
         * of this method is undefined if a type unknown to the language is passed as a value.
         *
         * @param language a language
         * @param value a known value of that language
         * @return a human readable string representation of the value.
         * @see #findLanguage(java.lang.Object)
         * @since 0.27
         */
        public String toString(LanguageInfo language, Object value) {
            final TruffleLanguage.Env env = AccessorInstrumentHandler.engineAccess().getEnvForInstrument(language);
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
         * @deprecated use
         *             {@link #findMetaObject(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object)}
         *             and retrieve {@link LanguageInfo} from
         *             <code>node.getRootNode().getLanguageInfo()</code>.
         */
        @Deprecated
        public Object findMetaObject(Node node, Object value) {
            final TruffleLanguage.Env env = getLangEnv(node);
            return AccessorInstrumentHandler.langAccess().findMetaObject(env, value);
        }

        /**
         * Uses the provided language to find a meta-object of a value, if any. The meta-object
         * represents a description of the object, reveals it's kind and it's features. Some
         * information that a meta-object might define includes the base object's type, interface,
         * class, methods, attributes, etc. When no meta-object is known, <code>null</code> is
         * returned. For the best results, use the {@link #findLanguage(java.lang.Object) value's
         * language}, if any.
         *
         * @param language a language
         * @param value a value to find the meta-object of
         * @return the meta-object, or <code>null</code>
         * @see #findLanguage(java.lang.Object)
         * @since 0.27
         */
        public Object findMetaObject(LanguageInfo language, Object value) {
            final TruffleLanguage.Env env = AccessorInstrumentHandler.engineAccess().getEnvForInstrument(language);
            return AccessorInstrumentHandler.langAccess().findMetaObject(env, value);
        }

        /**
         * Find a source location where a value is declared, if any.
         *
         * @param node a node
         * @param value a value to get the source location for
         * @return a source location of the object, or <code>null</code>
         * @since 0.22
         * @deprecated use
         *             {@link #findSourceLocation(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object)}
         *             and retrieve {@link LanguageInfo} from
         *             <code>node.getRootNode().getLanguageInfo()</code>.
         */
        @Deprecated
        public SourceSection findSourceLocation(Node node, Object value) {
            final TruffleLanguage.Env env = getLangEnv(node);
            return AccessorInstrumentHandler.langAccess().findSourceLocation(env, value);
        }

        /**
         * Uses the provided language to find a source location where a value is declared, if any.
         * For the best results, use the {@link #findLanguage(java.lang.Object) value's language},
         * if any.
         *
         * @param language a language
         * @param value a value to get the source location for
         * @return a source location of the object, or <code>null</code>
         * @see #findLanguage(java.lang.Object)
         * @since 0.27
         */
        public SourceSection findSourceLocation(LanguageInfo language, Object value) {
            final TruffleLanguage.Env env = AccessorInstrumentHandler.engineAccess().getEnvForInstrument(language);
            return AccessorInstrumentHandler.langAccess().findSourceLocation(env, value);
        }

        /**
         * Find a language that created the value, if any. This method will return <code>null</code>
         * for values representing a primitive value, or objects that are not associated with any
         * language.
         *
         * @param value the value to find a language of
         * @return the language, or <code>null</code> when there is no language associated with the
         *         value.
         * @since 0.27
         */
        public LanguageInfo findLanguage(Object value) {
            if (value == null ||
                            value instanceof Boolean ||
                            value instanceof Byte ||
                            value instanceof Short ||
                            value instanceof Integer ||
                            value instanceof Long ||
                            value instanceof Float ||
                            value instanceof Double ||
                            value instanceof Character ||
                            value instanceof String) {
                return null;
            }
            return AccessorInstrumentHandler.engineAccess().getObjectLanguage(value, vmObject);
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

        /**
         * Specifies whether the instrument is accessible using the polyglot API. Internal
         * instruments are only accessible from other instruments or guest languages.
         *
         * @since 0.27
         */
        boolean internal() default false;

        /**
         * Declarative list of classes this instrument is known to provide. The instrument is
         * supposed to override its
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) onCreate}
         * method and instantiate and {@link Env#registerService(java.lang.Object) register} all
         * here in defined services.
         * <p>
         * Instruments
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#setEnabled(boolean) get
         * automatically enabled} when their registered
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup(java.lang.Class)
         * service is requested}.
         *
         * @since 0.25
         * @return list of service types that this instrument can provide
         */
        Class<?>[] services() default {};
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
