/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
final class Target_jdk_internal_module_SystemModuleFinders {

    @Substitution
    abstract static class Of extends SubstitutionNode {

        abstract @JavaType(internalName = "Ljava/lang/module/ModuleFinder;") StaticObject execute(
                        @JavaType(internalName = "Ljdk/internal/module/SystemModules;") StaticObject systemModules);

        @Specialization
        @JavaType(internalName = "Ljava/lang/module/ModuleFinder;")
        StaticObject doDefault(
                        @JavaType(internalName = "Ljdk/internal/module/SystemModules;") StaticObject systemModules,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.jdk_internal_module_SystemModuleFinders_of.getCallTargetNoSubstitution())") DirectCallNode original) {
            // construct a ModuleFinder that can locate our Espresso-specific platform modules
            // and compose it with the resulting module finder from the original call
            StaticObject moduleFinder = (StaticObject) original.call(systemModules);
            StaticObject extensionPathArray = getEspressoExtensionPaths(getContext());
            if (extensionPathArray != StaticObject.NULL) {
                moduleFinder = extendModuleFinders(getLanguage(), meta, moduleFinder, extensionPathArray);
            }
            return moduleFinder;
        }
    }

    @Substitution
    abstract static class OfSystem extends SubstitutionNode {

        abstract @JavaType(internalName = "Ljava/lang/module/ModuleFinder;") StaticObject execute();

        @Specialization
        @JavaType(internalName = "Ljava/lang/module/ModuleFinder;")
        StaticObject doDefault(
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.jdk_internal_module_SystemModuleFinders_ofSystem.getCallTargetNoSubstitution())") DirectCallNode original) {
            // construct ModuleFinders that can locate our Espresso-specific platform modules
            // and compose it with the resulting module finder from the original call
            StaticObject moduleFinder = (StaticObject) original.call();
            StaticObject extensionPathArray = getEspressoExtensionPaths(getContext());
            if (extensionPathArray != StaticObject.NULL) {
                moduleFinder = extendModuleFinders(getLanguage(), meta, moduleFinder, extensionPathArray);
            }
            return moduleFinder;
        }
    }

    @TruffleBoundary
    private static StaticObject getEspressoExtensionPaths(EspressoContext context) {
        ArrayList<StaticObject> extensionPaths = new ArrayList<>(2);
        for (ModuleExtension me : ModuleExtension.getAllExtensions(context)) {
            extensionPaths.add(getEspressoModulePath(context, me.jarName()));
        }
        if (extensionPaths.isEmpty()) {
            return StaticObject.NULL;
        } else {
            return context.getMeta().java_nio_file_Path.allocateReferenceArray(extensionPaths.size(), extensionPaths::get);
        }
    }

    @TruffleBoundary
    private static StaticObject extendModuleFinders(EspressoLanguage language, Meta meta, StaticObject moduleFinder, StaticObject pathArray) {
        // ModuleFinder extension = ModulePath.of(pathArray);
        // moduleFinder = ModuleFinder.compose(extension, moduleFinder);
        StaticObject extension = (StaticObject) meta.jdk_internal_module_ModulePath_of.invokeDirect(StaticObject.NULL, pathArray);
        StaticObject moduleFinderArray = meta.java_lang_module_ModuleFinder.allocateReferenceArray(2);
        StaticObject[] unwrapped = moduleFinderArray.unwrap(language);
        unwrapped[0] = extension;
        unwrapped[1] = moduleFinder;
        return (StaticObject) meta.java_lang_module_ModuleFinder_compose.invokeDirect(StaticObject.NULL, moduleFinderArray);
    }

    @TruffleBoundary
    private static StaticObject getEspressoModulePath(EspressoContext context, String jarName) {
        Path jar = context.getEspressoLibs().resolve(jarName);
        Meta meta = context.getMeta();
        // Paths.get(guestPath);
        StaticObject guestPath = meta.toGuestString(jar.toFile().getAbsolutePath());
        StaticObject emptyArray = meta.java_nio_file_Path.allocateReferenceArray(0);
        return (StaticObject) meta.java_nio_file_Paths_get.invokeDirect(StaticObject.NULL, guestPath, emptyArray);
    }

}
