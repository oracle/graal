/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.EnumSet;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

@EspressoSubstitutions(nameProvider = Target_jdk_test_whitebox_WhiteBox.WhiteBoxNameProvider.class)
public final class Target_jdk_test_whitebox_WhiteBox {
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution(hasReceiver = true)
    public static boolean isJVMTIIncluded(@SuppressWarnings("unused") StaticObject self,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return false;
    }

    private static void checkWhiteBox(Meta meta, EspressoLanguage language) {
        if (!language.isWhiteBoxEnabled()) {
            throw meta.throwException(meta.java_lang_UnsatisfiedLinkError);
        }
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Boolean.class) StaticObject getBooleanVMFlag(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(String.class) StaticObject guestName,
                    @Inject Meta meta, @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkWhiteBox(meta, language);
        String name = meta.toHostString(guestName);
        return switch (name) {
            // ClassUnloading depends on the static object module implementation used
            case "VMContinuations" -> meta.boxBoolean(false);
            case "UseCompiler", "ProfileInterpreter" -> meta.boxBoolean(useCompiler());
            case "UseJVMCICompiler" -> meta.boxBoolean(useGraal());
            case "TieredCompilation", "FlightRecorder", "EnableJVMCI", "ClassUnloading", "ClassUnloadingWithConcurrentMark", "UseCompressedOops", "UseVectorizedMismatchIntrinsic",
                            "EliminateAllocations", "UseVtableBasedCHA" ->
                StaticObject.NULL;
            default -> {
                context.getLogger().warning(() -> "WhiteBox.getBooleanVMFlag(" + name + "): unknown flag");
                yield StaticObject.NULL;
            }
        };
    }

    @TruffleBoundary
    private static boolean useCompiler() {
        return !Truffle.getRuntime().getName().equals("Interpreted");
    }

    @TruffleBoundary
    private static boolean useGraal() {
        return Truffle.getRuntime().getName().contains("Graal");
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getStringVMFlag(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(String.class) StaticObject guestName,
                    @Inject Meta meta, @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkWhiteBox(meta, language);
        String name = meta.toHostString(guestName);
        return switch (name) {
            case "StartFlightRecording" -> StaticObject.NULL;
            default -> {
                context.getLogger().warning(() -> "WhiteBox.getStringVMFlag(" + name + "): unknown flag");
                yield StaticObject.NULL;
            }
        };
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Long.class) StaticObject getIntxVMFlag(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(String.class) StaticObject guestName,
                    @Inject Meta meta, @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkWhiteBox(meta, language);
        String name = meta.toHostString(guestName);
        return switch (name) {
            case "TieredStopAtLevel" -> StaticObject.NULL;
            default -> {
                context.getLogger().warning(() -> "WhiteBox.getIntxVMFlag(" + name + "): unknown flag");
                yield StaticObject.NULL;
            }
        };
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getLibcName(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return meta.toGuestString("unknown");
    }

    @Substitution(hasReceiver = true)
    public static boolean isJFRIncluded(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return false;
    }

    @Substitution(hasReceiver = true)
    public static boolean isDTraceIncluded(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return false;
    }

    @Substitution(hasReceiver = true)
    public static boolean isCDSIncluded(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return false;
    }

    @Substitution(hasReceiver = true)
    public static boolean isJVMCISupportedByGC(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return false;
    }

    @Substitution(hasReceiver = true)
    public static boolean canWriteJavaHeapArchive(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return false;
    }

    @Substitution(hasReceiver = true)
    public static int getVMPageSize(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        return UnsafeAccess.getIfAllowed(meta).pageSize();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getCPUFeatures(@SuppressWarnings("unused") StaticObject self,
                    @Inject Meta meta, @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkWhiteBox(meta, language);
        context.getLogger().warning(() -> "WhiteBox.getCPUFeatures(): returning an empty string");
        return meta.toGuestString("");
    }

    enum GC {
        None,
        Serial,
        Parallel,
        G1,
        Epsilon,
        Z,
        Shenandoah;

        static GC fromId(int id) {
            GC[] values = GC.values();
            if (id < 0 || id >= values.length) {
                return null;
            }
            return values[id];
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean isGCSupported(@SuppressWarnings("unused") StaticObject self,
                    int gcId,
                    @Inject Meta meta, @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkWhiteBox(meta, language);
        GC gc = GC.fromId(gcId);
        if (gc == null) {
            context.getLogger().warning(() -> "WhiteBox.isGCSupported(): unknown GC ID: " + gcId);
            return false;
        }
        // TODO
        return gc == GC.Serial;
    }

    @Substitution(hasReceiver = true)
    public static boolean isGCSelected(@SuppressWarnings("unused") StaticObject self,
                    int gcId,
                    @Inject Meta meta, @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkWhiteBox(meta, language);
        GC gc = GC.fromId(gcId);
        if (gc == null) {
            context.getLogger().warning(() -> "WhiteBox.isGCSelected(): unknown GC ID: " + gcId);
            return false;
        }
        return isGCSelected(gc, context);
    }

    @TruffleBoundary
    private static boolean isGCSelected(GC gc, EspressoContext context) {
        List<GarbageCollectorMXBean> gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
        EnumSet<GC> found = EnumSet.noneOf(GC.class);
        for (GarbageCollectorMXBean gcMxBean : gcMxBeans) {
            String name = gcMxBean.getName();
            if (name.startsWith("G1 ")) {
                found.add(GC.G1);
            } else if (name.startsWith("Epsilon")) {
                found.add(GC.Epsilon);
            } else if (name.startsWith("PS ")) {
                found.add(GC.Parallel);
            } else if (name.startsWith("ZGC ")) {
                found.add(GC.Z);
            } else if (name.startsWith("Shenandoah ")) {
                found.add(GC.Shenandoah);
            } else if (name.equals("Copy") || name.equals("MarkSweepCompact")) {
                // HotSpot Serial
                found.add(GC.Serial);
            } else if (name.equals("young generation scavenger") || name.equals("complete scavenger")) {
                // SVM Serial
                found.add(GC.Serial);
            } else {
                context.getLogger().warning("Unknown GC MX bean: " + name);
            }
        }
        if (found.size() != 1) {
            context.getLogger().warning("Cannot determine current GC: found " + found);
        }
        return found.contains(gc);
    }

    @Substitution(hasReceiver = true)
    public static boolean isGCSelectedErgonomically(@SuppressWarnings("unused") StaticObject self, @Inject Meta meta, @Inject EspressoLanguage language) {
        checkWhiteBox(meta, language);
        // TODO
        return true;
    }

    public static class WhiteBoxNameProvider extends SubstitutionNamesProvider {
        private static final String[] NAMES = {
                        "Target_jdk_test_whitebox_WhiteBox",
                        "Target_sun_hotspot_WhiteBox"
        };
        public static SubstitutionNamesProvider INSTANCE = new WhiteBoxNameProvider();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }
}
