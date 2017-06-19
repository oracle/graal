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
import static com.oracle.truffle.api.vm.VMAccessor.JAVAINTEROP;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

class PolyglotLanguageContextImpl implements VMObject {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    final PolyglotContextImpl context;
    final PolyglotLanguageImpl language;
    final Map<Object, CallTarget> sourceCache = new HashMap<>();
    final Map<Class<?>, PolyglotValueImpl> valueCache = new HashMap<>();
    final OptionValues optionValues;
    final Value nullValue;
    final String[] applicationArguments;
    volatile Env env;

    PolyglotLanguageContextImpl(PolyglotContextImpl context, PolyglotLanguageImpl language, OptionValues optionValues, String[] applicationArguments) {
        this.context = context;
        this.language = language;
        this.optionValues = optionValues;
        this.applicationArguments = applicationArguments == null ? EMPTY_STRING_ARRAY : applicationArguments;

        PolyglotValueImpl.createDefaultValueCaches(this);
        nullValue = toHostValue(toGuestValue(null));
    }

    void ensureInitialized() {
        language.ensureInitialized();

        if (env == null) {
            synchronized (this) {
                if (env == null) {
                    checkAccess();
                    env = LANGUAGE.createEnv(this, language.info,
                                    context.out,
                                    context.err,
                                    context.in, new HashMap<>(), getOptionValues(), applicationArguments);
                    LANGUAGE.postInitEnv(env);
                }
            }
        }
    }

    OptionValues getOptionValues() {
        return optionValues;
    }

    private void checkAccess() {
        boolean accessPermitted = language.isHost() || language.cache.isInternal() || context.singlePublicLanguage == null || context.singlePublicLanguage == language;
        if (!accessPermitted) {
            throw new IllegalStateException(String.format("Access to language '%s' is not permitted. ", language.getId()));
        }
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return context.getEngine();
    }

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

    Object toGuestValue(Object receiver) {
        if (receiver instanceof Value) {
            Value receiverValue = (Value) receiver;
            PolyglotValueImpl argumentCache = (PolyglotValueImpl) context.engine.impl.getAPIAccess().getImpl(receiverValue);
            if (argumentCache.languageContext.getEngine() != getEngine()) {
                throw engineError(new IllegalArgumentException(String.format("Values cannot be passed from one engine to another. " +
                                "The current value originates from engine 0x%s and the argument originates from engine 0x%s.",
                                Integer.toHexString(getEngine().hashCode()), Integer.toHexString(argumentCache.languageContext.getEngine().hashCode()))));
            } else if (argumentCache.languageContext.context.boundThread != context.boundThread) {
                throw engineError(new IllegalArgumentException(String.format("A given value argument must be bound to the same thread. " +
                                "The current value is bound to thread %s and the argument is bound to %s." +
                                "The involved languages %s and %s don't support multi-threaded access of values.",
                                context.boundThread, argumentCache.languageContext.context.boundThread,
                                language.api.getName(), argumentCache.languageContext.language.api.getName())));
            }
            return context.engine.impl.getAPIAccess().getReceiver(receiverValue);
        } else if (PolyglotImpl.isGuestPrimitive(receiver)) {
            return receiver;
        } else if (receiver instanceof Proxy) {
            return PolyglotProxyImpl.toProxyGuestObject(this, (Proxy) receiver);
        } else {
            return JAVAINTEROP.toJavaGuestObject(receiver, this);
        }
    }

    Value toHostValue(Object value) {
        assert value != null;
        assert !(value instanceof Value);
        Object receiver = value;
        PolyglotValueImpl cache = valueCache.get(receiver.getClass());
        if (cache == null) {
            if (receiver instanceof Proxy) {
                receiver = PolyglotProxyImpl.toProxyGuestObject(this, (Proxy) receiver);
            } else {
                receiver = JAVAINTEROP.toJavaGuestObject(receiver, this);
            }

            cache = valueCache.get(receiver.getClass());
            if (cache == null) {
                cache = PolyglotValueImpl.createInteropValueCache(this);
                valueCache.put(receiver.getClass(), cache);
            }
        }
        return context.engine.impl.getAPIAccess().newValue(receiver, cache);
    }

    Value[] toHostValues(Object[] values, int startIndex) {
        Value[] args = new Value[values.length - startIndex];
        for (int i = startIndex; i < values.length; i++) {
            args[i - startIndex] = toHostValue(values[i]);
        }
        return args;
    }

    Value[] toHostValues(Object[] values) {
        Value[] args = new Value[values.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = toHostValue(values[i]);
        }
        return args;
    }

}
