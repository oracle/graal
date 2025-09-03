/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
public final class Target_jdk_jfr_internal_JVM {
    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, "jdk.jfr.internal.JVM");

    @Substitution(languageFilter = VersionFilter.Java11OrLater.class)
    public static void registerNatives() {
        LOGGER.warning("Ignoring jdk.jfr.internal.JVM initialization, JFR is not supported in Espresso");
    }

    @Substitution(languageFilter = VersionFilter.Java11OrLater.class)
    @SuppressWarnings("unused")
    public static void subscribeLogLevel(@JavaType(internalName = "Ljdk/jfr/internal/LogTag;") StaticObject lt, int tagSetId) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java11To21.class)
    @SuppressWarnings("unused")
    public static void setFileNotification(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, long delta) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setFileNotification(long delta) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java11To21.class)
    @SuppressWarnings("unused")
    public static void setMemorySize(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, long size) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setMemorySize(long size) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java11To21.class)
    @SuppressWarnings("unused")
    public static void setGlobalBufferSize(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, long size) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setGlobalBufferSize(long size) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java11To21.class)
    @SuppressWarnings("unused")
    public static void setGlobalBufferCount(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, long count) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setGlobalBufferCount(long count) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java21.class)
    @SuppressWarnings("unused")
    public static void setDumpPath(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, @JavaType(String.class) StaticObject dumpPathText) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setDumpPath(@JavaType(String.class) StaticObject dumpPathText) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java11To21.class)
    @SuppressWarnings("unused")
    public static void setStackDepth(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, int depth) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setStackDepth(int depth) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java11To21.class)
    @SuppressWarnings("unused")
    public static void setThreadBufferSize(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self, long size) {
        // ignore this
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static void setThreadBufferSize(long size) {
        // ignore this
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java21.class)
    @SuppressWarnings("unused")
    public static boolean isContainerized(@JavaType(internalName = "Ljdk/jfr/internal/JVM;") StaticObject self) {
        return false;
    }

    @Substitution(languageFilter = VersionFilter.Java22OrLater.class)
    @SuppressWarnings("unused")
    public static boolean isContainerized() {
        return false;
    }
}
