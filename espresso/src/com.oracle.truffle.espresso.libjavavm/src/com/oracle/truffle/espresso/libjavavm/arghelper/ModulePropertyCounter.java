/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.libjavavm.arghelper;

import org.graalvm.polyglot.Context;

import com.oracle.truffle.espresso.libjavavm.Arguments;

/**
 * Handles numbered System properties, which may require keeping track of some counters.
 */
class ModulePropertyCounter {
    ModulePropertyCounter(Context.Builder builder) {
        this.builder = builder;
    }

    private static final String JDK_MODULES_PREFIX = "jdk.module.";

    private static final String ADD_MODULES = JDK_MODULES_PREFIX + "addmods";
    private static final String ADD_EXPORTS = JDK_MODULES_PREFIX + "addexports";
    private static final String ADD_OPENS = JDK_MODULES_PREFIX + "addopens";
    private static final String ADD_READS = JDK_MODULES_PREFIX + "addreads";
    private static final String ENABLE_MODULE_ACCESS = JDK_MODULES_PREFIX + "enable.native.access";

    private static final String MODULE_PATH = JDK_MODULES_PREFIX + "path";
    private static final String UPGRADE_PATH = JDK_MODULES_PREFIX + "upgrade.path";
    private static final String LIMIT_MODS = JDK_MODULES_PREFIX + "limitmods";

    private static final String[] KNOWN_OPTIONS = {
                    ADD_MODULES,
                    ADD_EXPORTS,
                    ADD_OPENS,
                    ADD_READS,
                    ENABLE_MODULE_ACCESS,
                    MODULE_PATH,
                    UPGRADE_PATH,
                    LIMIT_MODS,
    };

    private final Context.Builder builder;

    private int addModules = 0;
    private int addExports = 0;
    private int addOpens = 0;
    private int addReads = 0;
    private int enableModuleAccess = 0;

    void addModules(String value) {
        addNumbered(ADD_MODULES, value, addModules++);
    }

    void addExports(String value) {
        addNumbered(ADD_EXPORTS, value, addExports++);
    }

    void addOpens(String value) {
        addNumbered(ADD_OPENS, value, addOpens++);
    }

    void addReads(String value) {
        addNumbered(ADD_READS, value, addReads++);
    }

    void enableNativeAccess(String value) {
        addNumbered(ENABLE_MODULE_ACCESS, value, enableModuleAccess++);
    }

    void addNumbered(String prop, String value, int count) {
        String key = Arguments.JAVA_PROPS + prop + "." + count;
        builder.option(key, value);
    }

    boolean isModulesOption(String prop) {
        if (prop.startsWith(JDK_MODULES_PREFIX)) {
            for (String known : KNOWN_OPTIONS) {
                if (prop.equals(known)) {
                    return true;
                }
            }
        }
        return false;
    }
}
