/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_generic_failure;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_ok;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_pending_exception;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.Threading.RecurringCallback;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.CHeader;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.nativeapi.types.CBoolPointer;
import org.graalvm.polyglot.nativeapi.types.CInt16Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt32Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt64Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt64PointerPointer;
import org.graalvm.polyglot.nativeapi.types.CInt8Pointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedBytePointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedIntPointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedShortPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallback;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallbackInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContext;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextBuilder;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextBuilderPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngine;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngineBuilder;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngineBuilderPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEnginePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExceptionHandle;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExceptionHandlePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfoPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolate;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateThread;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotLanguage;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotLanguagePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotOutputHandler;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValue;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValuePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.SizeTPointer;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.SetThreadAndHeapBasePrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.handles.ObjectHandlesImpl;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jvmstat.PerfDataSupport;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.UnsignedUtils;

// Checkstyle: stop method name check

@SuppressWarnings("unused")
@CHeader(value = PolyglotAPIHeader.class)
public final class PolyglotNativeAPI {

    private static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

    private static final int MAX_UNSIGNED_BYTE = (1 << 8) - 1;
    private static final int MAX_UNSIGNED_SHORT = (1 << 16) - 1;
    private static final long MAX_UNSIGNED_INT = (1L << 32) - 1;
    private static final UnsignedWord POLY_AUTO_LENGTH = WordFactory.unsigned(0xFFFFFFFFFFFFFFFFL);
    private static final int DEFAULT_FRAME_CAPACITY = 16;

    private static final FastThreadLocalObject<ThreadLocalState> threadLocals = FastThreadLocalFactory.createObject(ThreadLocalState.class, "PolyglotNativeAPI.threadLocals");

    private static ThreadLocalState ensureLocalsInitialized() {
        ThreadLocalState state = threadLocals.get();
        if (state == null) {
            state = new ThreadLocalState();
            threadLocals.set(state);
        }
        return state;
    }

    private static PolyglotThreadLocalHandles<PolyglotNativeAPITypes.PolyglotHandle> getHandles() {
        ThreadLocalState locals = ensureLocalsInitialized();
        if (locals.handles == null) {
            locals.handles = new PolyglotThreadLocalHandles<>(DEFAULT_FRAME_CAPACITY);
        }
        return locals.handles;
    }

    private static final ObjectHandlesImpl objectHandles = new ObjectHandlesImpl(
                    WordFactory.signed(Long.MIN_VALUE), PolyglotThreadLocalHandles.nullHandle().subtract(1), PolyglotThreadLocalHandles.nullHandle());

    private static final class ThreadLocalState {
        PolyglotThreadLocalHandles<PolyglotNativeAPITypes.PolyglotHandle> handles;

        Throwable lastException;
        PolyglotExtendedErrorInfo lastExceptionUnmanagedInfo;

        PolyglotException polyglotException;

        PolyglotStatus lastErrorCode = poly_ok;

        CallbackException callbackException = null;

        ObjectHandle recurringCallbackInfoHandle = WordFactory.nullPointer();
    }

    private static void nullCheck(PointerBase ptr, String fieldName) {
        if (ptr.isNull()) {
            throw new NullPointerException(fieldName + " must be not be null");
        }
    }

    private static final AtomicReference<Context> contextToClose = new AtomicReference<>(null);

    /**
     * This thread is used to periodically close requested contexts. Note only one context closure
     * request can be pending at any time.
     */
    private static final ContextCloserThread contextCloserThread = new ContextCloserThread();

