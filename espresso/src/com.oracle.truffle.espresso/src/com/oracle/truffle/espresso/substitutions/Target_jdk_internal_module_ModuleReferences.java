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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public class Target_jdk_internal_module_ModuleReferences {

    @TruffleBoundary
    @Substitution
    public static @Host(typeName = "Ljava/lang/module/ModuleReference;") StaticObject newJarModule(
                    @Host(typeName = "Ljdk/internal/module/ModuleInfo$Attributes;") StaticObject attrs,
                    @Host(typeName = "Ljdk/internal/module/ModulePatcher;") StaticObject patcher,
                    @Host(Path.class) StaticObject path,
                    // Checkstyle: stop
                    @GuestCall(target = "jdk_internal_module_ModuleReferences_newJarModule", original = true) DirectCallNode original,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {
        // check if one of our injected boot modules and patch location if so
        String hostName = getModuleName(attrs, meta);
        if (Target_jdk_internal_module_ModuleLoaderMap.HOTSWAP_MODULE_NAME.equals(hostName)) {
            return (StaticObject) original.call(attrs, patcher, getPatchedPath(meta, "hotswap.jar"));
        } else if (Target_jdk_internal_module_ModuleLoaderMap.POLYGLOT_MODULE_NAME.equals(hostName)) {
            return (StaticObject) original.call(attrs, patcher, getPatchedPath(meta, "polyglot.jar"));
        }
        return (StaticObject) original.call(attrs, patcher, path);
    }

    @TruffleBoundary
    @Substitution
    public static @Host(typeName = "Ljava/lang/module/ModuleReference;") StaticObject newExplodedModule(
                    @Host(typeName = "Ljdk/internal/module/ModuleInfo$Attributes;") StaticObject attrs,
                    @Host(typeName = "Ljdk/internal/module/ModulePatcher;") StaticObject patcher,
                    @Host(Path.class) StaticObject path,
                    // Checkstyle: stop
                    @GuestCall(target = "jdk_internal_module_ModuleReferences_newExplodedModule", original = true) DirectCallNode original,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {

        // check if one of our injected boot modules and patch location if so
        String hostName = getModuleName(attrs, meta);
        if (Target_jdk_internal_module_ModuleLoaderMap.HOTSWAP_MODULE_NAME.equals(hostName)) {
            return (StaticObject) original.call(attrs, patcher, getPatchedPath(meta, "hotswap.jar"));
        } else if (Target_jdk_internal_module_ModuleLoaderMap.POLYGLOT_MODULE_NAME.equals(hostName)) {
            return (StaticObject) original.call(attrs, patcher, getPatchedPath(meta, "polyglot.jar"));
        }
        return (StaticObject) original.call(attrs, patcher, path);
    }

    @TruffleBoundary
    @Substitution
    public static @Host(typeName = "Ljava/lang/module/ModuleReference;") StaticObject newJModModule(
                    @Host(typeName = "Ljdk/internal/module/ModuleInfo$Attributes;") StaticObject attrs,
                    @Host(Path.class) StaticObject path,
                    // Checkstyle: stop
                    @GuestCall(target = "jdk_internal_module_ModuleReferences_newJModModule", original = true) DirectCallNode original,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {

        // check if one of our injected boot modules and patch location if so
        String hostName = getModuleName(attrs, meta);
        if (Target_jdk_internal_module_ModuleLoaderMap.HOTSWAP_MODULE_NAME.equals(hostName)) {
            return (StaticObject) original.call(attrs, getPatchedPath(meta, "hotswap.jar"));
        } else if (Target_jdk_internal_module_ModuleLoaderMap.POLYGLOT_MODULE_NAME.equals(hostName)) {
            return (StaticObject) original.call(attrs, getPatchedPath(meta, "polyglot.jar"));
        }
        return (StaticObject) original.call(attrs, path);
    }

    private static String getModuleName(@Host(typeName = "Ljdk/internal/module/ModuleInfo$Attributes;") StaticObject attrs, @InjectMeta Meta meta) {
        StaticObject moduleDescriptor = (StaticObject) attrs.getKlass().lookupDeclaredMethod(Symbol.Name.descriptor, Symbol.Signature.ModuleDescriptor).invokeDirect(attrs);
        StaticObject guestModuleName = (StaticObject) moduleDescriptor.getKlass().lookupDeclaredMethod(Symbol.Name.name, Symbol.Signature.String).invokeDirect(moduleDescriptor);
        return meta.toHostString(guestModuleName);
    }

    private static StaticObject getPatchedPath(@InjectMeta Meta meta, String jarName) {
        Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
        Path hotswapJar = espressoHome.resolve("lib").resolve(jarName);
        // Paths.get(new File("the path").toURI());
        StaticObject file = InterpreterToVM.newObject(meta.java_io_File, false);
        meta.java_io_File_init.invokeDirect(file, meta.toGuestString(hotswapJar.toFile().getAbsolutePath()));
        StaticObject uri = (StaticObject) meta.java_io_File_toURI.invokeDirect(file);
        return (StaticObject) meta.java_nio_file_Paths_get.invokeDirect(StaticObject.NULL, uri);
    }
}
