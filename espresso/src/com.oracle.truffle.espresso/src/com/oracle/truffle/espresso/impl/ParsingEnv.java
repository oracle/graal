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
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.runtime.JavaVersion;

// TODO (ivan-ristovic): Use an interface
public final class ParsingEnv {
    private final EspressoLanguage language;
    private TimerCollection timers;
    private Meta meta;
    private TruffleLogger logger;
    private JavaVersion javaVersion;
    private EspressoOptions.SpecCompliancyMode specCompliancyMode;
    private boolean verifyNeeded;
    private boolean extensionFieldUsed;

    // TODO (ivan-ristovic): Do proper construction
    public ParsingEnv(EspressoLanguage language) {
        this.language = language;
    }

    public EspressoLanguage getLanguage() {
        return language;
    }

    public Names getNames() {
        return language.getNames();
    }

    public Types getTypes() {
        return language.getTypes();
    }

    public TimerCollection getTimers() {
        return timers;
    }

    public void setTimers(TimerCollection timers) {
        this.timers = timers;
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

    public void setLogger(TruffleLogger logger) {
        this.logger = logger;
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(JavaVersion javaVersion) {
        this.javaVersion = javaVersion;
    }

    public EspressoOptions.SpecCompliancyMode getSpecCompliancyMode() {
        return specCompliancyMode;
    }

    public void setSpecCompliancyMode(EspressoOptions.SpecCompliancyMode specCompliancyMode) {
        this.specCompliancyMode = specCompliancyMode;
    }

    public boolean isVerifyNeeded() {
        return verifyNeeded;
    }

    public void setVerifyNeeded(boolean verifyNeeded) {
        this.verifyNeeded = verifyNeeded;
    }

    public boolean isExtensionFieldUsed() {
        return extensionFieldUsed;
    }

    public void setExtensionFieldUsed(boolean extensionFieldUsed) {
        this.extensionFieldUsed = extensionFieldUsed;
    }
}
