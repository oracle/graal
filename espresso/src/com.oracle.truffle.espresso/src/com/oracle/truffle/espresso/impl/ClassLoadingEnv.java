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
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface ClassLoadingEnv {

    EspressoLanguage getLanguage();
    Names getNames();
    Types getTypes();
    TimerCollection getTimers();
    TruffleLogger getLogger();
    JavaVersion getJavaVersion();
    EspressoOptions.SpecCompliancyMode getSpecCompliancyMode();
    boolean needsVerify(StaticObject loader);
    boolean isLoaderBootOrPlatform(StaticObject loader);
    int unboxInteger(StaticObject obj);
    float unboxFloat(StaticObject obj);
    long unboxLong(StaticObject obj);
    double unboxDouble(StaticObject obj);
    RuntimeException generateClassCircularityError();
    RuntimeException generateIncompatibleClassChangeError(String msg);
    RuntimeException generateSecurityException(String msg);
    RuntimeException wrapIntoClassDefNotFoundError(EspressoException e);

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

        public Meta getMeta() {
            return context.getMeta();
        }

        public ClassRegistries getRegistries() {
            return context.getRegistries();
        }

        @Override
        public TimerCollection getTimers() {
            return context.getTimers();
        }

        @Override
        public TruffleLogger getLogger() {
            return context.getLogger();
        }

        @Override
        public JavaVersion getJavaVersion() {
            return context.getJavaVersion();
        }

        @Override
        public EspressoOptions.SpecCompliancyMode getSpecCompliancyMode() {
            return context.SpecCompliancyMode;
        }

        @Override
        public boolean needsVerify(StaticObject loader) {
            return context.needsVerify(loader);
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

        @Override
        public RuntimeException generateClassCircularityError() {
            throw getMeta().throwException(getMeta().java_lang_ClassCircularityError);
        }

        @Override
        public RuntimeException generateIncompatibleClassChangeError(String msg) {
            return getMeta().throwExceptionWithMessage(getMeta().java_lang_IncompatibleClassChangeError, msg);
        }

        @Override
        public RuntimeException generateSecurityException(String msg) {
            return getMeta().throwExceptionWithMessage(getMeta().java_lang_SecurityException, msg);
        }

        @Override
        public RuntimeException wrapIntoClassDefNotFoundError(EspressoException e) {
            Meta meta = getMeta();
            if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                // NoClassDefFoundError has no <init>(Throwable cause). Set cause manually.
                StaticObject ncdfe = Meta.initException(meta.java_lang_NoClassDefFoundError);
                meta.java_lang_Throwable_cause.set(ncdfe, e.getExceptionObject());
                throw meta.throwException(ncdfe);
            }
            return e;
        }
    }

    class WithoutContext extends CommonEnv {
        private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID);
        private final JavaVersion javaVersion;

        public WithoutContext(EspressoLanguage language, JavaVersion version) {
            super(language);
            javaVersion = version;
        }

        @Override
        public TimerCollection getTimers() {
            return TimerCollection.create(false);
        }

        @Override
        public TruffleLogger getLogger() {
            return logger;
        }

        @Override
        public JavaVersion getJavaVersion() {
            return javaVersion;
        }

        @Override
        public EspressoOptions.SpecCompliancyMode getSpecCompliancyMode() {
            return EspressoOptions.SpecCompliancy.getDefaultValue();
        }

        @Override
        public boolean needsVerify(StaticObject loader) {
            EspressoOptions.VerifyMode defaultVerifyMode = EspressoOptions.Verify.getDefaultValue();
            return defaultVerifyMode != EspressoOptions.VerifyMode.NONE;
        }

        @Override
        public boolean isLoaderBootOrPlatform(StaticObject loader) {
            return StaticObject.isNull(loader);
        }

        // TODO
        @Override
        public int unboxInteger(StaticObject obj) {
            return 0;
        }

        @Override
        public float unboxFloat(StaticObject obj) {
            return 0;
        }

        @Override
        public long unboxLong(StaticObject obj) {
            return 0;
        }

        @Override
        public double unboxDouble(StaticObject obj) {
            return 0;
        }

        @Override
        public RuntimeException generateClassCircularityError() {
            return EspressoError.shouldNotReachHere("Class circularity detected");
        }

        @Override
        public RuntimeException generateIncompatibleClassChangeError(String msg) {
            return null;    // TODO
        }

        @Override
        public RuntimeException generateSecurityException(String msg) {
            return EspressoError.shouldNotReachHere(msg);
        }

        @Override
        public RuntimeException wrapIntoClassDefNotFoundError(EspressoException e) {
            return e;
        }
    }
}
