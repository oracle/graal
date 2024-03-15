/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions(nameProvider = Target_jdk_internal_module_ModuleLoaderMap_Modules.Provider.class)
public final class Target_jdk_internal_module_ModuleLoaderMap_Modules {
    private static final String[] TARGET_NAME = {"Target_jdk_internal_module_ModuleLoaderMap$Modules"};

    @Substitution(methodName = "<clinit>")
    public abstract static class Clinit extends SubstitutionNode {

        public abstract void execute();

        @Specialization
        public static void clinit(
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jdk_internal_module_ModuleLoaderMap_Modules_clinit.getCallTargetNoSubstitution())") DirectCallNode original) {
            Meta meta = context.getMeta();
            assert meta.getJavaVersion().java17OrLater();
            original.call();

            ModuleExtension[] bootExtensions = ModuleExtension.getBootExtensions(context);
            if (bootExtensions.length > 0) {
                /*
                 * Spoof the statically stored boot module set.
                 */
                spoofModules(Name.bootModules, meta, bootExtensions);
            }
            ModuleExtension[] platformExtensions = ModuleExtension.getPlatformExtensions(context);
            if (platformExtensions.length > 0) {
                /*
                 * Spoof the statically stored platform module set.
                 */
                spoofModules(Name.platformModules, meta, platformExtensions);
            }
        }

        @TruffleBoundary
        private static void spoofModules(Symbol<Name> fieldName, Meta meta, ModuleExtension[] toAdd) {
            Field field = meta.jdk_internal_module_ModuleLoaderMap_Modules.lookupDeclaredField(fieldName, Type.java_util_Set);

            StaticObject staticStorage = meta.jdk_internal_module_ModuleLoaderMap_Modules.tryInitializeAndGetStatics();
            @JavaType(Set.class)
            StaticObject originalResult = field.getObject(staticStorage);

            ArrayList<String> moduleNames = new ArrayList<>(toAdd.length);
            for (ModuleExtension moduleExtension : toAdd) {
                moduleNames.add(moduleExtension.moduleName());
            }
            field.setObject(staticStorage, meta.extendedStringSet(originalResult, moduleNames));
        }
    }

    public static class Provider extends SubstitutionNamesProvider {
        public static final Provider INSTANCE = new Provider();

        @Override
        public String[] substitutionClassNames() {
            return TARGET_NAME;
        }
    }
}
