/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions.standard;

import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.VersionFilter;
import sun.misc.Signal;
import sun.misc.SignalHandler;

@EspressoSubstitutions
public final class Target_sun_misc_Signal {
    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, "sun.misc.Signal");

    // Avoid going through JVM_FindSignal which has a char* argument
    @SuppressWarnings("unused")
    @Substitution(languageFilter = VersionFilter.Java8OrEarlier.class)
    @TruffleBoundary
    public static int findSignal(@JavaType(String.class) StaticObject name,
                    @Inject Meta meta) {
        if (StaticObject.isNull(name)) {
            throw meta.throwNullPointerException();
        }
        try {
            return new Signal(meta.toHostString(name)).getNumber();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    @SuppressWarnings("unused")
    @Substitution(languageFilter = VersionFilter.Java8OrEarlier.class)
    @TruffleBoundary
    public static void raise(@JavaType(Signal.class) StaticObject signal,
                    @Inject Meta meta) {
        if (StaticObject.isNull(signal)) {
            throw meta.throwNullPointerException();
        }
        Signal hostSignal = asHostSignal(signal, meta);
        logger.finer(() -> "raising " + hostSignal);
        try {
            Signal.raise(hostSignal);
        } catch (IllegalArgumentException e) {
            logger.fine(() -> "failed to raise " + hostSignal + ": " + e.getMessage());
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, meta.toGuestString(e.getMessage()));
        }
    }

    private static Signal asHostSignal(StaticObject signal, Meta meta) {
        StaticObject guestName = meta.sun_misc_Signal_name.getObject(signal);
        return new Signal(meta.toHostString(guestName));
    }

    private static StaticObject asGuestSignal(Signal signal, Meta meta) {
        StaticObject guestSignal = meta.sun_misc_Signal.allocateInstance(meta.getContext());
        meta.sun_misc_Signal_init_String.invokeDirectSpecial(guestSignal, meta.toGuestString(signal.getName()));
        return guestSignal;
    }

    @SuppressWarnings("unused")
    @Substitution(languageFilter = VersionFilter.Java8OrEarlier.class)
    @TruffleBoundary
    public static @JavaType(SignalHandler.class) StaticObject handle(@JavaType(Signal.class) StaticObject signal, @JavaType(SignalHandler.class) StaticObject handler,
                    @Inject Meta meta) {
        if (StaticObject.isNull(signal)) {
            throw meta.throwNullPointerException();
        }
        if (!meta.getContext().getEspressoEnv().EnableSignals) {
            logger.fine(() -> "failed to setup handler for " + asHostSignal(signal, meta) + ": signal handling is disabled ");
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Signal API is disabled");
        }
        Signal hostSignal = asHostSignal(signal, meta);
        SignalHandler hostHandler = asHostHandler(handler, meta);
        logger.finer(() -> "setting up handler for " + hostSignal + ": " + hostHandler);
        try {
            SignalHandler oldHandler = Signal.handle(hostSignal, hostHandler);
            return asGuestHandler(oldHandler, meta);
        } catch (IllegalArgumentException e) {
            logger.fine(() -> "failed to setup handler for " + hostSignal + ": " + e.getMessage());
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, meta.toGuestString(e.getMessage()));
        }
    }

    private static StaticObject asGuestHandler(SignalHandler handler, Meta meta) {
        if (handler == null) {
            return StaticObject.NULL;
        } else if (handler instanceof HostSignalHandler) {
            return ((HostSignalHandler) handler).guestHandler;
        } else if (handler == SignalHandler.SIG_DFL) {
            return meta.sun_misc_SignalHandler_SIG_DFL.getObject(meta.sun_misc_SignalHandler.tryInitializeAndGetStatics());
        } else if (handler == SignalHandler.SIG_IGN) {
            return meta.sun_misc_SignalHandler_SIG_IGN.getObject(meta.sun_misc_SignalHandler.tryInitializeAndGetStatics());
        }
        throw EspressoError.shouldNotReachHere();
    }

    // uses slot 1
    private static SignalHandler asHostHandler(StaticObject handler, Meta meta) {
        if (StaticObject.isNull(handler)) {
            return null;
        }
        if (meta.sun_misc_NativeSignalHandler.isAssignableFrom(handler.getKlass())) {
            long rawHandler = meta.sun_misc_NativeSignalHandler_handler.getLong(handler);
            if (rawHandler == 0) {
                return SignalHandler.SIG_DFL;
            } else if (rawHandler == 1) {
                return SignalHandler.SIG_IGN;
            } else {
                throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, meta.toGuestString("Unsupported: arbitrary native signal handlers"));
            }
        }
        return HostSignalHandler.get(meta, handler);
    }

    private static final class HostSignalHandler implements SignalHandler {
        private final Meta meta;
        private final StaticObject guestHandler;

        HostSignalHandler(Meta meta, StaticObject guestHandler) {
            this.meta = meta;
            this.guestHandler = guestHandler;
        }

        public static SignalHandler get(Meta meta, StaticObject handler) {
            WeakHashMap<StaticObject, SignalHandler> hostSignalHandlers = meta.getContext().getHostSignalHandlers();
            SignalHandler hostHandler;
            synchronized (hostSignalHandlers) {
                hostHandler = hostSignalHandlers.get(handler);
                if (hostHandler == null) {
                    hostHandler = new HostSignalHandler(meta, handler);
                    hostSignalHandlers.put(handler, hostHandler);
                }
            }
            return hostHandler;
        }

        @Override
        public void handle(Signal sig) {
            // the VM will call this on an un-attached thread
            Object prev = meta.getContext().getEnv().getContext().enter(null);
            try {
                meta.sun_misc_SignalHandler_handle.invokeDirectInterface(guestHandler, asGuestSignal(sig, meta));
            } finally {
                meta.getContext().getEnv().getContext().leave(null, prev);
            }
        }
    }
}