    private static class ContextCloserThread extends Thread {
        ContextCloserThread() {
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Thread.sleep(200);
                    Context context = contextToClose.getAndSet(null);
                    if (context != null) {
                        try {
                            context.close(true);
                        } catch (Exception t) {
                            // unable to close context, but should continue execution.
                        }
                    }
                }
            } catch (InterruptedException e) {
                // The normal way how this thread exits
            }
        }
    }

    static RuntimeSupport.Hook startupHook = (isFirstIsolate) -> contextCloserThread.start();

    private static String[] getPermittedLanguages(CCharPointerPointer permitted_languages, UnsignedWord length) {
        if (length.aboveThan(0)) {
            nullCheck(permitted_languages, "permitted_languages");
        }
        if (length.aboveThan(Integer.MAX_VALUE - 8)) {
            throw new IllegalArgumentException("permitted language array length is too large");
        }
        int intLength = UnsignedUtils.safeToInt(length);
        String[] jPermittedLangs = new String[intLength];
        for (int i = 0; i < intLength; i++) {
            jPermittedLangs[i] = CTypeConversion.toJavaString(permitted_languages.read(i));
        }
        return jPermittedLangs;
    }

    @CEntryPoint(name = "poly_create_engine_builder", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a new context builder that allows to configure an engine instance.",
                    "",
                    "@param permittedLanguages array of 0 terminated language identifiers in UTF-8 that are permitted.",
                    "@param length of the array of language identifiers.",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.html#newBuilder--",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_create_engine_builder(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotEngineBuilderPointer result) {
        resetErrorState();
        nullCheck(result, "result");
        ObjectHandle handle = createHandle(Engine.newBuilder(getPermittedLanguages(permitted_languages, length)));
        result.write(handle);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_allow_experimental_options", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows experimental options for a <code>poly_engine_builder</code>.",
                    "",
                    "@param engine_builder that is modified.",
                    "@param allow_experimental_options bool value that is passed to the builder.",
                    "@return poly_ok if successful; otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#allowExperimentalOptions-boolean-",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_engine_builder_allow_experimental_options(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder, boolean allow_experimental_options) {
        resetErrorState();
        nullCheck(engine_builder, "engine_builder");
        Engine.Builder engineBuilder = fetchHandle(engine_builder);
        engineBuilder.allowExperimentalOptions(allow_experimental_options);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_option", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets an option for an <code>poly_engine_builder</code> that will apply to constructed engines.",
                    "",
                    "@param engine_builder that is assigned an option.",
                    "@param key_utf8 0 terminated and UTF-8 encoded key for the option.",
                    "@param value_utf8 0 terminated and UTF-8 encoded value for the option.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.html#newBuilder--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_engine_builder_option(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder, @CConst CCharPointer key_utf8, @CConst CCharPointer value_utf8) {
        resetErrorState();
        nullCheck(engine_builder, "engine_builder");
        nullCheck(key_utf8, "key_utf8");
        nullCheck(value_utf8, "value_utf8");
        Engine.Builder eb = fetchHandle(engine_builder);
        eb.option(CTypeConversion.utf8ToJavaString(key_utf8), CTypeConversion.utf8ToJavaString(value_utf8));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_output", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets output handlers for a <code>poly_engine_builder</code>.",
                    "",
                    "@param engine_builder that is modified.",
                    "@param stdout_handler callback used for engine_builder output stream. Not used if NULL.",
                    "@param stderr_handler callback used for engine_builder error stream. Not used if NULL.",
                    "@param data user-defined data to be passed to stdout_handler and stderr_handler callbacks.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#out-java.io.OutputStream-",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#err-java.io.OutputStream-",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_engine_builder_output(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder, PolyglotOutputHandler stdout_handler,
                    PolyglotOutputHandler stderr_handler, VoidPointer data) {
        resetErrorState();
        nullCheck(engine_builder, "engine_builder");
        Engine.Builder eb = fetchHandle(engine_builder);
        if (stdout_handler.isNonNull()) {
            eb.out(newOutputStreamFor(stdout_handler, data));
        }
        if (stderr_handler.isNonNull()) {
            eb.err(newOutputStreamFor(stderr_handler, data));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_set_constrained_sandbox_policy", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets the engine's sandbox policy to CONSTRAINED.",
                    "",
                    "@param engine_builder that is modified.",
                    "@return poly_ok if successful; otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#sandbox-org.graalvm.polyglot.SandboxPolicy-",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_engine_builder_set_constrained_sandbox_policy(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder) {
        resetErrorState();
        nullCheck(engine_builder, "engine_builder");
        Engine.Builder engineBuilder = fetchHandle(engine_builder);
        engineBuilder.sandbox(SandboxPolicy.CONSTRAINED);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_builder_build", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Builds an <code>engine</code> from an <code>engine_builder</code>. The same builder can be used to ",
                    "produce multiple <code>poly_engine</code> instances.",
                    "",
                    "@param engine_builder that is used to build.",
                    "@param result the created engine.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.Builder.html#build--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_engine_builder_build(PolyglotIsolateThread thread, PolyglotEngineBuilder engine_builder, PolyglotEnginePointer result) {
        resetErrorState();
        nullCheck(engine_builder, "engine_builder");
        nullCheck(result, "result");
        Engine.Builder engineBuilder = fetchHandle(engine_builder);
        result.write(createHandle(engineBuilder.build()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_engine", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot engine: An execution engine for Graal guest languages that allows to inspect the ",
                    "installed languages and can have multiple execution contexts.",
                    "",
                    "Engine is a unit that holds configuration, instruments, and compiled code for all contexts assigned ",
                    "to this engine.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.html#create--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_engine(PolyglotIsolateThread thread, PolyglotEnginePointer result) {
        resetErrorState();
        nullCheck(result, "result");
        PolyglotNativeAPITypes.PolyglotHandle handle = createHandle(Engine.create());
        result.write(handle);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_close", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Closes this engine and frees up allocated native resources. If there are still open context",
                    "instances that were created using this engine and they are currently not being executed then",
                    "they will be closed automatically. If an attempt to close an engine was successful then",
                    "consecutive calls to close have no effect. If a context is cancelled then the currently",
                    "executing thread will throw a {@link PolyglotException}.",
                    "",
                    "@param engine to be closed.",
                    "@param cancel_if_executing if <code>true</code> then currently executing contexts will be cancelled.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.html#close-boolean-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_engine_close(PolyglotIsolateThread thread, PolyglotEngine engine, boolean cancel_if_executing) {
        resetErrorState();
        nullCheck(engine, "engine");
        Engine jEngine = fetchHandle(engine);
        jEngine.close(cancel_if_executing);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_engine_get_languages", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns an array where each element is a <code>poly_language<code> handle.",
                    "",
                    "To use, make two calls to this method:",
                    "",
                    "   The first passing a size_t pointer to write the count of the languages to (IE: 'size_t poly_language_count')",
                    "   The second passing the value of the written size_t length, and a pointer to a poly_language[poly_language_size]",
                    "<code>",
                    "size_t poly_language_count;",
                    "",
                    "// Get the number of languages",
                    "poly_engine_get_languages(thread, engine, NULL, &poly_language_count);",
                    "",
                    "// Allocate properly sized array of poly_language[]",
                    "poly_language languages_ptr[poly_language_count];",
                    "",
                    "// Write the language handles into the array",
                    "poly_engine_get_languages(thread, engine, &languages_ptr, &num_languages);",
                    "</code>",
                    "",
                    "@param engine for which languages are returned.",
                    "@param language_array array to write <code>poly_language</code>s to or NULL.",
                    "@param size the number of languages in the engine.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Engine.html#getLanguages--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_engine_get_languages(PolyglotIsolateThread thread, PolyglotEngine engine, PolyglotLanguagePointer language_array, SizeTPointer size) {
        resetErrorState();
        nullCheck(engine, "engine");
        nullCheck(size, "size");
        Engine jEngine = fetchHandle(engine);
        UnsignedWord languagesSize = WordFactory.unsigned(jEngine.getLanguages().size());
        if (language_array.isNull()) {
            size.write(languagesSize);
        } else {
            size.write(languagesSize);
            List<Language> sortedLanguages = sortedLangs(fetchHandle(engine));
            for (int i = 0; i < sortedLanguages.size(); i++) {
                language_array.write(i, createHandle(sortedLanguages.get(i)));
            }
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_context_builder", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a context with a new engine polyglot engine with a list ",
                    "",
                    "A context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "",
                    "@param permittedLanguages array of 0 terminated language identifiers in UTF-8 that are permitted.",
                    "@param length of the array of language identifiers.",
                    "@param result the created context.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#newBuilder-java.lang.String...-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_context_builder(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotContextBuilderPointer result) {
        resetErrorState();
        nullCheck(result, "result");
        Context.Builder c = Context.newBuilder(getPermittedLanguages(permitted_languages, length));
        result.write(createHandle(c));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_engine", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets an engine for the context builder.",
                    "",
                    "@param context_builder that is assigned an engine.",
                    "@param engine to assign to this builder.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#engine-org.graalvm.polyglot.Engine-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_engine(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotEngine engine) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        nullCheck(engine, "engine");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        Engine jEngine = fetchHandle(engine);
        contextBuilder.engine(jEngine);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_option", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets an option on a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is assigned an option.",
                    "@param key_utf8 0 terminated and UTF-8 encoded key for the option.",
                    "@param value_utf8 0 terminated and UTF-8 encoded value for the option.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#option-java.lang.String-java.lang.String-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_option(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, @CConst CCharPointer key_utf8, @CConst CCharPointer value_utf8) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        nullCheck(key_utf8, "key_utf8");
        nullCheck(value_utf8, "value_utf8");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.option(CTypeConversion.utf8ToJavaString(key_utf8), CTypeConversion.utf8ToJavaString(value_utf8));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_output", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets output handlers for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param stdout_handler callback used for context_builder output stream. Not used if NULL.",
                    "@param stderr_handler callback used for context_builder error stream. Not used if NULL.",
                    "@param data user-defined data to be passed to stdout_handler and stderr_handler callbacks.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#out-java.io.OutputStream-",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#err-java.io.OutputStream-",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_context_builder_output(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotOutputHandler stdout_handler,
                    PolyglotOutputHandler stderr_handler, VoidPointer data) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        if (stdout_handler.isNonNull()) {
            contextBuilder.out(newOutputStreamFor(stdout_handler, data));
        }
        if (stderr_handler.isNonNull()) {
            contextBuilder.err(newOutputStreamFor(stderr_handler, data));
        }
        return poly_ok;
    }

    private static OutputStream newOutputStreamFor(PolyglotOutputHandler outputHandler, VoidPointer data) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b});
            }

            @Override
            public void write(byte[] b, int off, int len) {
                Objects.checkFromIndexSize(off, len, b.length);
                if (len == 0) {
                    return;
                }
                try (var bytes = CTypeConversion.toCBytes(b)) {
                    outputHandler.invoke(bytes.get().addressOf(off), WordFactory.unsigned(len), data);
                }
            }
        };
    }

    @CEntryPoint(name = "poly_context_builder_allow_all_access", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows all access for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param allow_all_access bool value that defines all access.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowAllAccess-boolean-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_all_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_all_access) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowAllAccess(allow_all_access);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_io", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows IO for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param allow_IO bool value that is passed to the builder.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowIO-org.graalvm.polyglot.io.IOAccess-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_io(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_IO) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowIO(allow_IO ? IOAccess.ALL : IOAccess.NONE);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_native_access", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows native access for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param allow_native_access bool value that is passed to the builder.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowNativeAccess-boolean-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_native_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_native_access) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowNativeAccess(allow_native_access);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_polyglot_access", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows polyglot access for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param allow_polyglot_access bool value that is passed to the builder.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowPolyglotAccess-org.graalvm.polyglot.PolyglotAccess-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_polyglot_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_polyglot_access) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowPolyglotAccess(allow_polyglot_access ? PolyglotAccess.ALL : PolyglotAccess.NONE);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_create_thread", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows thread creation for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param allow_create_thread bool value that is passed to the builder.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowCreateThread-boolean-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_create_thread(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_create_thread) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowCreateThread(allow_create_thread);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_allow_experimental_options", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Allows or disallows experimental options for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param allow_experimental_options bool value that is passed to the builder.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowExperimentalOptions-boolean-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_allow_experimental_options(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_experimental_options) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.allowExperimentalOptions(allow_experimental_options);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_set_constrained_sandbox_policy", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets the context's sandbox policy to CONSTRAINED.",
                    "",
                    "@param context_builder that is modified.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#sandbox-org.graalvm.polyglot.SandboxPolicy-",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_context_builder_set_constrained_sandbox_policy(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.sandbox(SandboxPolicy.CONSTRAINED);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_builder_build", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Builds a <code>context</code> from a <code>context_builder</code>. The same builder can be used to ",
                    "produce multiple <code>poly_context</code> instances.",
                    "",
                    "@param context_builder that is used to construct a new context.",
                    "@param result the created context.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#build--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_builder_build(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotContextPointer result) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        nullCheck(result, "result");
        Context.Builder contextBuilder = fetchHandle(context_builder);
        result.write(createHandle(contextBuilder.build()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_context", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a context with default configuration.",
                    "",
                    "A context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "",
                    "@param permitted_languages array of 0 terminated language identifiers in UTF-8 that are permitted, or NULL for supporting all available languages.",
                    "@param length of the array of language identifiers.",
                    "@param result the created context.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#create-java.lang.String...-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_context(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotContextPointer result) {
        resetErrorState();
        nullCheck(result, "result");
        Context c = Context.create(getPermittedLanguages(permitted_languages, length));
        result.write(createHandle(c));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_close", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Closes this context and frees up potentially allocated native resources. A ",
                    "context cannot free all native resources allocated automatically. For this reason",
                    "it is necessary to close contexts after use. If a context is canceled then the",
                    "currently executing thread will throw a {@link PolyglotException}. Please note ",
                    "that canceling a single context can negatively affect the performance of other ",
                    "executing contexts constructed with the same engine.",
                    "",
                    "If internal errors occur during closing of the language then they are printed to the ",
                    "configured {@link Builder#err(OutputStream) error output stream}. If a context was ",
                    "closed then all its methods will throw an {@link IllegalStateException} when invoked. ",
                    "If an attempt to close a context was successful then consecutive calls to close have ",
                    "no effect.",
                    "",
                    "@param context to be closed.",
                    "@param cancel_if_executing if <code>true</code> then currently executing context will be cancelled.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#close-boolean-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_close(PolyglotIsolateThread thread, PolyglotContext context, boolean cancel_if_executing) {
        nullCheck(context, "context");
        resetErrorState();
        Context jContext = fetchHandle(context);
        jContext.close(cancel_if_executing);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_request_close_async", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Request for this context to be closed asynchronously.",
                    "An attempt to closed this context will later be made via a background thread.",
                    "Note this call will attempt to close a context; however, it is not guaranteed the closure will be successful. ",
                    "In addition, if another context request has already been made but hasn't been processed, then this call will not succeed.",
                    "",
                    "@param context to be closed.",
                    "@return poly_ok if closure request submitted, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#close-boolean-",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_context_request_close_async(PolyglotIsolateThread thread, PolyglotContext context) {
        resetErrorState();
        Context jContext = fetchHandleOrNull(context);
        if (jContext != null && contextToClose.compareAndSet(null, jContext)) {
            return poly_ok;
        } else {
            return poly_generic_failure;
        }
    }

    @CEntryPoint(name = "poly_context_eval", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Evaluate a source of guest languages inside a context.",
                    "",
                    "@param context in which we evaluate source code.",
                    "@param language_id_utf8 0 terminated and UTF-8 encoded language identifier.",
                    "@param name_utf8 0 terminated and UTF-8 encoded name given to the evaluate source code.",
                    "@param source_utf8 0 terminated and UTF-8 encoded source code to be evaluated.",
                    "@param result <code>poly_value</code> that is the result of the evaluation. You can pass <code>NULL</code> if you just want to evaluate the source and you can ignore the result.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.html#newBuilder-java.lang.String-java.lang.CharSequence-java.lang.String-",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#eval-org.graalvm.polyglot.Source-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_eval(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language_id_utf8, @CConst CCharPointer name_utf8,
                    @CConst CCharPointer source_utf8, PolyglotValuePointer result) throws Exception {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(language_id_utf8, "language_id_utf8");
        nullCheck(name_utf8, "name_utf8");
        nullCheck(source_utf8, "source_utf8");
        Context c = fetchHandle(context);
        String languageName = CTypeConversion.utf8ToJavaString(language_id_utf8);
        String jName = CTypeConversion.utf8ToJavaString(name_utf8);
        String jCode = CTypeConversion.utf8ToJavaString(source_utf8);

        Source sourceCode = Source.newBuilder(languageName, jCode, jName).build();
        Value evalResult = c.eval(sourceCode);
        if (result.isNonNull()) {
            result.write(createHandle(evalResult));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_get_engine", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns the engine this context belongs to.",
                    "",
                    "@param context for which we extract the bindings.",
                    "@param result a value whose members correspond to the symbols in the top scope of the `language_id`.",
                    "@return poly_ok if everything is fine, poly_generic_failure if there is an error.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#getEngine--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_get_engine(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context jContext = fetchHandle(context);
        result.write(createHandle(jContext.getEngine()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_get_bindings", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a value that represents the top-most bindings of a language. The top-most bindings of",
                    "the language are a value whose members correspond to each symbol in the top scope.",
                    "",
                    "Languages may allow modifications of members of the returned bindings object at the",
                    "language's discretion. If the language was not yet initialized it",
                    "will be initialized when the bindings are requested.",
                    "",
                    "@param context for which we extract the bindings.",
                    "@param language_id_utf8 0 terminated and UTF-8 encoded language identifier.",
                    "@param result a value whose members correspond to the symbols in the top scope of the `language_id_utf8`.",
                    "@return poly_generic_failure if the language does not exist, if context is already closed, ",
                    "        in case the lazy initialization failed due to a guest language error.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#getBindings-java.lang.String-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_get_bindings(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language_id_utf8, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(language_id_utf8, "language_id_utf8");
        nullCheck(result, "result");
        Context jContext = fetchHandle(context);
        String jLanguage = CTypeConversion.utf8ToJavaString(language_id_utf8);
        Value languageBindings = jContext.getBindings(jLanguage);
        result.write(createHandle(languageBindings));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_context_get_polyglot_bindings", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns polyglot bindings that may be used to exchange symbols between the host and ",
                    "guest languages. All languages have unrestricted access to the polyglot bindings. ",
                    "The returned bindings object always has members and its members are readable, writable and removable.",
                    "",
                    "Guest languages may put and get members through language specific APIs. For example, ",
                    "in JavaScript symbols of the polyglot bindings can be accessed using ",
                    "`Polyglot.import(\"name\")` and set using `Polyglot.export(\"name\", value)`. Please see ",
                    "the individual language reference on how to access these symbols.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is already closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#getPolyglotBindings--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_context_get_polyglot_bindings(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context jContext = fetchHandle(context);
        result.write(createHandle(jContext.getPolyglotBindings()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_can_execute", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks whether a polyglot value can be executed.",
                    "",
                    "@param value a polyglot value.",
                    "@param result true if the value can be executed, false otherwise.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#canExecute--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_can_execute(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.canExecute()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_execute", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Executes a value if it can be executed and returns its result. All arguments passed ",
                    "must be polyglot values.",
                    "",
                    "@param value to be executed.",
                    "@param args array of poly_value.",
                    "@param args_size length of the args array.",
                    "@param result <code>poly_value</code> that is the result of the execution. You can pass <code>NULL</code> if you just want to execute the source and you can ignore the result.",
                    "@return poly_ok if all works, poly_generic_error if the underlying context was closed, if a wrong ",
                    "         number of arguments was provided or one of the arguments was not applicable, if this value cannot be executed,",
                    " and if a guest language error occurred during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#execute-java.lang.Object...-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_execute(PolyglotIsolateThread thread, PolyglotValue value, PolyglotValuePointer args, int args_size, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(value, "value");
        if (args_size > 0) {
            nullCheck(args, "args");
        }
        Value function = fetchHandle(value);
        Object[] jArgs = new Object[args_size];
        for (int i = 0; i < args_size; i++) {
            PolyglotValue handle = args.read(i);
            jArgs[i] = fetchHandle(handle);
        }

        Value resultValue = function.execute(jArgs);
        if (result.isNonNull()) {
            result.write(createHandle(resultValue));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_get_member", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns the member with a given `identifier_utf8` or `null` if the member does not exist.",
                    "",
                    "@param identifier_utf8 0 terminated and UTF-8 encoded member identifier.",
                    "@return poly_ok if all works, poly_generic_failure if the value has no members, the given identifier exists ",
                    "        but is not readable, if a guest language error occurred during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getMember-java.lang.String-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_get_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer identifier_utf8, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(identifier_utf8, "identifier_utf8");
        nullCheck(result, "result");
        Value jObject = fetchHandle(value);
        result.write(createHandle(jObject.getMember(CTypeConversion.utf8ToJavaString(identifier_utf8))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_put_member", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets the value of a member with the `identifier_utf8`.",
                    "",
                    "@param identifier_utf8 0 terminated and UTF-8 encoded member identifier.",
                    "@return poly_ok if all works, poly_generic_failure if the context is already closed, if the value does ",
                    "         not have any members, the key does not exist and new members cannot be added, or the existing ",
                    "         member is not modifiable.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#putMember-java.lang.String-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_put_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer identifier_utf8, PolyglotValue member) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(identifier_utf8, "identifier_utf8");
        Value jObject = fetchHandle(value);
        Value jMember = fetchHandle(member);
        jObject.putMember(CTypeConversion.utf8ToJavaString(identifier_utf8), jMember);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_has_member", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if such a member exists for the given `identifier_utf8`. If the value has no members ",
                    "then it returns `false`.",
                    "",
                    "@param identifier_utf8 0 terminated and UTF-8 encoded member identifier.",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "         during execution.",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#hasMember-java.lang.String-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_has_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer identifier_utf8, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(identifier_utf8, "identifier_utf8");
        nullCheck(result, "result");
        Value jObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jObject.hasMember(CTypeConversion.utf8ToJavaString(identifier_utf8))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_boolean", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot boolean value from a C boolean.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_boolean(PolyglotIsolateThread thread, PolyglotContext context, boolean value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(value)));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int8_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_int8(PolyglotIsolateThread thread, PolyglotContext context, byte value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int16_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_int16(PolyglotIsolateThread thread, PolyglotContext context, short value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Short.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int32_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_int32(PolyglotIsolateThread thread, PolyglotContext context, int value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int64", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `int64_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_int64(PolyglotIsolateThread thread, PolyglotContext context, long value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Long.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `uint8_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_uint8(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned byte value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Byte.toUnsignedInt(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `uint16_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_uint16(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned short value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Short.toUnsignedInt(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot integer number from `uint32_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_uint32(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned int value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Integer.toUnsignedLong(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_float", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot floating point number from C `float`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_float(PolyglotIsolateThread thread, PolyglotContext context, float value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Float.valueOf(value))));
        return poly_ok;
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_double", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot floating point number from C `double`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_double(PolyglotIsolateThread thread, PolyglotContext context, double value, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(Double.valueOf(value))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_character", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot character from C `char`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_character(PolyglotIsolateThread thread, PolyglotContext context, char character, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(character)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_string_utf8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot string from an UTF-8 encoded string. ",
                    "If `POLY_AUTO_LENGTH` is passed as the `length` argument, then `string_utf8` is decoded until a 0 terminator is found.",
                    "Otherwise, `length` bytes from `string_uft8` are encoded as a polyglot string value.",
                    "",
                    "@param string_utf8 UTF-8 encoded C string, which may or may not be 0 terminated.",
                    "@param length POLY_AUTO_LENGTH if the string is 0 terminated, or otherwise the length of C string.",
                    "@return the polyglot string value.",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_string_utf8(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer string_utf8, UnsignedWord length, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(string_utf8, "string_utf8");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        if (length.equal(POLY_AUTO_LENGTH)) {
            result.write(createHandle(ctx.asValue(CTypeConversion.utf8ToJavaString(string_utf8))));
        } else {
            result.write(createHandle(ctx.asValue(CTypeConversion.toJavaString(string_utf8, length, UTF8_CHARSET))));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_null", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates the polyglot `null` value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_null(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        result.write(createHandle(ctx.asValue(null)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_object", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot object with no members.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/proxy/ProxyObject.html#fromMap-java.util.Map-",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_object(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(result, "result");
        Context c = fetchHandle(context);
        ProxyObject proxy = ProxyObject.fromMap(new HashMap<>());
        result.write(createHandle(c.asValue(proxy)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_array", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot array from the C array of polyglot values.",
                    "",
                    "@param value_array array containing polyglot values",
                    "@param array_length the number of elements in the value_array",
                    "@return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed, ",
                    "         if the array does not contain polyglot values.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/proxy/ProxyArray.html#fromList-java.util.List-",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html#asValue-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_array(PolyglotIsolateThread thread, PolyglotContext context, @CConst PolyglotValuePointer value_array, long array_length, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(context, "context");
        if (array_length > 0) {
            nullCheck(value_array, "value_array");
        }
        nullCheck(result, "result");
        Context ctx = fetchHandle(context);
        List<Object> values = new LinkedList<>();
        for (long i = 0; i < array_length; i++) {
            values.add(fetchHandle(value_array.read(i)));
        }
        result.write(createHandle(ctx.asValue(ProxyArray.fromList(values))));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_has_array_elements", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Check whether a polyglot value has array elements. ",
                    "",
                    "If yes, array elements can be accessed using {@link poly_value_get_array_element}, ",
                    "{@link poly_value_set_array_element}, {@link poly_value_remove_array_element} and the array size ",
                    "can be queried using {@link poly_value_get_array_size}.",
                    "",
                    "@param value value that we are checking.",
                    "@return true if the value has array elements.",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#hasArrayElements--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_has_array_elements(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.hasArrayElements()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_get_array_element", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns an array element from the specified index. ",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    "@param value value that has array elements.",
                    "@param index index of the element starting from 0.",
                    "@param result the returned array element.",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getArrayElement-long-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_get_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(createHandle(jValue.getArrayElement(index)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_set_array_element", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets the value at a given index.",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    "@param value value that we are checking.",
                    "@param index index of the element starting from 0.",
                    "@param element to be written into the array.",
                    "@param result true if the value has array elements.",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#setArrayElement-long-java.lang.Object-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_set_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, PolyglotValue element) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(element, "element");
        Value jValue = fetchHandle(value);
        Value jElement = fetchHandle(element);
        jValue.setArrayElement(index, jElement);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_remove_array_element", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Removes an array element at a given index.",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    "@param value value that we are checking.",
                    "@param index index of the element starting from 0.",
                    "@param result true if the underlying array element could be removed, otherwise false.",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#removeArrayElement-long-",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_remove_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.removeArrayElement(index)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_get_array_size", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the size of the polyglot value that has array elements.",
                    "",
                    "@param value value that has array elements.",
                    "@param result number of elements in the value.",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#getArraySize--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_get_array_size(PolyglotIsolateThread thread, PolyglotValue value, CInt64Pointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(jValue.getArraySize());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_null", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is `null` like.",
                    "",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#isNull--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_is_null(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isNull()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_boolean", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value represents a boolean value.",
                    "",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#isBoolean--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_is_boolean(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isBoolean()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_string", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value represents a string.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#isString--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_is_string(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isString()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_is_number", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value represents a number.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#isNumber--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_is_number(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.isNumber()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_float", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into a C float.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInFloat--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_fits_in_float(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInFloat()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_double", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into a C double.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInDouble--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_double(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInDouble()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int8_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInByte--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInByte()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int16_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInInt--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int16(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInInt();
        if (jResult) {
            int intValue = jValue.asInt();
            jResult = intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int32_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInInt--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInInt()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_int64", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `int64_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInLong--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_int64(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(dataObject.fitsInLong()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_uint8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `uint8_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInInt--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInInt();
        if (jResult) {
            int intValue = jValue.asInt();
            jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_BYTE;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_uint16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `uint16_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInInt--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint16(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInInt();
        if (jResult) {
            int intValue = jValue.asInt();
            jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_SHORT;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_fits_in_uint32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns `true` if this value is a number and can fit into `uint32_t`.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#fitsInLong--",
                    "@since 19.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        boolean jResult = jValue.fitsInLong();
        if (jResult) {
            long intValue = jValue.asLong();
            jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_INT;
        }
        result.write(CTypeConversion.toCBoolean(jResult));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_string_utf8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Writes the Polyglot value's string representation as a 0 terminated and UTF-8 encoded string.",
                    "",
                    "@param buffer Where to write the UTF-8 string representing the polyglot value. Can be NULL.",
                    "@param buffer_size Size of the user-supplied buffer.",
                    "@param result If buffer is NULL, this will contain the byte size of the string, otherwise, it will contain the number of bytes written. Note in either case this length does not contain the 0 terminator written to the end of the buffer",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asString--", "@since 19.0",

    })
    public static PolyglotStatus poly_value_as_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        writeUTF8String(jValue.asString(), buffer, buffer_size, result);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_to_string_utf8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Writes a <code>toString</code> representation of a <code>poly_value</code> as a 0 terminated and UTF-8 encoded string.",
                    "",
                    "@param buffer Where to write the UTF-8 string representing the toString representation of the polyglot value. Can be NULL.",
                    "@param buffer_size Size of the user-supplied buffer.",
                    "@param result If buffer is NULL, this will contain the byte size of the string, otherwise, it will contain the number of bytes written. Note in either case this length does not contain the 0 terminator written to the end of the buffer",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#toString--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_to_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        writeUTF8String(jValue.toString(), buffer, buffer_size, result);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_boolean", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a boolean representation of the value.",
                    "",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asBoolean--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_bool(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value jValue = fetchHandle(value);
        result.write(CTypeConversion.toCBoolean(jValue.asBoolean()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int8_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted. ",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asByte--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_int8(PolyglotIsolateThread thread, PolyglotValue value, CInt8Pointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        result.write(valueObject.asByte());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int32_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asInt--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_int16(PolyglotIsolateThread thread, PolyglotValue value, CInt16Pointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        int intValue = valueObject.asInt();
        if (intValue < Short.MIN_VALUE || intValue > Short.MAX_VALUE) {
            throw reportError("Value " + intValue + " does not fit into int_16_t.", poly_generic_failure);
        }
        result.write((short) intValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int32_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asInt--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_int32(PolyglotIsolateThread thread, PolyglotValue value, CInt32Pointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        result.write(valueObject.asInt());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_int64", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a int64_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asLong--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_int64(PolyglotIsolateThread thread, PolyglotValue value, CInt64Pointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        result.write(valueObject.asLong());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_uint8", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a uint8_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asInt--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_uint8(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedBytePointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        int intValue = valueObject.asInt();
        if (intValue < 0 || intValue > MAX_UNSIGNED_BYTE) {
            throw reportError("Value " + Integer.toUnsignedString(intValue) + "does not fit in uint8_t", poly_generic_failure);
        }
        result.write((byte) intValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_uint16", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a uint16_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asInt--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_uint16(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedShortPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        int intValue = valueObject.asInt();
        if (intValue < 0 || intValue > MAX_UNSIGNED_SHORT) {
            throw reportError("Value " + Integer.toUnsignedString(intValue) + "does not fit in uint16_t", poly_generic_failure);
        }
        result.write((short) intValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_uint32", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a uint32_t representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asLong--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_uint32(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedIntPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value valueObject = fetchHandle(value);
        long longValue = valueObject.asLong();
        if (longValue < 0 || longValue > MAX_UNSIGNED_INT) {
            throw reportError("Value " + Long.toUnsignedString(longValue) + "does not fit in uint32_t", poly_generic_failure);
        }
        result.write((int) longValue);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_float", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a float representation of the value.",
                    "",
                    "@return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asFloat--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_float(PolyglotIsolateThread thread, PolyglotValue value, CFloatPointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(dataObject.asFloat());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_value_as_double", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Returns a double representation of the value.",
                    "",
                    "@return poly_ok if the operation completed successfully, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html#asDouble--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_value_as_double(PolyglotIsolateThread thread, PolyglotValue value, CDoublePointer result) {
        resetErrorState();
        nullCheck(value, "value");
        nullCheck(result, "result");
        Value dataObject = fetchHandle(value);
        result.write(dataObject.asDouble());
        return poly_ok;
    }

    @CEntryPoint(name = "poly_language_get_id", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Writes the primary identification string of this language as a 0 terminated and UTF-8 encoded string.",
                    "",
                    "The language id is used as the primary way of identifying languages in the polyglot API. (eg. <code>js</code>)",
                    "",
                    "@param buffer Where to write the UTF-8 string representing the language id. Can be NULL.",
                    "@param buffer_size Size of the user-supplied buffer.",
                    "@param result If buffer is NULL, this will contain the byte size of the language, otherwise, it will contain the number of bytes written. Note in either case this length does not contain the 0 terminator written to the end of the buffer",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Language.html#getId--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_language_get_id(PolyglotIsolateThread thread, PolyglotLanguage language, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        nullCheck(language, "language");
        nullCheck(result, "result");
        Language jLanguage = fetchHandle(language);
        writeUTF8String(jLanguage.getId(), buffer, buffer_size, result);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_get_last_error_info", exceptionHandler = GenericFailureExceptionHandler.class, documentation = {
                    "Returns information about last error that occurred on this thread in the poly_extended_error_info structure.",
                    "",
                    "This method must be called right after a failure occurs and can be called only once.",
                    "",
                    "@return information about the last failure on this thread.",
                    "",
                    "@since 19.0",
    })
    @Uninterruptible(reason = "Prevent safepoint checks before pausing recurring callback.")
    public static PolyglotStatus poly_get_last_error_info(PolyglotIsolateThread thread, @CConst PolyglotExtendedErrorInfoPointer result) {
        ThreadingSupportImpl.pauseRecurringCallback("Prevent recurring callback from throwing another exception.");
        try {
            return doGetLastErrorInfo(result);
        } finally {
            ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
        }
    }

    @Uninterruptible(reason = "Not really, but our caller is.", calleeMustBe = false)
    private static PolyglotStatus doGetLastErrorInfo(PolyglotExtendedErrorInfoPointer result) {
        return doGetLastErrorInfo0(result);
    }

    private static PolyglotStatus doGetLastErrorInfo0(PolyglotExtendedErrorInfoPointer result) {
        nullCheck(result, "result");
        ThreadLocalState state = threadLocals.get();
        if (state == null || state.lastException == null) {
            result.write(WordFactory.nullPointer());
            return poly_ok;
        }

        assert state.lastErrorCode != poly_ok;
        freeUnmanagedErrorState(state);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(state.lastException.getMessage());
        pw.println("The full stack trace is:");
        state.lastException.printStackTrace(pw);
        ByteBuffer message = UTF8_CHARSET.encode(sw.toString());

        int messageSize = message.capacity() + 1;
        CCharPointer messageChars = UnmanagedMemory.malloc(messageSize);
        ByteBuffer messageBuffer = CTypeConversion.asByteBuffer(messageChars, messageSize);
        messageBuffer.put(message).put((byte) 0);

        PolyglotExtendedErrorInfo info = UnmanagedMemory.malloc(SizeOf.get(PolyglotExtendedErrorInfo.class));
        info.setErrorCode(state.lastErrorCode.getCValue());
        info.setErrorMessage(messageChars);

        state.lastExceptionUnmanagedInfo = info;
        result.write(state.lastExceptionUnmanagedInfo);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_function", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a polyglot function that calls back into native code.",
                    "",
                    "@param data user defined data to be passed into the function.",
                    "@param callback function that is called from the polyglot engine.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_function(PolyglotIsolateThread thread, PolyglotContext context, PolyglotCallback callback, VoidPointer data,
                    PolyglotValuePointer value) {
        ensureLocalsInitialized();
        resetErrorState();
        nullCheck(context, "context");
        nullCheck(callback, "callback");
        nullCheck(value, "value");
        Context c = fetchHandle(context);
        ProxyExecutable executable = new ProxyExecutable() {
            @Override
            public Object execute(Value... arguments) {
                int frame = getHandles().pushFrame(DEFAULT_FRAME_CAPACITY);
                try {
                    ObjectHandle[] handleArgs = new ObjectHandle[arguments.length];
                    for (int i = 0; i < arguments.length; i++) {
                        handleArgs[i] = createHandle(arguments[i]);
                    }
                    PolyglotCallbackInfo cbInfo = (PolyglotCallbackInfo) createHandle(new PolyglotCallbackInfoInternal(handleArgs, data));
                    PolyglotValue result = callback.invoke((PolyglotIsolateThread) CurrentIsolate.getCurrentThread(), cbInfo);
                    CallbackException ce = threadLocals.get().callbackException;
                    if (ce != null) {
                        throw ce;
                    } else {
                        return PolyglotNativeAPI.fetchHandle(result);
                    }
                } finally {
                    threadLocals.get().callbackException = null;
                    getHandles().popFramesIncluding(frame);
                }
            }
        };
        value.write(createHandle(c.asValue(executable)));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_get_callback_info", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Retrieves details about the call within a callback (e.g., the arguments from a given callback info).",
                    "",
                    "@param callback_info from the callback.",
                    "@param argc number of arguments to the callback.",
                    "@param argv poly_value array of arguments for the callback.",
                    "@param the data pointer for the callback.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_get_callback_info(PolyglotIsolateThread thread, PolyglotCallbackInfo callback_info, SizeTPointer argc, PolyglotValuePointer argv, WordPointer data) {
        resetErrorState();
        nullCheck(callback_info, "callback_info");
        nullCheck(argc, "argc");
        nullCheck(data, "data");
        PolyglotCallbackInfoInternal callbackInfo = fetchHandle(callback_info);
        UnsignedWord numberOfArguments = WordFactory.unsigned(callbackInfo.arguments.length);
        UnsignedWord bufferSize = argc.read();
        UnsignedWord size = bufferSize.belowThan(numberOfArguments) ? bufferSize : numberOfArguments;
        argc.write(size);
        if (size.aboveThan(0)) {
            nullCheck(argv, "argv");
        }
        for (int index = 0; size.aboveThan(index); index++) {
            ObjectHandle argument = callbackInfo.arguments[index];
            argv.write(index, argument);
        }
        data.write(callbackInfo.data);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_throw_exception", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Raises an exception in a C callback.",
                    "",
                    "Invocation of this method does not interrupt control-flow so it is necessary to return from a function after ",
                    "the exception has been raised. If this method is called multiple times only the last exception will be thrown in",
                    "in the guest language.",
                    "",
                    "@param message_utf8 0 terminated and UTF-8 encoded error message.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_throw_exception(PolyglotIsolateThread thread, @CConst CCharPointer message_utf8) {
        resetErrorState();
        nullCheck(message_utf8, "message_utf8");
        threadLocals.get().callbackException = new CallbackException(CTypeConversion.utf8ToJavaString(message_utf8));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_delete_reference", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Deletes a poly_reference. After this point, the reference must not be used anymore.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_delete_reference(PolyglotIsolateThread thread, PolyglotNativeAPITypes.PolyglotReference reference) {
        resetErrorState();
        nullCheck(reference, "reference");
        objectHandles.destroy(reference);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_create_reference", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Creates a poly_reference from a poly_handle. After this point, the reference is alive until poly_delete_reference is called. ",
                    "",
                    "Handles are: poly_engine, poly_engine_builder, poly_context, poly_context_builder, poly_language, poly_value, ",
                    "and poly_callback_info.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_create_reference(PolyglotIsolateThread thread, PolyglotNativeAPITypes.PolyglotHandle handle, PolyglotNativeAPITypes.PolyglotReferencePointer reference) {

        resetErrorState();
        nullCheck(handle, "handle");
        nullCheck(reference, "reference");
        ObjectHandle ref = objectHandles.create(getHandles().getObject(handle));
        reference.write((PolyglotNativeAPITypes.PolyglotReference) ref);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_open_handle_scope", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Opens a handle scope. Until the scope is closed, all objects will belong to the newly created scope.",
                    "",
                    "Handles are: poly_engine, poly_engine_builder, poly_context, poly_context_builder, poly_language, poly_value, ",
                    "and poly_callback_info.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_open_handle_scope(PolyglotIsolateThread thread) {
        resetErrorState();
        getHandles().pushFrame(DEFAULT_FRAME_CAPACITY);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_close_handle_scope", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Closes a handle scope. After this point, the handles from the current scope must not be used anymore.",
                    "",
                    "Handles are: poly_engine, poly_engine_builder, poly_context, poly_context_builder, poly_language, poly_value, ",
                    "and poly_callback_info.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_close_handle_scope(PolyglotIsolateThread thread) {
        resetErrorState();
        getHandles().popFrame();
        return poly_ok;
    }

    @CEntryPoint(name = "poly_get_last_exception", exceptionHandler = GenericFailureExceptionHandler.class, documentation = {
                    "Returns the last exception that occurred on this thread, or does nothing if an exception did not happen.",
                    "",
                    "This method must be called right after an exception occurs (after a method returns poly_pending_exception), ",
                    "and can be called only once.",
                    "",
                    "@param result On success, a handle to the last exception on this thread is put here.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_get_last_exception(PolyglotIsolateThread thread, PolyglotExceptionHandlePointer result) {
        ThreadLocalState state = threadLocals.get();
        nullCheck(result, "result");
        if (state == null || state.polyglotException == null) {
            result.write(PolyglotThreadLocalHandles.nullHandle());
        } else {
            result.write(createHandle(state.polyglotException));
            state.polyglotException = null;
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_syntax_error", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if an exception is caused by a parser or syntax error.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#isSyntaxError--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_exception_is_syntax_error(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isSyntaxError()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_cancelled", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if execution has been cancelled.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#isCancelled--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_exception_is_cancelled(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isCancelled()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_internal_error", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception was caused by an internal implementation error.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#isInternalError--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_exception_is_internal_error(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isInternalError()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_resource_exhausted", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception indicates that a resource limit was exceeded.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#isResourceExhausted--",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_exception_is_resource_exhausted(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isResourceExhausted()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_host_exception", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception originates from the Java host language.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#isHostException--",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_exception_is_host_exception(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isHostException()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_is_guest_exception", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception originates from a Graal guest language.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#isGuestException--",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_exception_is_guest_exception(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.isGuestException()));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_has_object", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Checks if this exception has a guest language exception object attached to it.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The result of the check.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#getGuestObject--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_exception_has_object(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CBoolPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        result.write(CTypeConversion.toCBoolean(e.getGuestObject() != null));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_object", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the handle to the guest exception object. This object can then be used in other poly methods.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param result The handle to the guest object if it exists.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#getGuestObject--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_exception_get_object(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, PolyglotValuePointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        Value guestObject = e.getGuestObject();
        if (guestObject == null) {
            reportError("Attempted to get the guest object of an exception that did not have one.", poly_generic_failure);
        } else {
            result.write(createHandle(guestObject));
        }
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_stack_trace", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Writes the full stack trace as a 0 terminated and UTF-8 encoded string.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param buffer Where to write the UTF-8 string representing the stack trace. Can be NULL.",
                    "@param buffer_size Size of the user-supplied buffer.",
                    "@param result If buffer is NULL, this will contain the byte size of the trace string, otherwise, it will contain the number of bytes written. Note in either case this length does not contain the 0 terminator written to the end of the buffer",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#getStackTrace--",
                    "@since 19.0",
    })
    public static PolyglotStatus poly_exception_get_stack_trace(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        e.getPolyglotStackTrace().forEach(trace -> pw.println(trace));

        writeUTF8String(sw.toString(), buffer, buffer_size, result);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_guest_stack_trace", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Writes the guest stack trace as a 0 terminated and UTF-8 encoded string.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param buffer Where to write the UTF-8 string representing the stack trace. Can be NULL.",
                    "@param buffer_size Size of the user-supplied buffer.",
                    "@param result If buffer is NULL, this will contain the byte size of the trace, otherwise, it will contain the number of bytes written. Note in either case this length does not contain the 0 terminator written to the end of the buffer",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#getPolyglotStackTrace--",
                    "@since 22.3",
    })
    public static PolyglotStatus poly_exception_get_guest_stack_trace(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CCharPointer buffer, UnsignedWord buffer_size,
                    SizeTPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        Iterable<PolyglotException.StackFrame> traceElements = e.getPolyglotStackTrace();
        for (PolyglotException.StackFrame trace : traceElements) {
            if (trace.isGuestFrame()) {
                pw.println(trace);
            }
        }
        writeUTF8String(sw.toString(), buffer, buffer_size, result);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_exception_get_message", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the error message as a 0 terminated and UTF-8 encoded string.",
                    "",
                    "@param exception Handle to the exception object.",
                    "@param buffer Where to write the UTF-8 string representing the error message. Can be NULL.",
                    "@param buffer_size Size of the user-supplied buffer.",
                    "@param result If buffer is NULL, this will contain the byte size of the error message string, otherwise, it will contain the number of bytes written. Note in either case this length does not contain the 0 terminator written to the end of the buffer",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/PolyglotException.html#getMessage--",
                    "@since 22.3",
    })
    public static PolyglotStatus poly_exception_get_message(PolyglotIsolateThread thread, PolyglotExceptionHandle exception, CCharPointer buffer, UnsignedWord buffer_size,
                    SizeTPointer result) {
        resetErrorState();
        nullCheck(exception, "exception");
        nullCheck(result, "result");
        PolyglotException e = fetchHandle(exception);

        writeUTF8String(e.getMessage(), buffer, buffer_size, result);
        return poly_ok;
    }

    @CEntryPoint(name = "poly_register_recurring_callback", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Registers (or unregisters) a recurring callback in the current thread to be",
                    "called approximately at the specified interval. The callback's result value is",
                    "ignored. Any previously registered callback is replaced. Passing NULL for the",
                    "the callback function removes a previously registered callback (in which case",
                    "the interval and data parameters are ignored).",
                    "",
                    "@param intervalNanos interval between invocations in nanoseconds.",
                    "@param callback the function that is invoked.",
                    "@param data a custom pointer to be passed to each invocation of the callback.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@since 22.2",
    })
    @Uninterruptible(reason = "Prevent safepoint checks before pausing recurring callback.")
    public static PolyglotStatus poly_register_recurring_callback(PolyglotIsolateThread thread, long intervalNanos, PolyglotCallback callback, VoidPointer data) {
        ThreadingSupportImpl.pauseRecurringCallback("Prevent recurring callback execution before returning.");
        try {
            return doRegisterRecurringCallback(intervalNanos, callback, data);
        } finally {
            ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
        }
    }

    @Uninterruptible(reason = "Not really, but our caller is.", calleeMustBe = false)
    private static PolyglotStatus doRegisterRecurringCallback(long intervalNanos, PolyglotCallback callback, VoidPointer data) {
        return doRegisterRecurringCallback0(intervalNanos, callback, data);
    }

    private static PolyglotStatus doRegisterRecurringCallback0(long intervalNanos, PolyglotCallback callback, VoidPointer data) {
        ensureLocalsInitialized();
        resetErrorState();
        if (!ThreadingSupportImpl.isRecurringCallbackSupported()) {
            return poly_generic_failure;
        }
        if (callback.isNull()) {
            Threading.registerRecurringCallback(-1, null, null);
            setNewInfoHandle(WordFactory.nullPointer());
            return poly_ok;
        }
        PolyglotCallbackInfoInternal info = new PolyglotCallbackInfoInternal(new ObjectHandle[0], data);
        var infoHandle = (PolyglotCallbackInfo) objectHandles.create(info);
        var handles = getHandles();
        RecurringCallback recurringCallback = access -> {
            handles.freezeHandleCreation();
            try {
                callback.invoke((PolyglotIsolateThread) CurrentIsolate.getCurrentThread(), infoHandle);
                CallbackException ce = threadLocals.get().callbackException;
                if (ce != null) {
                    access.throwException(ce);
                }
            } finally {
                threadLocals.get().callbackException = null;
                handles.unfreezeHandleCreation();
            }
        };
        try {
            Threading.registerRecurringCallback(intervalNanos, TimeUnit.NANOSECONDS, recurringCallback);
        } catch (Throwable t) {
            objectHandles.destroy(infoHandle);
            throw t;
        }

        setNewInfoHandle(infoHandle);

        return poly_ok;
    }

    private static void setNewInfoHandle(ObjectHandle infoHandle) {
        ObjectHandle previousInfoHandle = threadLocals.get().recurringCallbackInfoHandle;
        if (WordFactory.nullPointer().notEqual(previousInfoHandle)) {
            objectHandles.destroy(previousInfoHandle);
        }
        threadLocals.get().recurringCallbackInfoHandle = infoHandle;
    }

    private static class PolyglotCallbackInfoInternal {
        ObjectHandle[] arguments;
        VoidPointer data;

        PolyglotCallbackInfoInternal(ObjectHandle[] arguments, VoidPointer data) {
            this.arguments = arguments;
            this.data = data;
        }
    }

    @CEntryPoint(name = "poly_register_log_handler_callbacks", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Registers callbacks for log functions. Note that this is expected to be called exactly once.",
                    "The API defined in LogHandler is extended so that an additional data pointer is passed back with each action.",
                    "",
                    "@param data Value passed back with each action.",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/LogHandler.html",
                    "@since 23.1",
    })
    public static PolyglotStatus poly_register_log_handler_callbacks(PolyglotIsolateThread thread, PolyglotNativeAPITypes.PolyglotLogCallback logCallback,
                    PolyglotNativeAPITypes.PolyglotFlushCallback flushCallback, PolyglotNativeAPITypes.PolyglotFatalErrorCallback fatalErrorCallback, VoidPointer data) {
        resetErrorState();
        nullCheck(logCallback, "logCallback");
        nullCheck(flushCallback, "flushCallback");
        nullCheck(fatalErrorCallback, "fatalErrorCallback");

        PolyglotNativeLogHandler handler = (PolyglotNativeLogHandler) ImageSingletons.lookup(LogHandler.class);
        if (handler.log.isNonNull()) {
            throw reportError("poly_register_log_handler_callbacks called multiple times", poly_generic_failure);
        }
        handler.log = logCallback;
        handler.flush = flushCallback;
        handler.fatalError = fatalErrorCallback;
        handler.data = data;

        return poly_ok;
    }

    @CEntryPoint(name = "poly_perf_data_get_address_of_int64_t", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Gets the address of the int64_t value for a performance data entry of type long. Performance data support must be enabled.",
                    "",
                    "@param key_utf8 0 terminated and UTF-8 encoded key that identifies the performance data entry.",
                    "@param result a pointer to which the address of the int64_t value will be written.",
                    "@return poly_ok if everything went ok, otherwise an error occurred.",
                    "",
                    "@since 22.3",
    })
    public static PolyglotStatus poly_perf_data_get_address_of_int64_t(PolyglotIsolateThread thread, CCharPointer key_utf8, CInt64PointerPointer result) {
        resetErrorState();
        nullCheck(key_utf8, "key_utf8");
        nullCheck(result, "result");
        String key = CTypeConversion.utf8ToJavaString(key_utf8);
        if (!ImageSingletons.lookup(PerfDataSupport.class).hasLong(key)) {
            throw reportError("Key " + key + " is not a valid performance data entry key.", poly_generic_failure);
        }
        CInt64Pointer ptr = (CInt64Pointer) ImageSingletons.lookup(PerfDataSupport.class).getLong(key);
        result.write(ptr);
        return poly_ok;
    }

    @Uninterruptible(reason = "Must be uninterruptible until it gets immune to safepoints.")
    @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
    @CEntryPoint(name = "poly_handle_segfault_in_thread", documentation = {
                    "Prints information about the segfault and calls the fatal error handler. Note that this method may only be called from an already attached thread.",
                    "",
                    "@param signal_info the OS-specific signal information, e.g., siginfo_t* on Linux.",
                    "@param signal_handler_context the OS-specific signal handler context, e.g., ucontext_t* on Linux.",
                    "@return this method never returns because it invokes the fatal error handling instead.",
                    "",
                    "@since 23.1.2",
    })
    public static void poly_handle_segfault_in_thread(PolyglotIsolateThread thread, PointerBase signal_info, RegisterDumper.Context signal_handler_context) {
        /* Don't reset the error state so that we see it in the crash log. */
        SubstrateSegfaultHandler.dump(signal_info, signal_handler_context);
    }

    @Uninterruptible(reason = "Must be uninterruptible until it gets immune to safepoints.")
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @CEntryPoint(name = "poly_handle_segfault_in_isolate", documentation = {
                    "Prints information about the segfault and calls the fatal error handler. Note that this method may only be called from an already attached thread.",
                    "",
                    "@param signal_info the OS-specific signal information, e.g., siginfo_t* on Linux.",
                    "@param signal_handler_context the OS-specific signal handler context, e.g., ucontext_t* on Linux.",
                    "@return this method never returns because it invokes the fatal error handling instead.",
                    "",
                    "@since 23.1.2",
    })
    public static void poly_handle_segfault_in_isolate(PolyglotIsolate isolate, PointerBase signal_info, RegisterDumper.Context signal_handler_context) {
        /* Don't reset the error state so that we see it in the crash log. */
        SubstrateSegfaultHandler.enterIsolateAsyncSignalSafe(isolate);
        SubstrateSegfaultHandler.dump(signal_info, signal_handler_context);
    }

    private static void writeUTF8String(String valueString, CCharPointer buffer, UnsignedWord length, SizeTPointer result) {
        result.write(CTypeConversion.toCString(valueString, UTF8_CHARSET, buffer, length));
    }

    private static List<Language> sortedLangs(Engine engine) {

        return engine.getLanguages().entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).map(Entry::getValue).collect(Collectors.toList());
    }

    private static void resetErrorState() {
        ThreadLocalState state = ensureLocalsInitialized();
        resetErrorStateAtomically(state);
    }

    @Uninterruptible(reason = "An exception thrown by recurring callback can cause inconsistency, leaks or stale pointers.")
    private static void resetErrorStateAtomically(ThreadLocalState state) {
        state.lastErrorCode = poly_ok;
        state.lastException = null;
        freeUnmanagedErrorState(state);
    }

    @Uninterruptible(reason = "An exception thrown by recurring callback can cause inconsistency, leaks or stale pointers.")
    private static void freeUnmanagedErrorState(ThreadLocalState state) {
        if (state.lastExceptionUnmanagedInfo.isNonNull()) {
            assert state.lastExceptionUnmanagedInfo.getErrorMessage().isNonNull();

            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(state.lastExceptionUnmanagedInfo.getErrorMessage());
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(state.lastExceptionUnmanagedInfo);
            state.lastExceptionUnmanagedInfo = WordFactory.nullPointer();
        }
    }

    private static RuntimeException reportError(String message, PolyglotStatus errorCode) {
        throw new PolyglotNativeAPIError(errorCode, message);
    }

    private static final class ExceptionHandler implements CEntryPoint.ExceptionHandler {
        @Uninterruptible(reason = "exception handler")
        static PolyglotStatus handle(Throwable t) {
            ThreadLocalState state = threadLocals.get();
            if (state == null) { // caught exception from recurring callback early during init?
                return poly_generic_failure;
            }
            PolyglotStatus errorCode = t instanceof PolyglotNativeAPIError ? ((PolyglotNativeAPIError) t).getCode() : poly_generic_failure;
            if (t instanceof PolyglotException) {
                // We should never have both a PolyglotException and an error at the same time
                state.polyglotException = (PolyglotException) t;
                errorCode = poly_pending_exception;
            }
            state.lastException = t;
            state.lastErrorCode = errorCode;
            return errorCode;
        }
    }

    private static final class GenericFailureExceptionHandler implements CEntryPoint.ExceptionHandler {
        @Uninterruptible(reason = "exception handler")
        static PolyglotStatus handle(Throwable t) {
            return poly_generic_failure;
        }
    }

    private static PolyglotNativeAPITypes.PolyglotHandle createHandle(Object result) {
        return getHandles().create(result);
    }

    private static <T> T fetchHandle(PolyglotNativeAPITypes.PolyglotHandle object) {
        if (object.equal(PolyglotThreadLocalHandles.nullHandle())) {
            return null;
        }

        if (PolyglotThreadLocalHandles.isInRange(object)) {
            return getHandles().getObject(object);
        }

        if (objectHandles.isInRange(object)) {
            return objectHandles.get(object);
        }

        throw new RuntimeException("Invalid poly_reference or poly_handle.");
    }

    private static <T> T fetchHandleOrNull(PolyglotNativeAPITypes.PolyglotHandle object) {
        if (object.equal(PolyglotThreadLocalHandles.nullHandle())) {
            return null;
        }

        if (PolyglotThreadLocalHandles.isInRange(object)) {
            return getHandles().getObject(object);
        }

        if (objectHandles.isInRange(object)) {
            return objectHandles.get(object);
        }

        return null;
    }

    public static class CallbackException extends RuntimeException {
        static final long serialVersionUID = 123123098097526L;

        CallbackException(String message) {
            super(message);
        }
    }

    @CEntryPoint(name = "poly_context_builder_timezone", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Sets timezone for a <code>poly_context_builder</code>.",
                    "",
                    "@param context_builder that is modified.",
                    "@param zone_utf8 id of a timezone to be set via ZoneId.of().",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#timeZone-java.time.ZoneId-",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_context_builder_timezone(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, @CConst CCharPointer zone_utf8) {
        resetErrorState();
        nullCheck(context_builder, "context_builder");
        nullCheck(zone_utf8, "zone_utf8");

        String zoneString = CTypeConversion.utf8ToJavaString(zone_utf8);
        Context.Builder contextBuilder = fetchHandle(context_builder);
        contextBuilder.timeZone(ZoneId.of(zoneString));
        return poly_ok;
    }

    @CEntryPoint(name = "poly_vmruntime_initialize", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Initializes the VM: Runs all startup hooks that were registered during image building.",
                    "",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/VMRuntime.html#initialize--",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_vmruntime_initialize(PolyglotIsolateThread thread) {
        resetErrorState();

        VMRuntime.initialize();
        return poly_ok;
    }

    @CEntryPoint(name = "poly_vmruntime_shutdown", exceptionHandler = ExceptionHandler.class, documentation = {
                    "Shuts down the VM: Runs all shutdown hooks and waits for all finalization to complete.",
                    "",
                    "@return poly_ok if all works, poly_generic_error if there is a failure.",
                    "",
                    "@see https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/VMRuntime.html#shutdown--",
                    "@since 23.0",
    })
    public static PolyglotStatus poly_vmruntime_shutdown(PolyglotIsolateThread thread) {
        resetErrorState();

        VMRuntime.shutdown();
        return poly_ok;
    }

    public static class PolyglotNativeLogHandler implements LogHandler {
        VoidPointer data;
        PolyglotNativeAPITypes.PolyglotLogCallback log;
        PolyglotNativeAPITypes.PolyglotFlushCallback flush;
        PolyglotNativeAPITypes.PolyglotFatalErrorCallback fatalError;

        @Platforms(Platform.HOSTED_ONLY.class)
        PolyglotNativeLogHandler() {
        }

        @Override
        public void log(CCharPointer bytes, UnsignedWord length) {
            if (log.isNonNull()) {
                log.invoke(bytes, length, data);
            }
        }

        @Override
        public void flush() {
            if (flush.isNonNull()) {
                flush.invoke(data);
            }
        }

        @Override
        public void fatalError() {
            if (fatalError.isNonNull()) {
                markThreadAsCrashedAndInvokeFatalError();
            }

            /*
             * If we reach this point, then we must abort since the execution cannot proceed.
             */
            LibC.abort();
        }

        @Uninterruptible(reason = "Don't access objects in the collected Java heap after marking the thread as crashed.")
        private void markThreadAsCrashedAndInvokeFatalError() {
            SafepointBehavior.markThreadAsCrashed();
            fatalError.invoke(data);
        }
    }
}
