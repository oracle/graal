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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.PolyglotImpl.engineError;
import static com.oracle.truffle.api.vm.PolyglotImpl.isGuestInteropValue;
import static com.oracle.truffle.api.vm.VMAccessor.JAVAINTEROP;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

final class PolyglotLanguageContext implements VMObject {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    final PolyglotContextImpl context;
    final PolyglotLanguage language;
    final Map<Object, CallTarget> sourceCache = new HashMap<>();
    final Map<Class<?>, PolyglotValue> valueCache = new HashMap<>();
    final Map<String, Object> config;
    final PolyglotValue defaultValueCache;
    final OptionValuesImpl optionValues;
    final Value nullValue;
    final String[] applicationArguments;
    volatile boolean disposed;
    volatile Env env;

    PolyglotLanguageContext(PolyglotContextImpl context, PolyglotLanguage language, OptionValuesImpl optionValues, String[] applicationArguments, Map<String, Object> config) {
        this.context = context;
        this.language = language;
        this.optionValues = optionValues;
        this.applicationArguments = applicationArguments == null ? EMPTY_STRING_ARRAY : applicationArguments;
        this.config = config;
        PolyglotValue.createDefaultValueCaches(this);
        nullValue = toHostValue(toGuestValue(null));
        defaultValueCache = new PolyglotValue.Default(this);
    }

    Object enter() {
        return context.enter();
    }

    void leave(Object prev) {
        context.leave(prev);
    }

    void dispose() {
        if (env != null) {
            synchronized (this) {
                if (env != null) {
                    try {
                        checkAccess();
                        LANGUAGE.dispose(env);
                        env = null;
                    } catch (Throwable t) {
                        throw PolyglotImpl.wrapGuestException(this, t);
                    }
                }
                disposed = true;
            }
        }
    }

    boolean ensureInitialized() {
        language.ensureInitialized();

        if (env == null) {
            synchronized (this) {
                if (env == null) {
                    checkAccess();
                    env = LANGUAGE.createEnv(this, language.info,
                                    context.out,
                                    context.err,
                                    context.in, config, getOptionValues(), applicationArguments);
                    LANGUAGE.postInitEnv(env);
                    return true;
                }
            }
        }
        return false;
    }

    OptionValuesImpl getOptionValues() {
        return optionValues;
    }

    void checkAccess() {
        if (disposed) {
            throw new IllegalStateException(String.format("Context is already disposed for language %s.", language.getId()));
        }
        boolean accessPermitted = language.isHost() || language.cache.isInternal() || context.allowedPublicLanguages.contains(language.info.getId());
        if (!accessPermitted) {
            throw new IllegalStateException(String.format("Access to language '%s' is not permitted. ", language.getId()));
        }
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return context.getEngine();
    }

