/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.home.HomeFinder;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public class Target_jdk_internal_module_SystemModuleFinders {

    @TruffleBoundary
    @Substitution
    public static @Host(typeName = "Ljava/lang/module/ModuleFinder;") StaticObject of(
                    @Host(typeName = "Ljdk/internal/module/SystemModules;") StaticObject systemModules,
                    @GuestCall(original = true, target = "jdk_internal_module_SystemModuleFinders_of") DirectCallNode original,
                    @InjectMeta Meta meta) {
        // construct a ModuleFinder that can locate our Espresso-specific platform modules
        // and compose it with the resulting module finder from the original call
        StaticObject moduleFinder = (StaticObject) original.call(systemModules);
        if (meta.getContext().JDWPOptions != null) {
            StaticObject hotSwapPath = getEspressoModulePath(meta, "hotswap.jar");
            moduleFinder = extendModuleFinders(meta, moduleFinder, hotSwapPath);
        }
        if (meta.getContext().Polyglot) {
            StaticObject polyglotPath = getEspressoModulePath(meta, "polyglot.jar");
            moduleFinder = extendModuleFinders(meta, moduleFinder, polyglotPath);
        }
        return moduleFinder;
    }

    @TruffleBoundary
    @Substitution
    public static @Host(typeName = "Ljava/lang/module/ModuleFinder;") StaticObject ofSystem(
                    @GuestCall(original = true, target = "jdk_internal_module_SystemModuleFinders_ofSystem") DirectCallNode original,
                    @InjectMeta Meta meta) {
        // construct ModuleFinders that can locate our Espresso-specific platform modules
        // and compose it with the resulting module finder from the original call
        StaticObject moduleFinder = (StaticObject) original.call();
        if (meta.getContext().JDWPOptions != null) {
            StaticObject hotSwapPath = getEspressoModulePath(meta, "hotswap.jar");
            moduleFinder = extendModuleFinders(meta, moduleFinder, hotSwapPath);
        }
        if (meta.getContext().Polyglot) {
            StaticObject polyglotPath = getEspressoModulePath(meta, "polyglot.jar");
            moduleFinder = extendModuleFinders(meta, moduleFinder, polyglotPath);
        }

        return moduleFinder;
    }

    private static StaticObject extendModuleFinders(Meta meta, StaticObject moduleFinder, StaticObject hotSwapPath) {
        StaticObject pathArray = meta.java_nio_file_Path.allocateReferenceArray(1);
        StaticObject[] unwrapped = pathArray.unwrap();
        unwrapped[0] = hotSwapPath;
        // ModuleFinder extension = ModulePath.of(hotswapPath);
        // moduleFinder = ModuleFinder.compose(extension, moduleFinder);
        StaticObject extension = (StaticObject) meta.jdk_internal_module_ModulePath_of.invokeDirect(StaticObject.NULL, pathArray);
        StaticObject moduleFinderArray = meta.java_lang_module_ModuleFinder.allocateReferenceArray(2);
        unwrapped = moduleFinderArray.unwrap();
        unwrapped[0] = extension;
        unwrapped[1] = moduleFinder;
        moduleFinder = (StaticObject) meta.java_lang_module_ModuleFinder_compose.invokeDirect(StaticObject.NULL, moduleFinderArray);
        return moduleFinder;
    }

    private static StaticObject getEspressoModulePath(Meta meta, String jarName) {
        Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
        Path hotswapJar = espressoHome.resolve("lib").resolve(jarName);
        // Paths.get(new File("the path").toURI());
        StaticObject file = InterpreterToVM.newObject(meta.java_io_File, false);
        meta.java_io_File_init.invokeDirect(file, meta.toGuestString(hotswapJar.toFile().getAbsolutePath()));
        StaticObject uri = (StaticObject) meta.java_io_File_toURI.invokeDirect(file);
        return (StaticObject) meta.java_nio_file_Paths_get.invokeDirect(StaticObject.NULL, uri);
    }
}
