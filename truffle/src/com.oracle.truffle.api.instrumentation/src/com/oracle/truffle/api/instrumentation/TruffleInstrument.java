/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.instrumentation.InstrumentAccessor.ENGINE;

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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.Interruptible;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

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

    List<ContextThreadLocal<?>> contextThreadLocals;
    List<ContextLocal<?>> contextLocals;

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
     * <p>
     * If this method throws an {@link com.oracle.truffle.api.exception.AbstractTruffleException}
     * the exception interop messages are executed without a context being entered.
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
     * <p>
     * By default option descriptors may only be specified per engine or bound engine, but option
     * values may also be specified per context. In this case the context specific options can be
     * specified with {@link #getContextOptionDescriptors()} and the values can be accessed with
     * {@link Env#getOptions(TruffleContext)}.
     *
     * @see Option For an example of declaring the option descriptor using an annotation.
     * @since 0.27
     */
    protected OptionDescriptors getOptionDescriptors() {
        return OptionDescriptors.EMPTY;
    }

    /**
     * Returns a set of option descriptors for instrument options that can be specified per context.
     * This can be specified in addition to options specified on the engine level, instruments may
     * specify options for each context. Option descriptors specified per context must not overlap
     * with option descriptors specified per instrument instance.
     * <p>
     * Example usage:
     *
     * <pre>
     *
     * &#64;Option.Group(MyInstrument.ID)
     * final class MyContext {
     *
     *     &#64;Option(category = OptionCategory.EXPERT, help = "Description...")
     *     static final OptionKey<Boolean> MyContextOption = new OptionKey<>(Boolean.FALSE);
     * }
     *
     * &#64;Registration(...)
     * class MyInstrument extends TruffleInstrument {
     *
     *   static final OptionDescriptors CONTEXT_OPTIONS = new MyContextOptionDescriptors();
     *
     *   //...
     *
     *   protected OptionDescriptors getContextOptionDescriptors() {
     *      return CONTEXT_OPTIONS;
     *   }
     * }
     * </pre>
     *
     * @see Env#getOptions(TruffleContext) to lookup the option values for a context.
     * @since 20.3
     */
    protected OptionDescriptors getContextOptionDescriptors() {
        return OptionDescriptors.EMPTY;
    }

    /**
     * Creates a new context local reference for this instrument. Context locals for instruments
     * allow to store additional top-level values for each context similar to language contexts.
     * This enables instruments to use context local values just as languages using their language
     * context. Context local factories are guaranteed to be invoked after the instrument is
     * {@link #onCreate(Env) created}.
     * <p>
     * Context local references must be created during the invocation in the
     * {@link TruffleInstrument} constructor. Calling this method at a later point in time will
     * throw an {@link IllegalStateException}. For each registered {@link TruffleInstrument}
     * subclass it is required to always produce the same number of context local references. The
     * values produced by the factory must not be <code>null</code> and use a stable exact value
     * type for each instance of a registered instrument class. If the return value of the factory
     * is not stable or <code>null</code> then an {@link IllegalStateException} is thrown. These
     * restrictions allow the Truffle runtime to read the value more efficiently.
     * <p>
     * Usage example:
     *
     * <pre>
     * &#64;TruffleInstrument.Registration(id = "example", name = "Example Instrument")
     * public static class ExampleInstrument extends TruffleInstrument {
     *
     *     final ContextLocal<ExampleLocal> local = createContextLocal(ExampleLocal::new);
     *
     *     &#64;Override
     *     protected void onCreate(Env env) {
     *         ExecutionEventListener listener = new ExecutionEventListener() {
     *             public void onEnter(EventContext context, VirtualFrame frame) {
     *                 ExampleLocal value = local.get();
     *                 // use context local value;
     *             }
     *
     *             public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
     *             }
     *
     *             public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
     *             }
     *         };
     *
     *         env.getInstrumenter().attachExecutionEventListener(
     *                         SourceSectionFilter.ANY,
     *                         listener);
     *     }
     *
     *     static class ExampleLocal {
     *
     *         final TruffleContext context;
     *
     *         ExampleLocal(TruffleContext context) {
     *             this.context = context;
     *         }
     *
     *     }
     *
     * }
     * </pre>
     *
     * @since 20.3
     */
    protected final <T> ContextLocal<T> createContextLocal(ContextLocalFactory<T> factory) {
        ContextLocal<T> local = ENGINE.createInstrumentContextLocal(factory);
        if (contextLocals == null) {
            contextLocals = new ArrayList<>();
        }
        try {
            contextLocals.add(local);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("The set of context locals is frozen. Context locals can only be created during construction of the TruffleInstrument subclass.");
        }
        return local;
    }

    /**
     * Creates a new context thread local reference for this Truffle language. Context thread locals
     * for languages allow to store additional top-level values for each context and thread. The
     * factory may be invoked on any thread other than the thread of the context thread local value.
     * Context thread local factories are guaranteed to be invoked after the instrument is
     * {@link #onCreate(Env) created}.
     * <p>
     * Context thread local references must be created during the invocation in the
     * {@link TruffleLanguage} constructor. Calling this method at a later point in time will throw
     * an {@link IllegalStateException}. For each registered {@link TruffleLanguage} subclass it is
     * required to always produce the same number of context thread local references. The values
     * produces by the factory must not be <code>null</code> and use a stable exact value type for
     * each instance of a registered language class. If the return value of the factory is not
     * stable or <code>null</code> then an {@link IllegalStateException} is thrown. These
     * restrictions allow the Truffle runtime to read the value more efficiently.
     * <p>
     * Context thread locals should not contain a strong reference to the provided thread. Use a
     * weak reference instance for that purpose.
     * <p>
     * Usage example:
     *
     * <pre>
     * &#64;TruffleInstrument.Registration(id = "example", name = "Example Instrument")
     * public static class ExampleInstrument extends TruffleInstrument {
     *
     *     final ContextThreadLocal<ExampleLocal> local = createContextThreadLocal(ExampleLocal::new);
     *
     *     &#64;Override
     *     protected void onCreate(Env env) {
     *         ExecutionEventListener listener = new ExecutionEventListener() {
     *             public void onEnter(EventContext context, VirtualFrame frame) {
     *                 ExampleLocal value = local.get();
     *                 // use thread local value;
     *                 assert value.thread.get() == Thread.currentThread();
     *             }
     *
     *             public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
     *             }
     *
     *             public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
     *             }
     *         };
     *
     *         env.getInstrumenter().attachExecutionEventListener(
     *                         SourceSectionFilter.ANY,
     *                         listener);
     *     }
     *
     *     static class ExampleLocal {
     *
     *         final TruffleContext context;
     *         final WeakReference<Thread> thread;
     *
     *         ExampleLocal(TruffleContext context, Thread thread) {
     *             this.context = context;
     *             this.thread = new WeakReference<>(thread);
     *         }
     *     }
     *
     * }
     * </pre>
     *
     * @since 20.3
     */
    protected final <T> ContextThreadLocal<T> createContextThreadLocal(ContextThreadLocalFactory<T> factory) {
        ContextThreadLocal<T> local = ENGINE.createInstrumentContextThreadLocal(factory);
        if (contextThreadLocals == null) {
            contextThreadLocals = new ArrayList<>();
        }
        try {
            contextThreadLocals.add(local);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("The set of context thread locals is frozen. Context thread locals can only be created during construction of the TruffleInstrument subclass.");
        }
        return local;
    }

    /**
     * Context local factory for Truffle instruments. Creates a new value per context.
     *
     * @since 20.3
     */
    @FunctionalInterface
    protected interface ContextLocalFactory<T> {

        /**
         * Returns a new value for a context local of an instrument. The returned value must not be
         * <code>null</code> and must return a stable and exact type per registered instrument. A
         * thread local must always return the same {@link Object#getClass() class}, even for
         * multiple instances of the same {@link TruffleInstrument}. If this method throws an
         * {@link com.oracle.truffle.api.exception.AbstractTruffleException} the exception interop
         * messages may be executed without a context being entered.
         *
         * @see TruffleInstrument#createContextLocal(ContextLocalFactory)
         * @since 20.3
         */
        T create(TruffleContext context);
    }

    /**
     * Context local factory for Truffle instruments. Creates a new value per context and thread.
     *
     * @since 20.3
     */
    @FunctionalInterface
    protected interface ContextThreadLocalFactory<T> {

        /**
         * Returns a new value for a context thread local for a context and thread. The returned
         * value must not be <code>null</code> and must return a stable and exact type per
         * registered instrument. A thread local must always return the same
         * {@link Object#getClass() class}, even for multiple instances of the same
         * {@link TruffleInstrument}. If this method throws an
         * {@link com.oracle.truffle.api.exception.AbstractTruffleException} the exception interop
         * messages may be executed without a context being entered.
         *
         * @see TruffleInstrument#createContextThreadLocal(ContextThreadLocalFactory)
         * @since 20.3
         */
        T create(TruffleContext context, Thread thread);
    }

    /**
     * Access to instrumentation services as well as input, output, and error streams.
     *
     * @since 0.12
     */
    @SuppressWarnings("static-method")
    public static final class Env {

        private final Object polyglotInstrument;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final MessageTransport messageTransport;
        OptionValues options;
        InstrumentClientInstrumenter instrumenter;
        private List<Object> services;

        Env(Object polyglotInstrument, OutputStream out, OutputStream err, InputStream in, MessageTransport messageInterceptor) {
            this.polyglotInstrument = polyglotInstrument;
            this.in = in;
            this.err = err;
            this.out = out;
            this.messageTransport = messageInterceptor != null ? new MessageTransportProxy(messageInterceptor) : null;
        }

        Object getPolyglotInstrument() {
            return polyglotInstrument;
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

        @SuppressWarnings("unchecked")
        @TruffleBoundary
        static <T extends RuntimeException> RuntimeException engineToInstrumentException(Throwable t) {
            return InstrumentAccessor.engineAccess().engineToInstrumentException(t);
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
            try {
                return InstrumentAccessor.engineAccess().lookup(language, type);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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
            try {
                Object vm = InstrumentAccessor.langAccess().getPolyglotInstrument(instrument);
                if (vm == this.polyglotInstrument) {
                    throw new IllegalArgumentException("Not allowed to lookup services from the currrent instrument.");
                }
                return InstrumentAccessor.engineAccess().lookup(instrument, type);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Returns a map {@link LanguageInfo#getId() language id} to {@link LanguageInfo language
         * info} of all languages that are installed in the environment.
         *
         * @since 0.26
         */
        public Map<String, LanguageInfo> getLanguages() {
            try {
                return InstrumentAccessor.engineAccess().getInternalLanguages(polyglotInstrument);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Returns a map {@link InstrumentInfo#getId() instrument id} to {@link InstrumentInfo
         * instrument info} of all instruments that are installed in the environment.
         *
         * @since 0.26
         */
        public Map<String, InstrumentInfo> getInstruments() {
            try {
                return InstrumentAccessor.engineAccess().getInstruments(polyglotInstrument);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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
         * Returns the context independent option values for the options described in
         * {@link TruffleInstrument#getOptionDescriptors()}. The returned options are never
         * <code>null</code>.
         *
         * @see #getOptions(TruffleContext) to return the context specific options for this
         *      instrument.
         * @since 0.27
         */
        public OptionValues getOptions() {
            return options;
        }

        /**
         * Returns the context specific option values for the options described in
         * {@link TruffleInstrument#getContextOptionDescriptors()} and
         * {@link TruffleInstrument#getOptionDescriptors()}. Instrument context options can be
         * different for each TruffleContext, whereas regular options cannot.
         *
         * @see #getOptions() to get the context independent options set for this instrument
         * @since 20.3
         */
        @TruffleBoundary
        public OptionValues getOptions(TruffleContext context) {
            Objects.requireNonNull(context);
            return InstrumentAccessor.ENGINE.getInstrumentContextOptions(polyglotInstrument, InstrumentAccessor.LANGUAGE.getPolyglotContext(context));
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
         * @throws SecurityException
         * @since 0.12
         */
        public CallTarget parse(Source source, String... argumentNames) throws IOException {
            try {
                TruffleLanguage.Env env = InstrumentAccessor.ENGINE.getEnvForInstrument(source.getLanguage(), source.getMimeType());
                Object languageContext = InstrumentAccessor.LANGUAGE.getPolyglotLanguageContext(env);
                return InstrumentAccessor.ENGINE.parseForLanguage(languageContext, source, argumentNames, true);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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
            try {
                if (node == null) {
                    throw new IllegalArgumentException("Node must not be null.");
                }
                TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(source.getLanguage(), source.getMimeType());
                // Assert that the languages match:
                assert InstrumentAccessor.langAccess().getLanguageInfo(env) == node.getRootNode().getLanguageInfo();
                ExecutableNode fragment = InstrumentAccessor.langAccess().parseInline(env, source, node, frame);
                if (fragment != null) {
                    TruffleLanguage<?> languageSPI = InstrumentAccessor.langAccess().getSPI(env);
                    fragment = new GuardedExecutableNode(languageSPI, fragment, frame);
                }
                return fragment;
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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
            try {
                return InstrumentAccessor.engineAccess().getTruffleFile(path);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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
            try {
                return InstrumentAccessor.engineAccess().getTruffleFile(uri);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Returns the entered {@link TruffleContext} or {@code null} when no context is entered.
         *
         * @since 20.3
         */
        public TruffleContext getEnteredContext() {
            return InstrumentAccessor.ENGINE.getCurrentCreatorTruffleContext();
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
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
            try {
                return InstrumentAccessor.engineAccess().isEvalRoot(root);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Request for languages to provide stack frames of scheduled asynchronous execution.
         * Languages might not provide asynchronous stack frames by default for performance reasons.
         * At most <code>depth</code> asynchronous stack frames are asked for. When multiple
         * instruments call this method, the languages get a maximum depth of these calls and may
         * therefore provide longer asynchronous stacks than requested. Also, languages may provide
         * asynchronous stacks if it's of no performance penalty, or if requested by other options.
         * <p/>
         * Asynchronous stacks can then be accessed via
         * {@link TruffleStackTrace#getAsynchronousStackTrace(CallTarget, Frame)}.
         *
         * @param depth the requested stack depth, 0 means no asynchronous stack frames are
         *            required.
         * @see TruffleStackTrace#getAsynchronousStackTrace(CallTarget, Frame)
         * @since 20.1.0
         */
        public void setAsynchronousStackDepth(int depth) {
            InstrumentAccessor.engineAccess().setAsynchronousStackDepth(polyglotInstrument, depth);
        }

        /**
         * Returns the {@link LanguageInfo language info} for a given language class if available.
         * Language classes are typically obtained by invoking the
         * {@link InteropLibrary#getLanguage(Object)} message. Throws an
         * {@link IllegalArgumentException} if the provided language is not registered. Note that
         * languages may be returned that are not contained in {@link #getLanguages()}. For example,
         * values originating from the embedder like Java classes or {@link Proxy polyglot proxies}.
         *
         * @param languageClass the language class to convert
         * @return the associated language info
         * @throws IllegalArgumentException if the language class is not valid.
         *
         * @since 20.1
         */
        @TruffleBoundary
        public LanguageInfo getLanguageInfo(Class<? extends TruffleLanguage<?>> languageClass) {
            try {
                Objects.requireNonNull(languageClass);
                return InstrumentAccessor.engineAccess().getLanguageInfo(polyglotInstrument, languageClass);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Wraps the provided value to provide language specific information for primitive and
         * foreign values. A typical implementation of a given language for this method does the
         * following:
         * <ul>
         * <li>Return the current language as their associated
         * {@link com.oracle.truffle.api.interop.InteropLibrary#getLanguage(Object) language}.
         * <li>Provide a language specific
         * {@link com.oracle.truffle.api.interop.InteropLibrary#toDisplayString(Object) display
         * string} for primitive and foreign values.
         * <li>Return a language specific
         * {@link com.oracle.truffle.api.interop.InteropLibrary#getMetaObject(Object) metaobject}
         * primitive or foreign values.
         * <li>Add members to the object that would implicitly be available for all objects. For
         * example, any JavaScript object is expected to have a prototype member. Foreign objects,
         * even if they do not have such a member, are interpreted as if they have.
         * </ul>
         *
         * @param language the language to provide the view for
         * @param value the value to language specific information for.
         * @see TruffleLanguage#getLanguageView(Object, Object)
         * @since 20.1
         */
        @TruffleBoundary
        public Object getLanguageView(LanguageInfo language, Object value) {
            try {
                Objects.requireNonNull(language);
                return InstrumentAccessor.engineAccess().getLanguageView(language, value);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Returns the polyglot scope - symbols explicitly exported by languages. The polyglot
         * bindings of the current entered context are returned.
         *
         * @return an interop object having the symbol names as properties
         * @since 20.1
         */
        public Object getPolyglotBindings() {
            try {
                return InstrumentAccessor.engineAccess().getPolyglotBindingsObject();
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
        }

        /**
         * Provides top scope object of the language, if any. Uses the current context to find the
         * language scope associated with. The returned object is an
         * {@link com.oracle.truffle.api.interop.InteropLibrary#isScope(Object) interop scope
         * object}, or <code>null</code>.
         *
         * @param language a language
         * @return the top scope, or <code>null</code> if the language does not support such concept
         * @see TruffleLanguage#getScope(Object)
         * @since 20.3
         */
        public Object getScope(LanguageInfo language) {
            assert language != null;
            try {
                final TruffleLanguage.Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(language);
                return InstrumentAccessor.langAccess().getScope(env);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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
            try {
                return InstrumentAccessor.engineAccess().getLogger(polyglotInstrument, loggerName);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
            }
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

        /**
         * Returns heap memory size retained by a polyglot context.
         *
         * @param truffleContext specifies the polyglot context for which retained size is
         *            calculated.
         * @param stopAtBytes when the calculated size exceeds stopAtBytes, calculation is stopped
         *            and only size calculated up to that point is returned, i.e., if the retained
         *            size is greater than stopAtBytes, a value greater than stopAtBytes will be
         *            returned, not the total retained size which might be much greater.
         * @param cancelled when cancelled returns true, calculation is cancelled and
         *            {@link java.util.concurrent.CancellationException} is thrown. The message of
         *            the exception specifies the number of bytes calculated up to that point.
         * @return calculated heap memory size retained by the specified polyglot context, or a
         *         value greater than stopAtBytes if the calculated size is greater than
         *         stopAtBytes.
         *
         * @throws UnsupportedOperationException in case heap size calculation is not supported on
         *             current runtime.
         * @throws java.util.concurrent.CancellationException in case the heap size calculation is
         *             cancelled based on the cancelled parameter. The message of the exception
         *             specifies the number of bytes calculated up to that point.
         * @since 21.1
         */
        public long calculateContextHeapSize(TruffleContext truffleContext, long stopAtBytes, AtomicBoolean cancelled) {
            return InstrumentAccessor.engineAccess().calculateContextHeapSize(InstrumentAccessor.langAccess().getPolyglotContext(truffleContext), stopAtBytes, cancelled);
        }

        /**
         * Submits a thread local action to be performed at the next guest language safepoint on a
         * provided set of threads, once for each thread. If the threads array is <code>null</code>
         * then the thread local action will be performed on all alive threads. The submitted
         * actions are processed in the same order as they are submitted in. The action can be
         * synchronous or asynchronous, side-effecting or non-sideeffecting. Please see
         * {@link ThreadLocalAction} for details.
         * <p>
         * It is ensured that a thread local action will get processed as long as the thread stays
         * active for this context. If a thread becomes inactive before the action can get processed
         * then the action will not be performed for this thread. If a thread becomes active while
         * the action is being processed then the action will be performed for that thread as long
         * as the thread filter includes the thread or <code>null</code> was passed. Already started
         * synchronous actions will block on activation of a new thread. If the synchronous action
         * was not yet started on any thread, then the synchronous action will also be performed for
         * the newly activated thread.
         * <p>
         * The method returns a {@link Future} instance that allows to wait for the thread local
         * action to complete or to cancel a currently performed event.
         * <p>
         * Example Usage:
         *
         * <pre>
         * Env env; // supplied by TruffleInstrument
         * TruffleContext context; // supplied by ContextsListener
         *
         * env.submitThreadLocal(context, null, new ThreadLocalAction(true, true) {
         *     &#64;Override
         *     protected void perform(Access access) {
         *         // perform action
         *     }
         * });
         * </pre>
         * <p>
         * By default thread-local actions are executed once per configured thread and do not repeat
         * themselves. If a ThreadLocalAction is configured to be
         * {@link ThreadLocalAction#ThreadLocalAction(boolean, boolean, boolean) recurring} then the
         * action will automatically be rescheduled in the same configuration until it is
         * {@link Future#cancel(boolean) cancelled}. For recurring actions, an invocation of
         * {@link Future#get()} will only wait for the first action to to be performed.
         * {@link Future#isDone()} will return <code>true</code> only if the action was canceled.
         * Canceling a recurring action will result in the current event being canceled and no
         * further events being submitted. Using recurring events should be preferred over
         * submitting the event again for the current thread while performing the thread-local
         * action as recurring events are also resubmitted in case all threads leave and later
         * reenter.
         * <p>
         * If the thread local action future needs to be waited on and this might be prone to
         * deadlocks the
         * {@link TruffleSafepoint#setBlocked(Node, Interrupter, Interruptible, Object, Runnable, Runnable)
         * blocking API} can be used to allow other thread local actions to be processed while the
         * current thread is waiting. The returned {@link Future#get()} method can be used as
         * {@link Interruptible}. If the supplied context is already closed, the method returns a
         * completed {@link Future}.
         *
         * @param context the context in which the action should be performed. Non <code>null</code>
         *            .
         * @param threads the threads to execute the action on. <code>null</code> for all threads
         * @param action the action to perform on that thread.
         * @see ThreadLocalAction
         * @see TruffleSafepoint
         * @since 21.1
         */
        // Note keep the javadoc in sync with TruffleLanguage.Env.submitThreadLocal
        public Future<Void> submitThreadLocal(TruffleContext context, Thread[] threads, ThreadLocalAction action) {
            Objects.requireNonNull(context);
            try {
                return InstrumentAccessor.ENGINE.submitThreadLocal(InstrumentAccessor.LANGUAGE.getPolyglotContext(context), this.polyglotInstrument, threads, action, true);
            } catch (Throwable t) {
                throw engineToInstrumentException(t);
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
        Class<?>[] services() default {
        };

        /**
         * A link to a website with more information about the instrument. Will be shown in the help
         * text of GraalVM launchers.
         * 
         * @since 22.1.0
         */
        String website() default "";
    }

    /**
     * Used to register a {@link TruffleInstrument} using a {@link ServiceLoader}. This interface is
     * not intended to be implemented directly by an instrument developer, rather the implementation
     * is generated by the Truffle DSL. The generated implementation has to inherit the
     * {@link Registration} annotations from the {@link TruffleInstrument}.
     *
     * @since 19.3.0
     */
    public interface Provider {

        /**
         * Returns the name of a class implementing the {@link TruffleInstrument}.
         *
         * @since 19.3.0
         */
        String getInstrumentClassName();

        /**
         * Creates a new instance of a {@link TruffleInstrument}.
         *
         * @since 19.3.0
         */
        TruffleInstrument create();

        /**
         * Returns the class names of provided services.
         *
         * @since 19.3.0
         */
        Collection<String> getServicesClassNames();
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
