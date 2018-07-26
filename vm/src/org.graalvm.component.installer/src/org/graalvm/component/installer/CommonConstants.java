/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

/**
 * Constants which do not fit elsewhere.
 */
public class CommonConstants {
    /**
     * The installer's version. Printed as part of the help message.
     */
    public static final String INSTALLER_VERSION = "1.0.0"; // NOI18N

    public static final String CAP_GRAALVM_VERSION = "graalvm_version";
    public static final String CAP_OS_ARCH = "os_arch";
    public static final String CAP_OS_NAME = "os_name";

    /**
     * Replaceable token for the path to the graalvm installation. The token can be used in
     * messages.
     */
    public static final String TOKEN_GRAALVM_PATH = "graalvm_home"; // NOI18N

    /**
     * Relative path for the component storage.
     */
    public static final String PATH_COMPONENT_STORAGE = "jre/lib/installer/components"; // NOI18N
    // the trailing backspace is important !
    public static final String PATH_POLYGLOT_REGISTRY = "jre/lib/installer/components/polyglot/"; // NOI18N

    public static final String PATH_JRE_BIN = "jre/bin/"; // NOI18N

    /**
     * System property to specify catalog URL.
     */
    public static final String SYSPROP_CATALOG_URL = "org.graalvm.component.catalog"; // NOI18N

    /**
     * Env variable that controls catalog URL.
     */
    public static final String ENV_CATALOG_URL = "GRAALVM_CATALOG"; // NOI18N

    /**
     * Warns the user to rebuild the polyglot image and/or libraries.
     */
    public static final boolean WARN_REBUILD_IMAGES = true;

    /**
     * Component ID prefix for graalvm core components. The prefix will be stripped from the
     * display, if the component is not ambiguous.
     */
    public static final String GRAALVM_CORE_PREFIX = "org.graalvm."; // NOI18N

    /**
     * Key in <code>release</code> file with catalog URL.
     */
    public static final String RELEASE_CATALOG_KEY = "component_catalog"; // NOI18N
}
