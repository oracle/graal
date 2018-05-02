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
 * Constants related to individual commands. Options, default values, etc.
 */
public interface Commands {
    /**
     * Dry run - do not change anything on the disk.
     */
    String OPTION_DRY_RUN = "0";   // NOI18N

    /**
     * Replace existing components.
     */
    String OPTION_REPLACE_COMPONENTS = "r"; // NOI18N

    /**
     * Force - implies all replace + ignore options.
     */
    String OPTION_FORCE = "f"; // NOI18N

    /**
     * Replace different files.
     */
    String OPTION_REPLACE_DIFFERENT_FILES = "o"; // NOI18N

    /**
     * Do not terminate uninstall on failed file deletions.
     */
    String OPTION_IGNORE_FAILURES = "x"; // NOI18N

    /**
     * List files.
     */
    String OPTION_LIST_FILES = "l";

    /**
     * Display full paths in lists.
     */
    String OPTION_FULL_PATHS = "p";

    /**
     * Ignore open errors, but report.
     */
    String OPTION_IGNORE_OPEN_ERRORS = "r";

    /**
     * Hide download progress bar.
     */
    String OPTION_NO_DOWNLOAD_PROGRESS = "n";

    /**
     * Verifies JAR integrity.
     */
    String OPTION_VERIFY_JARS = "s";

    /**
     * Do not use tabular list.
     */
    String OPTION_SUPPRESS_TABLE = "t";

    /**
     * Verbose option, prints more messages.
     */
    String OPTION_VERBOSE = "v";   // NOI18N

    /**
     * Validate only.
     */
    String OPTION_VALIDATE = "y";

    /**
     * Full validation, may require download of components.
     */
    String OPTION_VALIDATE_DOWNLOAD = "Y";

    /**
     * Print error stack traces.
     */
    String OPTION_DEBUG = "e";     // NOI18N

    /**
     * Help.
     */
    String OPTION_HELP = "h";

    /**
     * Interpret parameters as remote component IDs.
     */
    String OPTION_CATALOG = "c";

    /**
     * Interpret parameters as remote component IDs.
     */
    String OPTION_FOREIGN_CATALOG = "C";

    /**
     * Interpret parameters as URLs.
     */
    String OPTION_URLS = "u";

    /**
     * When present on a command, will terminate option processing and all parameters will be passed
     * on as positionals.
     */
    String DO_NOT_PROCESS_OPTIONS = "*";
}
