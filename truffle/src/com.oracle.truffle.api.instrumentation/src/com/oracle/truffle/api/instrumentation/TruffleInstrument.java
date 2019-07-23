/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * The service provider interface (SPI) for Truffle instruments: clients of Truffle instrumentation
 * that may observe and inject behavior into interpreters written using the Truffle framework.
 * <p>
 * Instrument implementation classes must use the {@link Registration} annotation to provide
 * required metadata and to enable automatic discovery of the implementation.
 * <p>
 * An instrument is {@link #onCreate(Env) created } if at least one instrument
 * {@link TruffleInstrument.Env#getOptions() option} was specified or if a
 * {@link TruffleInstrument.Env#registerService(Object) service} was looked up. The
 * {@link Instrumenter} available in the provided {@linkplain Env environment} allows the instrument
 * instance to bind listeners for {@linkplain ExecutionEventListener execution} and
 * {@linkplain LoadSourceListener source} events, as well as {@linkplain ExecutionEventNodeFactory
 * node factories} for code injection at guest language code locations.
 * <p>
 * An instrument is disposed when the associated polyglot {@linkplain Engine engine} is disposed.
 * All active bindings created by a disposed instrument become disposed automatically. The
 * {@link Instrumenter} instance available in the provided {@linkplain Env environment} may not be
 * used after disposal.
 * <p>
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
     * {@link org.graalvm.polyglot.Instrument#lookup lookup query}. For example to expose a debugger
     * one could define an abstract debugger controller:
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
     * Invoked once on an {@linkplain TruffleInstrument instance} just before all instruments and
     * languages are going to be disposed, possibly because the underlying
     * {@linkplain org.graalvm.polyglot.Engine engine} is going to be closed. This method is called
     * before {@link #onDispose(Env)} and the instrument must remain usable after finalization. The
     * instrument can prepare for disposal while still having other instruments not disposed yet.
     *
     * @param env environment information for the instrument
     * @since 19.0
     */
    protected void onFinalize(Env env) {
        // default implementation does nothing
    }

    /**
     * Invoked once on an {@linkplain TruffleInstrument instance} when it becomes disabled, possibly
     * because the underlying {@linkplain org.graalvm.polyglot.Engine engine} has been closed. A
     * disposed instance is no longer usable. If the instrument is re-enabled, the engine will
     * create a new instance.
     *
     * @param env environment information for the instrument
     * @since 0.12
     */
    protected void onDispose(Env env) {
        // default implementation does nothing
    }

    /**
     * Returns a set of option descriptors that are supported by this instrument. Option values are
     * accessible using the {@link Env#getOptions() environment} when the instrument is
     * {@link #onCreate(Env) created}. By default no options are available for an instrument.
     * Options returned by this method must specify the {@link Registration#id() instrument id} as
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
        return OptionDescriptors.EMPTY;
    }

    /**
     * Access to instrumentation services as well as input, output, and error streams.
     *
     * @since 0.12
     */
    @SuppressWarnings("static-method")
    public static final class Env {

        private final Object vmObject; // PolyglotInstrument
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final MessageTransport messageTransport;
        OptionValues options;
        InstrumentClientInstrumenter instrumenter;
        private List<Object> services;

        Env(Object vm, OutputStream out, OutputStream err, InputStream in, MessageTransport messageInterceptor) {
            this.vmObject = vm;
            this.in = in;
            this.err = err;
            this.out = out;
            this.messageTransport = messageInterceptor != null ? new MessageTransportProxy(messageInterceptor) : null;
        }

        Object getVMObject() {
            return vmObject;
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
         * Input associated with {@link org.graalvm.polyglot.Engine} this {@link TruffleInstrument
         * instrument} is being executed in.
         *
         * @return reader, never <code>null</code>
         * @since 0.12
         */
        public InputStream in() {
            return in;
        }

        /**
         * Standard output writer for {@link org.graalvm.polyglot.Engine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.12
         */
        public OutputStream out() {
            return out;
        }

        /**
         * Standard error writer for {@link org.graalvm.polyglot.Engine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.12
         */
        public OutputStream err() {
            return err;
        }

        /**
         * Start a server at the provided URI via the {@link MessageTransport} service. Before an
         * instrument creates a server endpoint for a message protocol, it needs to check the result
         * of this method. When a virtual message transport is available, it blocks until a client
         * connects and {@link MessageEndpoint} representing the peer endpoint is returned. Those
         * endpoints need to be used instead of a direct creation of a server socket. If no virtual
         * message transport is available at that URI, <code>null</code> is returned and the
         * instrument needs to set up the server itself.
         * <p>
         * When {@link org.graalvm.polyglot.io.MessageTransport.VetoException} is thrown, the server
         * creation needs to be abandoned.
         * <p>
         * This method can be called concurrently from multiple threads. However, the
         * {@link MessageEndpoint} ought to be called on one thread at a time, unless you're sure
         * that the particular implementation can handle concurrent calls. The same holds true for
         * the returned endpoint, it's called synchronously.
         *
         * @param uri the URI of the server endpoint
         * @param server the handler of messages at the server side
         * @return an implementation of {@link MessageEndpoint} call back representing the client
         *         side, or <code>null</code> when no virtual transport is available
         * @throws MessageTransport.VetoException if creation of a server at that URI is not allowed
         * @since 19.0
         */
        public MessageEndpoint startServer(URI uri, MessageEndpoint server) throws IOException, MessageTransport.VetoException {
            if (messageTransport == null) {
                return null;
            }
            return messageTransport.open(uri, server);
        }

        /**
         * Registers additional service. This method can be called multiple time, but only during
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
         * initialization of the instrument}. These services are made available to users via
         * {@link org.graalvm.polyglot.Instrument#lookup} query method.
         *
         * This method can only be called from
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)} method -
         * then the services are collected and cannot be changed anymore.
         *
         * @param service a service to be returned from associated
         *            {@link org.graalvm.polyglot.Instrument#lookup}
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
         * {@link TruffleLanguage}. The truffle language needs to be entered on the current Thread
         * otherwise an {@link AssertionError} is thrown.
         *
         * @param <S> the requested type
         * @param language identification of the language to query
         * @param type the class of the requested type
         * @return the registered service or <code>null</code> if none is found
         * @since 0.26
         */
        public <S> S lookup(LanguageInfo language, Class<S> type) {
            return InstrumentAccessor.engineAccess().lookup(language, type);
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
            Object vm = InstrumentAccessor.langAccess().getVMObject(instrument);
            if (vm == this.vmObject) {
                throw new IllegalArgumentException("Not allowed to lookup services from the currrent instrument.");
            }
            return InstrumentAccessor.engineAccess().lookup(instrument, type);
        }

        /**
         * Returns a map {@link LanguageInfo#getId() language id} to {@link LanguageInfo language
         * info} of all languages that are installed in the environment.
         *
         * @since 0.26
         */
        public Map<String, LanguageInfo> getLanguages() {
            return InstrumentAccessor.engineAccess().getInternalLanguages(vmObject);
        }

        /**
         * Returns a map {@link InstrumentInfo#getId() instrument id} to {@link InstrumentInfo
         * instrument info} of all instruments that are installed in the environment.
         *
         * @since 0.26
         */
        public Map<String, InstrumentInfo> getInstruments() {
            return InstrumentAccessor.engineAccess().getInstruments(vmObject);
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
            TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(vmObject, source.getLanguage(), source.getMimeType());
            return InstrumentAccessor.langAccess().parse(env, source, null, argumentNames);
        }

        /**
         * Parses source snippet of the node's language at the provided node location. The result is
         * an AST fragment represented by {@link ExecutableNode} that accepts frames valid at the
         * provided node location, or <code>null</code> when inline parsing is not supported by the
         * language.
         *
         * @param source a source snippet to parse at the provided node location
         * @param node a context location where the source is parsed at, must not be
         *            <code>null</code>
         * @param frame a frame location where the source is parsed at, can be <code>null</code>
         * @return the executable fragment representing the parsed result, or <code>null</code>
         * @since 0.31
         */
        public ExecutableNode parseInline(Source source, Node node, MaterializedFrame frame) {
            if (node == null) {
                throw new IllegalArgumentException("Node must not be null.");
            }
            TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(vmObject, source.getLanguage(), source.getMimeType());
            // Assert that the languages match:
            assert InstrumentAccessor.langAccess().getLanguageInfo(env) == node.getRootNode().getLanguageInfo();
            ExecutableNode fragment = InstrumentAccessor.langAccess().parseInline(env, source, node, frame);
            if (fragment != null) {
                TruffleLanguage<?> languageSPI = InstrumentAccessor.langAccess().getSPI(env);
                fragment = new GuardedExecutableNode(languageSPI, fragment, frame);
            }
            return fragment;
        }

        /**
         * Returns a {@link TruffleFile} for given path. This must be called on a context thread
         * only.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.0
         */
        public TruffleFile getTruffleFile(String path) {
            return InstrumentAccessor.engineAccess().getTruffleFile(path);
        }

        /**
         * Returns a {@link TruffleFile} for given {@link URI}. This must be called on a context
         * thread only.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.0
         */
        public TruffleFile getTruffleFile(URI uri) {
            return InstrumentAccessor.engineAccess().getTruffleFile(uri);
        }

        private static class GuardedExecutableNode extends ExecutableNode {

            private final FrameDescriptor frameDescriptor;
            @Child private ExecutableNode fragment;

            GuardedExecutableNode(TruffleLanguage<?> languageSPI, ExecutableNode fragment, MaterializedFrame frameLocation) {
                super(languageSPI);
                this.frameDescriptor = (frameLocation != null) ? frameLocation.getFrameDescriptor() : null;
                this.fragment = fragment;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                assert frameDescriptor == null || frameDescriptor == frame.getFrameDescriptor();
                assureAdopted();
                Object ret = fragment.execute(frame);
                assert checkNullOrInterop(ret);
                return ret;
            }

            private void assureAdopted() {
                if (getParent() == null) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException("Needs to be inserted into the AST before execution.");
                }
            }
        }

        private static boolean checkNullOrInterop(Object obj) {
            if (obj == null) {
                return true;
            }
            InstrumentAccessor.interopAccess().checkInteropType(obj);
            return true;
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
            return InstrumentAccessor.engineAccess().isEvalRoot(root);
        }

        /**
         * Uses the provided language to print a string representation of this value. The behavior
         * of this method is undefined if a type unknown to the language is passed as a value.
         *
         * @param language a language
         * @param value a known value of that language, must be an interop type (i.e. either
         *            implementing TruffleObject or be a primitive value)
         * @return a human readable string representation of the value.
         * @see #findLanguage(java.lang.Object)
         * @since 0.27
         */
        public String toString(LanguageInfo language, Object value) {
            InstrumentAccessor.interopAccess().checkInteropType(value);
            final TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(language);
            return InstrumentAccessor.langAccess().toStringIfVisible(env, value, false);
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
         * @param value a value to find the meta-object of, must be an interop type (i.e. either
         *            implementing TruffleObject or be a primitive value)
         * @return the meta-object, or <code>null</code>
         * @see #findLanguage(java.lang.Object)
         * @since 0.27
         */
        public Object findMetaObject(LanguageInfo language, Object value) {
            InstrumentAccessor.interopAccess().checkInteropType(value);
            final TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(language);
            Object metaObject = InstrumentAccessor.langAccess().findMetaObject(env, value);
            assert checkNullOrInterop(metaObject);
            return metaObject;
        }

        /**
         * Uses the provided language to find a source location where a value is declared, if any.
         * For the best results, use the {@link #findLanguage(java.lang.Object) value's language},
         * if any.
         *
         * @param language a language
         * @param value a value to get the source location for, must be an interop type (i.e. either
         *            implementing TruffleObject or be a primitive value)
         * @return a source location of the object, or <code>null</code>
         * @see #findLanguage(java.lang.Object)
         * @since 0.27
         */
        public SourceSection findSourceLocation(LanguageInfo language, Object value) {
            InstrumentAccessor.interopAccess().checkInteropType(value);
            final TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(language);
            return InstrumentAccessor.langAccess().findSourceLocation(env, value);
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
            return InstrumentAccessor.engineAccess().getObjectLanguage(value, vmObject);
        }

        /**
         * Returns the polyglot scope - symbols explicitly exported by languages.
         *
         * @return a read-only map of symbol names and their values
         * @since 0.30
         */
        public Map<String, ? extends Object> getExportedSymbols() {
            return InstrumentAccessor.engineAccess().getExportedSymbols(vmObject);
        }

        /**
         * Find a list of local scopes enclosing the given {@link Node node}. The scopes contain
         * variables that are valid at the provided node and that have a relation to it. Unless the
         * node is in a global scope, it is expected that there is at least one scope provided, that
         * corresponds to the enclosing function. Global top scopes are provided by
         * {@link #findTopScopes(java.lang.String)}. The iteration order corresponds with the scope
         * nesting, from the inner-most to the outer-most.
         * <p>
         * Scopes may depend on the information provided by the frame. <br/>
         * Lexical scopes are returned when <code>frame</code> argument is <code>null</code>.
         *
         * @param node a node to get the enclosing scopes for. The node needs to be inside a
         *            {@link RootNode} associated with a language.
         * @param frame The current frame the node is in, or <code>null</code> for lexical access
         *            when the program is not running, or is not suspended at the node's location.
         * @return an {@link Iterable} providing list of scopes from the inner-most to the
         *         outer-most.
         * @see TruffleLanguage#findLocalScopes(java.lang.Object, com.oracle.truffle.api.nodes.Node,
         *      com.oracle.truffle.api.frame.Frame)
         * @since 0.30
         */
        public Iterable<Scope> findLocalScopes(Node node, Frame frame) {
            RootNode rootNode = node.getRootNode();
            if (rootNode == null) {
                throw new IllegalArgumentException("The node " + node + " does not have a RootNode.");
            }
            LanguageInfo languageInfo = rootNode.getLanguageInfo();
            if (languageInfo == null) {
                throw new IllegalArgumentException("The root node " + rootNode + " does not have a language associated.");
            }
            final TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(languageInfo);
            Iterable<Scope> langScopes = InstrumentAccessor.langAccess().findLocalScopes(env, node, frame);
            assert langScopes != null : languageInfo.getId();
            return langScopes;
        }

        /**
         * Find a list of top scopes of a language. The iteration order corresponds with the scope
         * nesting, from the inner-most to the outer-most.
         *
         * @param languageId a language id.
         * @return a list of top scopes, can be empty when no top scopes are provided by the
         *         language
         * @see TruffleLanguage#findTopScopes(java.lang.Object)
         * @since 0.30
         */
        public Iterable<Scope> findTopScopes(String languageId) {
            LanguageInfo languageInfo = getLanguages().get(languageId);
            if (languageInfo == null) {
                throw new IllegalArgumentException("Unknown language: " + languageId + ". Known languages are: " + getLanguages().keySet());
            }
            final TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(languageInfo);
            return findTopScopes(env);
        }

        /**
         * Find or create an engine bound logger for an instrument. When a logging is required from
         * a thread which entered a context the context's logging handler and options are used.
         * Otherwise the engine's logging handler and options are used.
         * <p>
         * If a logger with given name already exists it's returned, otherwise a new logger is
         * created.
         * <p>
         * Unlike loggers created by
         * {@link TruffleLogger#getLogger(java.lang.String, java.lang.String)
         * TruffleLogger.getLogger} loggers created by this method are bound to engine, there may be
         * more logger instances having the same name but each bound to different engine instance.
         * Instruments should never store the returned logger into a static fields. A new logger
         * must be always created in
         * {@link TruffleInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
         * onCreate} method.
         *
         * @param loggerName the the name of a {@link TruffleLogger}, if a {@code loggerName} is
         *            null or empty a root logger for language or instrument is returned
         * @return a {@link TruffleLogger}
         * @since 19.0
         */
        public TruffleLogger getLogger(String loggerName) {
            return InstrumentAccessor.engineAccess().getLogger(vmObject, loggerName);
        }

        /**
         * Find or create an engine bound logger for an instrument. The engine bound loggers can be
         * used by threads executing without any current context. When a logging is required from a
         * thread which entered a context the context's logging handler and options are used.
         * Otherwise the engine's logging handler and options are used.
         * <p>
         * If a logger for the class already exists it's returned, otherwise a new logger is
         * created.
         * <p>
         * Unlike loggers created by
         * {@link TruffleLogger#getLogger(java.lang.String, java.lang.Class)
         * TruffleLogger.getLogger} loggers created by this method are bound to engine, there may be
         * more logger instances having the same name but each bound to different engine instance.
         * Instruments should never store the returned logger into a static fields. A new logger
         * must be always created in
         * {@link TruffleInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
         * onCreate} method.
         *
         * @param forClass the {@link Class} to create a logger for
         * @return a {@link TruffleLogger}
         * @throws NullPointerException if {@code forClass} is null
         * @since 19.0
         */
        public TruffleLogger getLogger(Class<?> forClass) {
            return getLogger(forClass.getName());
        }

        static Iterable<Scope> findTopScopes(TruffleLanguage.Env env) {
            Iterable<Scope> langScopes = InstrumentAccessor.langAccess().findTopScopes(env);
            assert langScopes != null : InstrumentAccessor.langAccess().getLanguageInfo(env).getId();
            return langScopes;
        }

        private static class MessageTransportProxy implements MessageTransport {

            private final MessageTransport transport;

            MessageTransportProxy(MessageTransport transport) {
                this.transport = transport;
            }

            @Override
            public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, VetoException {
                Objects.requireNonNull(peerEndpoint, "The peer endpoint must be non null.");
                MessageEndpoint openedEndpoint = transport.open(uri, new MessageEndpointProxy(peerEndpoint));
                if (openedEndpoint == null) {
                    return null;
                }
                return new MessageEndpointProxy(openedEndpoint);
            }

            private static class MessageEndpointProxy implements MessageEndpoint {

                private final MessageEndpoint endpoint;

                MessageEndpointProxy(MessageEndpoint endpoint) {
                    this.endpoint = endpoint;
                }

                @Override
                public void sendText(String text) throws IOException {
                    endpoint.sendText(text);
                }

                @Override
                public void sendBinary(ByteBuffer data) throws IOException {
                    endpoint.sendBinary(data);
                }

                @Override
                public void sendPing(ByteBuffer data) throws IOException {
                    endpoint.sendPing(data);
                }

                @Override
                public void sendPong(ByteBuffer data) throws IOException {
                    endpoint.sendPong(data);
                }

                @Override
                public void sendClose() throws IOException {
                    endpoint.sendClose();
                }
            }
        }
    }

    /**
     * Annotation that registers an {@link TruffleInstrument instrument} implementations for
     * automatic discovery.
     *
     * @since 0.12
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Registration {

        /**
         * A custom machine identifier for this instrument. If not defined then the fully qualified
         * class name is used.
         *
         * @since 0.12
         */
        String id() default "";

        /**
         * The name of the instrument in an arbitrary format for humans.
         *
         * @since 0.12
         */
        String name() default "";

        /**
         * The version for instrument in an arbitrary format. It inherits from
         * {@link org.graalvm.polyglot.Engine#getVersion()} by default.
         *
         * @since 0.12
         */
        String version() default "inherit";

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
         * Instruments automatically get created when their registered
         * {@link org.graalvm.polyglot.Instrument#lookup(java.lang.Class) service is requested}.
         *
         * @since 0.25
         * @return list of service types that this instrument can provide
         */
        Class<?>[] services() default {};
    }

    static {
        try {
            // Instrument is loaded by Engine which should load InstrumentationHandler
            // this is important to load the accessors properly.
            Class.forName(InstrumentationHandler.class.getName(), true, InstrumentationHandler.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
