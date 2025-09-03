/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.classfile.ParsingContext;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.perf.TimerCollection;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class ClassLoadingEnv implements LanguageAccess {
    private final AtomicLong klassIdProvider = new AtomicLong();
    private final AtomicLong loaderIdProvider = new AtomicLong();

    private final EspressoLanguage language;
    private final TruffleLogger logger;
    private final TimerCollection timers;

    @CompilationFinal private Meta meta;

    public ClassLoadingEnv(EspressoLanguage language, TruffleLogger logger, TimerCollection timers) {
        this.language = language;
        this.logger = logger;
        this.timers = timers;
    }

    @Override
    public EspressoLanguage getLanguage() {
        return language;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public TruffleLogger getLogger() {
        return logger;
    }

    public TimerCollection getTimers() {
        return timers;
    }

    private static boolean shouldCacheClass(ClassRegistry.ClassDefinitionInfo info) {
        /*
         * Cached class representations must not contain context-dependent objects that cannot be
         * shared on a language level. Anonymous classes, by definition, contain a Klass
         * self-reference in the constant pool.
         */
        return !info.isAnonymousClass() && !info.isHidden();
    }

    public boolean shouldCacheClass(ClassRegistry.ClassDefinitionInfo info, StaticObject loader) {
        return shouldCacheClass(info) && // No cached hidden class
                        (loaderIsBootOrPlatform(loader) || loaderIsAppLoader(loader));
    }

    public boolean isReflectPackage(Symbol<Name> pkg) {
        /*
         * Note: This class is created too early in the init process to make this variable a final
         * field.
         */
        Symbol<Name> reflectPackage = getLanguage().getJavaVersion().java8OrEarlier()
                        ? EspressoSymbols.Names.sun_reflect
                        : EspressoSymbols.Names.jdk_internal_reflect;
        return pkg == reflectPackage;
    }

    @SuppressWarnings("static-method")
    public boolean loaderIsBoot(StaticObject loader) {
        return StaticObject.isNull(loader);
    }

    public boolean loaderIsBootOrPlatform(StaticObject loader) {
        return loaderIsBoot(loader) ||
                        (language.getJavaVersion().java9OrLater() && meta.jdk_internal_loader_ClassLoaders$PlatformClassLoader.isAssignableFrom(loader.getKlass()));
    }

    public boolean loaderIsAppLoader(StaticObject loader) {
        return !loaderIsBoot(loader) &&
                        (meta.jdk_internal_loader_ClassLoaders$AppClassLoader.isAssignableFrom(loader.getKlass()));
    }

    public boolean loaderIsReflection(StaticObject loader) {
        return meta.sun_reflect_DelegatingClassLoader != null &&
                        !loaderIsBoot(loader) &&
                        meta.sun_reflect_DelegatingClassLoader.isAssignableFrom(loader.getKlass());
    }

    public long getNewKlassId() {
        long id = klassIdProvider.getAndIncrement();
        if (id < 0) {
            throw EspressoError.shouldNotReachHere("Exhausted klass IDs");
        }
        return id;
    }

    public long getNewLoaderId() {
        long id = loaderIdProvider.getAndIncrement();
        if (id < 0) {
            throw EspressoError.shouldNotReachHere("Exhausted loader IDs");
        }
        return id;
    }

    public static ParsingContext createParsingContext(ClassLoadingEnv env, boolean ensureStrongReferences) {
        return new ParsingContext() {

            final Logger truffleEnvLogger = new Logger() {
                @Override
                public void log(String message) {
                    env.getLogger().warning(message);
                }

                @Override
                public void log(Supplier<String> messageSupplier) {
                    env.getLogger().warning(messageSupplier);
                }

                @Override
                public void log(String message, Throwable throwable) {
                    env.getLogger().log(Level.SEVERE, message, throwable);
                }
            };

            @Override
            public JavaVersion getJavaVersion() {
                return env.getJavaVersion();
            }

            @Override
            public boolean isStrictJavaCompliance() {
                return env.getLanguage().getSpecComplianceMode() == EspressoOptions.SpecComplianceMode.STRICT;
            }

            @Override
            public TimerCollection getTimers() {
                return env.getTimers();
            }

            @Override
            public boolean isPreviewEnabled() {
                return env.isPreviewEnabled();
            }

            @Override
            public Logger getLogger() {
                return truffleEnvLogger;
            }

            @Override
            public Symbol<Name> getOrCreateName(ByteSequence byteSequence) {
                return env.getNames().getOrCreate(byteSequence, ensureStrongReferences);
            }

            @Override
            public Symbol<Type> getOrCreateTypeFromName(ByteSequence byteSequence) {
                return env.getTypes().getOrCreateValidType(TypeSymbols.nameToType(byteSequence), ensureStrongReferences);
            }

            @Override
            public Symbol<? extends ModifiedUTF8> getOrCreateUtf8(ByteSequence byteSequence) {
                return env.getLanguage().getUtf8Symbols().getOrCreateValidUtf8(byteSequence, ensureStrongReferences);
            }
        };
    }
}
