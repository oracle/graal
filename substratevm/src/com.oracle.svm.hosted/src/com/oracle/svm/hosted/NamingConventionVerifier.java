/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.Locale;
import java.util.Set;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.util.AnnotationUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that runtime-reachable image elements do not accidentally come from hosted or HotSpot
 * implementation code.
 */
final class NamingConventionVerifier {
    /**
     * These are legitimate runtime elements that have hotspot in their name.
     */
    private static final Set<String> CHECK_NAMING_EXCEPTIONS = Set.of(
                    "java.awt.Cursor.DOT_HOTSPOT_SUFFIX",
                    "sun.lwawt.macosx.CCustomCursor.fHotspot",
                    "sun.lwawt.macosx.CCustomCursor.getHotSpot()",
                    "sun.awt.shell.Win32ShellFolder2.ATTRIB_GHOSTED");

    private NamingConventionVerifier() {
    }

    static void checkUniverse(BigBang bb, AnalysisUniverse universe) {
        if (!SubstrateOptions.VerifyNamingConventions.getValue()) {
            return;
        }
        for (AnalysisMethod method : universe.getMethods()) {
            if ((method.isInvoked() || method.isReachable()) && AnnotationUtil.getAnnotation(method, Fold.class) == null) {
                checkName(bb, method);
            }
        }
        for (AnalysisField field : universe.getFields()) {
            if (field.isAccessed()) {
                checkName(bb, field);
            }
        }
        for (AnalysisType type : universe.getTypes()) {
            if (type.isReachable()) {
                checkName(bb, type);
            }
        }
    }

    static void checkName(BigBang bb, AnalysisMethod method) {
        String format = method.format("%H.%n(%p)");
        checkName(bb, method, format);
    }

    static void checkName(BigBang bb, ResolvedJavaMethod method) {
        String format = method.format("%H.%n(%p)");
        checkName(bb, null, format);
    }

    static void checkName(BigBang bb, ResolvedJavaField field) {
        String format = field.format("%H.%n");
        checkName(bb, null, format);
    }

    static void checkName(BigBang bb, ResolvedJavaType type) {
        String format = type.toJavaName(true);
        checkName(bb, null, format);
    }

    private static void checkName(BigBang bb, AnalysisMethod method, String name) {
        String message = namingConventionsViolation(name);
        if (message != null) {
            report(bb, name, method, message);
        }
    }

    private static String namingConventionsViolation(String name) {
        /*
         * We do not want any parts of the native image generator in the generated image. Therefore,
         * no element whose name contains "hosted" must be seen as reachable by the static analysis.
         * The same holds for "host VM" elements, which come from the hosting VM, unless they are
         * JDK internal types.
         */
        String lcName = name.toLowerCase(Locale.ROOT);
        if (!CHECK_NAMING_EXCEPTIONS.contains(name)) {
            if (lcName.contains("hosted")) {
                return "Hosted element used at run time: " + name + namingConventionsErrorMessageSuffix("hosted");
            } else if (!lcName.startsWith("jdk.internal") && lcName.contains("hotspot")) {
                return "Element with HotSpot in its name used at run time: " + name + namingConventionsErrorMessageSuffix("HotSpot");
            }
        }
        return null;
    }

    private static String namingConventionsErrorMessageSuffix(String elementType) {
        return """

                        If this is a regular JDK value, and not a %s element that was accidentally included, you can add it to the NamingConventionVerifier.CHECK_NAMING_EXCEPTIONS
                        If this is a %s element that was accidentally included, find a way to exclude it from the image.""".formatted(elementType, elementType);
    }

    private static void report(BigBang bb, String key, AnalysisMethod method, String message) {
        if (bb != null) {
            bb.getUnsupportedFeatures().addMessage(key, method, message);
        } else {
            throw new UnsupportedFeatureException(message);
        }
    }
}