    @TruffleBoundary
    Object[] toGuestValues(Object[] args) {
        Object[] newArgs = args;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Object newArg = toGuestValue(arg);
            if (newArg != arg) {
                if (newArgs == args) {
                    newArgs = Arrays.copyOf(args, args.length);
                }
                newArgs[i] = newArg;
            }
        }
        return newArgs;
    }

    ToGuestValuesNode createToGuestValues() {
        return new ToGuestValuesNode();
    }

    final class ToGuestValuesNode {

        @CompilationFinal private int cachedLength = -1;
        @CompilationFinal(dimensions = 1) private ToGuestValueNode[] toGuestValue;
        @CompilationFinal private boolean needsCopy = false;

        private ToGuestValuesNode() {
        }

        @ExplodeLoop
        Object[] execute(Object[] args) {
            if (cachedLength == -1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedLength = args.length;
                toGuestValue = new ToGuestValueNode[cachedLength];
                for (int i = 0; i < cachedLength; i++) {
                    toGuestValue[i] = createToGuestValue();
                }
            }
            if (cachedLength == args.length) {
                // fast path
                Object[] newArgs = fastToGuestValuesUnroll(args);
                return newArgs;
            } else {
                if (cachedLength != -2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedLength = -2;
                    toGuestValue = Arrays.copyOf(toGuestValue, 1);
                    if (toGuestValue[0] == null) {
                        toGuestValue[0] = createToGuestValue();
                    }
                }
                return fastToGuestValues(args);
            }
        }

        /*
         * Specialization for constant number of arguments. Uses a profile for each argument.
         */
        @ExplodeLoop
        private Object[] fastToGuestValuesUnroll(Object[] args) {
            Object[] newArgs = needsCopy ? new Object[toGuestValue.length] : args;
            for (int i = 0; i < toGuestValue.length; i++) {
                Object arg = args[i];
                Object newArg = toGuestValue[i].execute(arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = Arrays.copyOf(args, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        /*
         * Specialization that supports multiple argument lengths but uses a single profile for all
         * arguments.
         */
        private Object[] fastToGuestValues(Object[] args) {
            assert toGuestValue[0] != null;
            Object[] newArgs = needsCopy ? new Object[args.length] : args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object newArg = toGuestValue[0].execute(arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = Arrays.copyOf(args, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

    }

    final class ToGuestValueNode {

        @CompilationFinal private Class<?> cachedClass;

        private ToGuestValueNode() {

        }

        Object execute(Object receiver) {
            if (cachedClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedClass = receiver.getClass();
            }
            if (cachedClass != ToGuestValueNode.class) {
                if (cachedClass.isInstance(receiver)) {
                    return toGuestValue(cachedClass.cast(receiver));
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = ToGuestValueNode.class; // switch to generic
                }
            }
            return slowPath(receiver);
        }

        @TruffleBoundary
        private Object slowPath(Object receiver) {
            return toGuestValue(receiver);
        }

    }

    Object toGuestValue(Object receiver) {
        if (receiver instanceof Value) {
            Value receiverValue = (Value) receiver;
            PolyglotValue argumentCache = (PolyglotValue) context.engine.impl.getAPIAccess().getImpl(receiverValue);
            Thread valueThread = argumentCache.languageContext.context.boundThread.get();
            Thread currentThread = context.boundThread.get();

            if (argumentCache.languageContext.getEngine() != getEngine()) {
                CompilerDirectives.transferToInterpreter();
                throw engineError(new IllegalArgumentException(String.format("Values cannot be passed from one engine to another. " +
                                "The current value originates from engine 0x%s and the argument originates from engine 0x%s.",
                                Integer.toHexString(getEngine().hashCode()), Integer.toHexString(argumentCache.languageContext.getEngine().hashCode()))));
            } else if (valueThread != null && currentThread != null && valueThread != currentThread) {
                CompilerDirectives.transferToInterpreter();
                throw engineError(new IllegalArgumentException(String.format("A given value argument must be bound to the same or no thread. " +
                                "The current value is bound to thread %s and the argument is bound to %s." +
                                "The involved languages %s and %s don't support multi-threaded access of values.",
                                context.boundThread, valueThread,
                                language.api.getName(), argumentCache.languageContext.language.api.getName())));
            }
            return context.engine.impl.getAPIAccess().getReceiver(receiverValue);
        } else if (PolyglotImpl.isGuestPrimitive(receiver)) {
            return receiver;
        } else if (receiver instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(this, (Proxy) receiver);
        } else {
            return JAVAINTEROP.toJavaGuestObject(receiver, this);
        }
    }

    ToGuestValueNode createToGuestValue() {
        return new ToGuestValueNode();
    }

    @TruffleBoundary
    Value toHostValue(Object value) {
        assert value != null;
        assert !(value instanceof Value);
        Object receiver = value;
        PolyglotValue cache = valueCache.get(receiver.getClass());
        final APIAccess apiAccess = context.engine.impl.getAPIAccess();
        if (cache == null) {
            receiver = convertToInterop(receiver);
            cache = lookupValueCache(receiver);
        }
        return apiAccess.newValue(receiver, cache);
    }

    Object convertToInterop(Object receiver) {
        if (receiver instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(PolyglotLanguageContext.this, (Proxy) receiver);
        } else {
            return JAVAINTEROP.toJavaGuestObject(receiver, PolyglotLanguageContext.this);
        }
    }

    PolyglotValue lookupValueCache(Object value) {
        assert value instanceof TruffleObject;
        PolyglotValue cache = valueCache.get(value.getClass());
        if (cache == null) {
            cache = PolyglotValue.createInteropValueCache(PolyglotLanguageContext.this, (TruffleObject) value, value.getClass());
            valueCache.put(value.getClass(), cache);
        }
        return cache;
    }

    final class ToHostValueNode {

        final APIAccess apiAccess = context.engine.impl.getAPIAccess();
        @CompilationFinal Class<?> cachedClass;
        @CompilationFinal PolyglotValue cachedValue;
        @CompilationFinal Class<?> cachedFallbackClass;
        @CompilationFinal PolyglotValue cachedFallbackValue;

        private ToHostValueNode() {
        }

        Value execute(Object value) {
            Object receiver = value;
            if (cachedClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedClass = receiver.getClass();
                cachedValue = valueCache.get(cachedClass);
                PolyglotValue cache = cachedValue;
                if (cachedValue == null) {
                    receiver = convertToInterop(receiver);
                    cachedFallbackClass = receiver.getClass();
                    cachedFallbackValue = lookupValueCache(receiver);
                    cache = cachedFallbackValue;
                }
                return apiAccess.newValue(receiver, cache);
            } else if (cachedClass != ToHostValueNode.class) {
                if (cachedClass.isInstance(value)) {
                    receiver = cachedClass.cast(receiver);
                    PolyglotValue cache = cachedValue;
                    if (cache == null) {
                        receiver = convertToInterop(receiver);
                        if (cachedFallbackClass.isInstance(receiver)) {
                            cache = cachedFallbackValue;
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            cachedClass = ToHostValueNode.class;
                            cache = lookupValueCache(receiver.getClass());
                        }
                    }
                    return apiAccess.newValue(receiver, cache);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = ToHostValueNode.class; // switch to generic
                    // fall through to generic
                }
            }
            return toHostValue(value);

        }

    }

    ToHostValueNode createToHostValue() {
        return new ToHostValueNode();
    }

    @TruffleBoundary
    Value[] toHostValues(Object[] values, int startIndex) {
        Value[] args = new Value[values.length - startIndex];
        for (int i = startIndex; i < values.length; i++) {
            args[i - startIndex] = toHostValue(values[i]);
        }
        return args;
    }

    @TruffleBoundary
    Value[] toHostValues(Object[] values) {
        Value[] args = new Value[values.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = toHostValue(values[i]);
        }
        return args;
    }

    Object lookupGuest(String symbolName) {
        ensureInitialized();
        return LANGUAGE.lookupSymbol(env, symbolName);
    }

    Value lookupHost(String symbolName) {
        Object symbol = lookupGuest(symbolName);
        Value resolvedSymbol = null;
        if (symbol != null) {
            assert isGuestInteropValue(symbol);
            resolvedSymbol = toHostValue(symbol);
        }
        return resolvedSymbol;
    }

}
