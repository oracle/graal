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

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public class Target_jdk_internal_module_ModuleLoaderMap {

    public static final String HOTSWAP_MODULE_NAME = "espresso.hotswap";
    public static final String POLYGLOT_MODULE_NAME = "espresso.polyglot";

    @Substitution
    public static @Host(Set.class) StaticObject bootModules(
                    // Checkstyle: stop
                    @GuestCall(target = "jdk_internal_module_ModuleLoaderMap_bootModules", original = true) DirectCallNode original,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {
        // fetch original platform modules set
        @Host(Set.class)
        StaticObject originalResult = (StaticObject) original.call();
        // inject our platform modules if options are enabled
        Method add = ((ObjectKlass) originalResult.getKlass()).itableLookup(meta.java_util_Set, meta.java_util_Set_add.getITableIndex());
        if (meta.getContext().JDWPOptions != null) {
            add.invokeDirect(originalResult, meta.toGuestString(HOTSWAP_MODULE_NAME));
        }
        if (meta.getContext().Polyglot) {
            add.invokeDirect(originalResult, meta.toGuestString(POLYGLOT_MODULE_NAME));
        }
        return originalResult;
    }
}
