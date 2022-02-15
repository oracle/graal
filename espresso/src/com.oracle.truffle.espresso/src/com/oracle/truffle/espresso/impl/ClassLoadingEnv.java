/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.verifier.MethodVerifier;

public interface ClassLoadingEnv {

    EspressoLanguage getLanguage();
    Names getNames();
    Types getTypes();
    TimerCollection getTimers();
    TruffleLogger getLogger();
    JavaVersion getJavaVersion();
    EspressoOptions.SpecComplianceMode getSpecComplianceMode();
    Classpath getClasspath();
    boolean needsVerify(StaticObject loader);
    boolean isLoaderBootOrPlatform(StaticObject loader);
    int unboxInteger(StaticObject obj);
    float unboxFloat(StaticObject obj);
    long unboxLong(StaticObject obj);
    double unboxDouble(StaticObject obj);

    abstract class CommonEnv implements ClassLoadingEnv {
        private final EspressoLanguage language;

        public CommonEnv(EspressoLanguage lang) {
            language = lang;
        }

        @Override
        public EspressoLanguage getLanguage() {
            return language;
        }

        @Override
        public Names getNames() {
            return language.getNames();
        }

        @Override
        public Types getTypes() {
            return language.getTypes();
        }
    }

    class InContext extends CommonEnv {
        private final EspressoContext context;

        public InContext(EspressoContext ctx) {
            super(ctx.getLanguage());
            context = ctx;
        }

        public EspressoContext getContext() {
            return context;
        }

        public Meta getMeta() {
            return getContext().getMeta();
        }

        public ClassRegistries getRegistries() {
            return getContext().getRegistries();
        }

        @Override
        public TimerCollection getTimers() {
            return getContext().getTimers();
        }

        @Override
        public TruffleLogger getLogger() {
            return getContext().getLogger();
        }

        @Override
        public JavaVersion getJavaVersion() {
            return getContext().getJavaVersion();
        }

        @Override
        public EspressoOptions.SpecComplianceMode getSpecComplianceMode() {
            return getContext().getLanguage().getSpecComplianceMode();
        }

        @Override
        public Classpath getClasspath() {
            return getContext().getBootClasspath();
        }

        @Override
        public boolean needsVerify(StaticObject loader) {
            return MethodVerifier.needsVerify(getLanguage(), loader);
        }

        @Override
        public boolean isLoaderBootOrPlatform(StaticObject loader) {
            Meta meta = context.getMeta();
            return StaticObject.isNull(loader) ||
                    (meta.getJavaVersion().java9OrLater() && meta.jdk_internal_loader_ClassLoaders$PlatformClassLoader.isAssignableFrom(loader.getKlass()));
        }

        @Override
        public int unboxInteger(StaticObject obj) {
            return getMeta().unboxInteger(obj);
        }

        @Override
        public float unboxFloat(StaticObject obj) {
            return getMeta().unboxFloat(obj);
        }

        @Override
        public long unboxLong(StaticObject obj) {
            return getMeta().unboxLong(obj);
        }

        @Override
        public double unboxDouble(StaticObject obj) {
            return getMeta().unboxDouble(obj);
        }
    }

    class WithoutContext extends CommonEnv {

        public static class Options {
            private final TruffleLogger logger;
            private final JavaVersion javaVersion;
            private final Classpath classpath;
            private EspressoOptions.SpecComplianceMode specComplianceMode;
            private boolean needsVerify;

            public Options(JavaVersion version, Classpath cp) {
                this(version, cp, TruffleLogger.getLogger(EspressoLanguage.ID));
            }

            public Options(JavaVersion version, Classpath cp, TruffleLogger loggerOverride) {
                javaVersion = version;
                classpath = cp;
                logger = loggerOverride;
                specComplianceMode = EspressoOptions.SpecCompliance.getDefaultValue();
                EspressoOptions.VerifyMode defaultVerifyMode = EspressoOptions.Verify.getDefaultValue();
                needsVerify = defaultVerifyMode != EspressoOptions.VerifyMode.NONE;
            }

            public void enableVerify() {
                this.needsVerify = true;
            }

            public void complancyModeOverride(EspressoOptions.SpecComplianceMode specCompliancyMode) {
                this.specComplianceMode = specCompliancyMode;
            }
        }

        private final Options options;

        public WithoutContext(EspressoLanguage language, JavaVersion version, Classpath cp) {
            this(language, new Options(version, cp));
        }

        public WithoutContext(EspressoLanguage language, Options opts) {
            super(language);
            options = opts;
        }

        @Override
        public TimerCollection getTimers() {
            return TimerCollection.create(false);
        }

        @Override
        public TruffleLogger getLogger() {
            return options.logger;
        }

        @Override
        public JavaVersion getJavaVersion() {
            return options.javaVersion;
        }

        @Override
        public EspressoOptions.SpecComplianceMode getSpecComplianceMode() {
            return options.specComplianceMode;
        }

        @Override
        public Classpath getClasspath() {
            return options.classpath;
        }

        @Override
        public boolean needsVerify(StaticObject loader) {
            return options.needsVerify;
        }

        @Override
        public boolean isLoaderBootOrPlatform(StaticObject loader) {
            return StaticObject.isNull(loader);
        }

        @Override
        public int unboxInteger(StaticObject obj) {
            assert !StaticObject.isNull(obj);
            assert obj.getKlass().getType().equals(Symbol.Type.java_lang_Integer);
            try {
                return InteropLibrary.getUncached().asInt(obj);
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }

        @Override
        public float unboxFloat(StaticObject obj) {
            assert !StaticObject.isNull(obj);
            assert obj.getKlass().getType().equals(Symbol.Type.java_lang_Float);
            try {
                return InteropLibrary.getUncached().asFloat(obj);
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }

        @Override
        public long unboxLong(StaticObject obj) {
            assert !StaticObject.isNull(obj);
            assert obj.getKlass().getType().equals(Symbol.Type.java_lang_Long);
            try {
                return InteropLibrary.getUncached().asLong(obj);
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }

        @Override
        public double unboxDouble(StaticObject obj) {
            assert !StaticObject.isNull(obj);
            assert obj.getKlass().getType().equals(Symbol.Type.java_lang_Double);
            try {
                return InteropLibrary.getUncached().asDouble(obj);
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }
    }
}
