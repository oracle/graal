/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Objects;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;

/**
 * Substitution class for {@link java.lang.Module}. We need to substitute native methods
 * particularly, because original methods in the JDK contain VM state updates and perform additional
 * bookkeeping. We implement all the data structures we need to answer module system queries in Java
 * (see {@link ModuleNative}. In order to preserve JCK compatibility, we need to perform all the
 * checks performed by original methods and throw the exact same exception types and messages.
 */
@SuppressWarnings("unused")
@TargetClass(value = java.lang.Module.class)
public final class Target_java_lang_Module {

    /**
     * {@link Alias} to make {@code Module.layer} non-final. The actual run-time value is set via
     * reflection in {@code ModuleLayerFeatureUtils#patchModuleLayerField}, which is called after
     * analysis. Thus, we cannot leave it {@code final}, because the analysis might otherwise
     * constant-fold the initial {@code null} value. Ideally, we would make it {@code @Stable}, but
     * our substitution system currently does not allow this (GR-60154).
     */
    @Alias //
    @RecomputeFieldValue(isFinal = false, kind = RecomputeFieldValue.Kind.None)
    // @Stable (no effect currently GR-60154)
    private ModuleLayer layer;

    /**
     * Creating an {@link Alias} directly for {@code ALL_UNNAMED_MODULE} and {@code EVERYONE_MODULE}
     * makes {@code java.util.regex.Pattern} reachable, which increases the size of the binary.
     */
    // Checkstyle: stop
    @Alias //
    private static Set<Module> ALL_UNNAMED_MODULE_SET;
    @Alias //
    private static Set<Module> EVERYONE_SET;
    // Checkstyle: resume

    @Substitute
    @TargetElement(onlyWith = ForeignDisabled.class)
    @SuppressWarnings("static-method")
    public boolean isNativeAccessEnabled() {
        throw ForeignDisabledSubstitutions.fail();
    }

    @Alias
    public native void ensureNativeAccess(Class<?> owner, String methodName, Class<?> currentClass, boolean jni);

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/classfile/modules.cpp#L279-L474")
    private static void defineModule0(Module module, boolean isOpen, String version, String location, Object[] pns) {
        ModuleNative.defineModule(module, isOpen, pns);
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/share/classfile/modules.cpp#L763-L799")
    private static void addReads0(Module from, Module to) {
        ModuleNative.addReads(from, to);
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/share/classfile/modules.cpp#L753-L761")
    private static void addExports0(Module from, String pn, Module to) {
        ModuleNative.addExports(from, pn, to);
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/share/classfile/modules.cpp#L686-L750")
    private static void addExportsToAll0(Module from, String pn) {
        ModuleNative.addExportsToAll(from, pn);
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+10/src/hotspot/share/classfile/modules.cpp#L869-L918")
    private static void addExportsToAllUnnamed0(Module from, String pn) {
        ModuleNative.addExportsToAllUnnamed(from, pn);
    }

    @Substitute
    @SuppressWarnings("static-method")
    private boolean allows(Set<Module> targets, Module module) {
        if (targets != null) {
            Module everyoneModule = EVERYONE_SET.stream().findFirst().get();
            if (targets.contains(everyoneModule)) {
                return true;
            }
            if (module != everyoneModule) {
                if (targets.contains(module)) {
                    return true;
                }
                if (!module.isNamed() && targets.contains(ALL_UNNAMED_MODULE_SET.stream().findFirst().get())) {
                    return true;
                }
                if (ImageLayerBuildingSupport.buildingImageLayer()) {
                    for (var m : targets) {
                        /*
                         * This is based on the assumption that in Layered Image, all modules have
                         * different names. This is ensured in LayeredModuleSingleton.setPackages.
                         */
                        if (Objects.equals(m.getName(), module.getName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
