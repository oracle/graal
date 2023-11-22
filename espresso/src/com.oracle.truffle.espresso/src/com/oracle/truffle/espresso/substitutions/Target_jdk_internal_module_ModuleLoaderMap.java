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

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
final class Target_jdk_internal_module_ModuleLoaderMap {

    /**
     * For JDK >11, boot modules are injected at
     * {@link Target_jdk_internal_module_ModuleLoaderMap_Modules.Clinit}.
     */
    @Substitution(versionFilter = VersionFilter.Java11OrEarlier.class)
    abstract static class BootModules extends SubstitutionNode {

        abstract @JavaType(Set.class) StaticObject execute();

        @Specialization
        @JavaType(Set.class)
        StaticObject doDefault(
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jdk_internal_module_ModuleLoaderMap_bootModules.getCallTargetNoSubstitution())") DirectCallNode original) {
            Meta meta = context.getMeta();
            // fetch original platform modules set
            @JavaType(Set.class)
            StaticObject originalResult = (StaticObject) original.call();
            ModuleExtension[] extensions = ModuleExtension.get(context);
            if (extensions.length == 0) {
                return originalResult;
            }
            // inject our platform modules if options are enabled
            return addModules(meta, originalResult, extensions);
        }

        @TruffleBoundary
        private static StaticObject addModules(Meta meta, StaticObject originalResult, ModuleExtension[] extensions) {
            Method add = ((ObjectKlass) originalResult.getKlass()).itableLookup(meta.java_util_Set, meta.java_util_Set_add.getITableIndex());
            for (ModuleExtension me : extensions) {
                add.invokeDirect(originalResult, meta.toGuestString(me.moduleName()));
            }
            return originalResult;
        }
    }
}
