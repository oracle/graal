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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.TimerCollection;
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

    public boolean loaderIsBootOrPlatform(StaticObject loader) {
        return StaticObject.isNull(loader) ||
                        (language.getJavaVersion().java9OrLater() && meta.jdk_internal_loader_ClassLoaders$PlatformClassLoader.isAssignableFrom(loader.getKlass()));
    }

    public boolean loaderIsAppLoader(StaticObject loader) {
        return !StaticObject.isNull(loader) &&
                        (meta.jdk_internal_loader_ClassLoaders$AppClassLoader.isAssignableFrom(loader.getKlass()));
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
}
