/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
    public static final String INSTALLER_VERSION = "2.0.0"; // NOI18N

    public static final String CAP_GRAALVM_VERSION = "graalvm_version";
    public static final String CAP_OS_ARCH = "os_arch";
    public static final String CAP_OS_NAME = "os_name";
    public static final String CAP_EDITION = "edition";
    public static final String CAP_JAVA_VERSION = "java_version";

    public static final String EDITION_CE = "ce";

    /**
     * Replaceable token for the path to the graalvm installation. The token can be used in
     * messages.
     */
    public static final String TOKEN_GRAALVM_PATH = "graalvm_home"; // NOI18N

    /**
     * Path to the runtime lib. Replaceable token for installation messages.
     */
    public static final String TOKEN_GRAALVM_RTLIB_DIR = "graalvm_rtlib_dir"; // NOI18N

    /**
     * Path to arch-dependent runtime lib. Replaceable token for installation messages.
     */
    public static final String TOKEN_GRAALVM_RTLIB_ARCH_DIR = "graalvm_rtlib_arch_dir"; // NOI18N

    /**
     * Path to languages home. Replaceable token for installation messages.
     */
    public static final String TOKEN_GRAALVM_LANG_DIR = "graalvm_languages_dir"; // NOI18N

    /**
     * Relative path for the component storage.
     */
    public static final String PATH_COMPONENT_STORAGE = "lib/installer/components"; // NOI18N

    public static final String PATH_JRE_BIN = "bin/"; // NOI18N

    public static final String PATH_USER_GU = ".gu"; // NOI18N

    public static final String PATH_GDS_CONFIG = "config"; // NOI18N

    /**
     * System property to specify catalog URL.
     */
    public static final String SYSPROP_CATALOG_URL = "org.graalvm.component.catalog"; // NOI18N

    public static final String SYSPROP_JAVA_VERSION = "java.specification.version"; // NOI18N

    public static final String ENV_VARIABLE_PREFIX = "GRAALVM_"; // NOI18N

    /**
     * Env variable that points to GraalVM home used for debug and development purposes.
     */
    public static final String ENV_GRAALVM_HOME_DEV = "GRAALVM_HOME_DEV"; // NOI18N

    /**
     * Env variable that controls catalog URL.
     */
    public static final String ENV_CATALOG_URL = ENV_VARIABLE_PREFIX + "CATALOG"; // NOI18N

    /**
     * Prefix for env variables that define catalog list.
     */
    public static final String ENV_CATALOG_PREFIX = ENV_VARIABLE_PREFIX + "CATALOG_"; // NOI18N
    /**
     * Prefix for env variables that define catalog list.
     */
    public static final String CAP_CATALOG_PREFIX = "component_catalog_"; // NOI18N

    public static final String CAP_CATALOG_EDITION = "edition"; // NOI18N
    public static final String CAP_CATALOG_EDITION_NAME = "editionLabel"; // NOI18N

    public static final String CAP_CATALOG_URL = "url"; // NOI18N
    public static final String CAP_CATALOG_LABEL = "label"; // NOI18N

    /**
     * Component ID prefix for graalvm core components. The prefix will be stripped from the
     * display, if the component is not ambiguous.
     */
    public static final String GRAALVM_CORE_PREFIX = "org.graalvm"; // NOI18N

    /**
     * Short ID of the GraalVM core component.
     */
    public static final String GRAALVM_CORE_SHORT_ID = "graalvm"; // NOI18N

    /**
     * Key in <code>release</code> file with catalog URL.
     */
    public static final String RELEASE_CATALOG_KEY = "component_catalog"; // NOI18N

    /**
     * Key in <code>release</code> file with GDS Product ID.
     */
    public static final String RELEASE_GDS_PRODUCT_ID_KEY = "gds_product_id"; // NOI18N

    /**
     * Default installation dir encoded in RPM packages. The installer will strip this prefix to
     * relocate the package contents.
     */
    public static final String BUILTIN_INSTALLATION_DIR = "/usr/lib/graalvm"; // NOI18N

    /**
     * Origin of the component. An URL. Used only in directory-based registry of installed
     * components.
     */
    public static final String BUNDLE_ORIGIN_URL = "x-GraalVM-Component-Origin"; // NOI18N

    /**
     * ID of the native-image component.
     */
    public static final String NATIVE_IMAGE_ID = "native-image";

    public static final String ENV_DELETE_LIST = "GU_POST_DELETE_LIST"; // NOI18N
    public static final String ENV_COPY_CONTENTS = "GU_POST_COPY_CONTENTS"; // NOI18N

    public static final String ARCH_X8664 = "x86_64"; // NOI18N
    public static final String ARCH_AARCH64 = "aarch64"; // NOI18N
    public static final String ARCH_AMD64 = "amd64"; // NOI18N

    public static final String OS_MACOS_DARWIN = "darwin"; // NOI18N
    public static final String OS_TOKEN_MAC = "mac"; // NOI18N
    public static final String OS_TOKEN_MACOS = OS_TOKEN_MAC + "os"; // NOI18N
    public static final String OS_TOKEN_LINUX = "linux"; // NOI18N
    public static final String OS_TOKEN_WINDOWS = "windows"; // NOI18N

    /**
     * Return code which will cause the wrapper to retry operations on locked files.
     */
    public static final int WINDOWS_RETCODE_DELAYED_OPERATION = 11;

    /**
     * Hacky way how to reach out to all classes. Used to switch the output to a script-readable
     * format.
     */
    public static final String SYSPROP_SIMPLE_OUTPUT = "org.graalvm.component.installer.SimpleOutput";

    public static final String SYSPROP_OS_NAME = "os.name"; // NOI18N
    public static final String SYSPROP_ARCH_NAME = "os.arch"; // NOI18N
    public static final String SYSPROP_USER_HOME = "user.home"; // NOI18N

    public static final String JSON_KEY_COMPONENTS = "components"; // NOI18N
    public static final String JSON_KEY_COMPONENT_ID = "id"; // NOI18N
    public static final String JSON_KEY_COMPONENT_VERSION = "version"; // NOI18N
    public static final String JSON_KEY_COMPONENT_NAME = "name"; // NOI18N
    public static final String JSON_KEY_COMPONENT_FILENAME = "filename"; // NOI18N
    public static final String JSON_KEY_COMPONENT_GRAALVM = "graalvm"; // NOI18N
    public static final String JSON_KEY_COMPONENT_STABILITY = "stability"; // NOI18N
    public static final String JSON_KEY_COMPONENT_ORIGIN = "origin"; // NOI18N
    public static final String JSON_KEY_COMPONENT_FILES = "files"; // NOI18N
    public static final String JSON_KEY_COMPONENT_REQUIRES = "requires"; // NOI18N
    public static final String JSON_KEY_COMPONENT_ERRORS = "errors"; // NOI18N
    public static final String JSON_KEY_COMPONENT_PROBLEMS = "problems"; // NOI18N
}
