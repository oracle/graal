/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * A {@link BootClassRegistry} maps type names to resolved {@link Klass} instances loaded by the
 * boot class loader.
 */
public final class BootClassRegistry extends ClassRegistry {

    static final DebugCounter loadKlassCount = DebugCounter.create("BCL loadKlassCount");
    static final DebugCounter loadKlassCacheHits = DebugCounter.create("BCL loadKlassCacheHits");
    static final DebugCounter loadLinkedKlassCount = DebugCounter.create("BCL loadLinkedKlassCount");
    static final DebugCounter loadLinkedKlassCacheHits = DebugCounter.create("BCL loadLinkedKlassCacheHits");
    private static final DebugTimer BOOT_KLASS_READ = DebugTimer.create("boot klass read");

    private final Classpath bootClasspath;

    public BootClassRegistry(Classpath cp, long loaderID) {
        super(loaderID);
        bootClasspath = cp;
    }

    @Override
    protected void loadKlassCountInc() {
        loadKlassCount.inc();
    }

    @Override
    protected void loadKlassCacheHitsInc() {
        loadKlassCacheHits.inc();
    }

    @Override
    protected void loadLinkedKlassCountInc() {
        loadLinkedKlassCount.inc();
    }

    @Override
    protected void loadLinkedKlassCacheHitsInc() {
        loadLinkedKlassCacheHits.inc();
    }

    private final Map<String, String> packageMap = new ConcurrentHashMap<>();

    @Override
    public ParserKlass loadParserKlass(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) throws EspressoClassLoadingException.SecurityException {
        if (Types.isArray(type)) {
            throw EspressoError.shouldNotReachHere("Array type provided to loadParserKlass");
        }

        ParserKlass parserKlass;
        synchronized (type) {
            ClasspathFile classpathFile = getClasspathFile(env, bootClasspath, type);
            if (classpathFile == null) {
                return null;
            }
            parserKlass = parseKlass(env, classpathFile.contents, type, info);
        }

        return parserKlass;
    }

    @Override
    public LinkedKlass loadLinkedKlassImpl(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) throws EspressoClassLoadingException {
        ParserKlass parserKlass = loadParserKlass(env, type, info);
        return parserKlass != null ? createLinkedKlass(env, parserKlass, info) : null;
    }

    @Override
    public Klass loadKlassImpl(ClassLoadingEnv.InContext env, Symbol<Type> type) throws EspressoClassLoadingException {
        ClasspathFile classpathFile = getClasspathFile(env, bootClasspath, type);
        if (classpathFile == null) {
            return null;
        }

        // Defining a class also loads the superclass and the superinterfaces which excludes the
        // use of computeIfAbsent to insert the class since the map is modified.
        ObjectKlass result = defineKlass(env, type, classpathFile.contents);
        env.getRegistries().recordConstraint(type, result, getClassLoader());
        packageMap.put(result.getRuntimePackage().toString(), classpathFile.classpathEntry.path());
        return result;
    }

    @TruffleBoundary
    public String getPackagePath(String pkgName) {
        String result = packageMap.get(pkgName);
        return result;
    }

    @TruffleBoundary
    public String[] getPackages() {
        return packageMap.keySet().toArray(new String[0]);
    }

    @Override
    public @JavaType(ClassLoader.class) StaticObject getClassLoader() {
        return StaticObject.NULL;
    }

    @SuppressWarnings("try")
    private static ClasspathFile getClasspathFile(ClassLoadingEnv env, Classpath cp, Symbol<Type> type) {
        if (Types.isPrimitive(type)) {
            return null;
        }
        try (DebugCloseable scope = BOOT_KLASS_READ.scope(env.getTimers())) {
            ClasspathFile classpathFile = cp.readClassFile(type);
            return classpathFile;
        }
    }
}
