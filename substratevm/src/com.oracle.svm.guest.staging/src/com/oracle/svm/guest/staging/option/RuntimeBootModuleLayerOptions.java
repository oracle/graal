/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.option;

/** Runtime module options and the system properties used to pass them to the boot layer. */
public final class RuntimeBootModuleLayerOptions {
    public static final String UPGRADE_MODULE_PATH_OPTION = "--upgrade-module-path";
    public static final String MODULE_PATH_OPTION = "--module-path";
    public static final String ADD_MODULES_OPTION = "--add-modules";
    public static final String ADD_READS_OPTION = "--add-reads";
    public static final String ADD_EXPORTS_OPTION = "--add-exports";
    public static final String ADD_OPENS_OPTION = "--add-opens";
    public static final String ENABLE_NATIVE_ACCESS_OPTION = "--enable-native-access";

    /// Used by `ModuleBootstrap#boot2` and `ModulePathValidator#scanAllModules`.
    public static final String UPGRADE_MODULE_PATH_PROPERTY = "jdk.module.upgrade.path";

    public static final String MODULE_PATH_PROPERTY = "jdk.module.path";

    /// Read by `jdk.internal.loader.ClassLoaders.<clinit>`.
    public static final String MAIN_MODULE_PROPERTY = "jdk.module.main";

    public static final String ADD_MODULES_PROPERTY_PREFIX = "jdk.module.addmods.";

    /// Used by `ModuleBootstrap#addExtraReads`.
    public static final String ADD_READS_PROPERTY_PREFIX = "jdk.module.addreads.";

    /// Used by `ModuleBootstrap#addExtraExportsAndOpens`.
    public static final String ADD_EXPORTS_PROPERTY_PREFIX = "jdk.module.addexports.";

    /// Used by `ModuleBootstrap#addExtraExportsAndOpens`.
    public static final String ADD_OPENS_PROPERTY_PREFIX = "jdk.module.addopens.";

    /// Used by `ModuleBootstrap#decodeEnableNativeAccess` and replayed by
    /// `ModuleBootstrap#addEnableNativeAccess`.
    public static final String ENABLE_NATIVE_ACCESS_PROPERTY_PREFIX = "jdk.module.enable.native.access.";

    private RuntimeBootModuleLayerOptions() {
    }
}
