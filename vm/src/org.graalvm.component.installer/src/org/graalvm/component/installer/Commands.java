/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * Constants related to individual commands. Options, default values, ...
 */
public interface Commands {
    /**
     * Dry run - do not change anything on the disk.
     */
    String OPTION_DRY_RUN = "0";   // NOI18N
    String LONG_OPTION_DRY_RUN = "dry-run";

    /**
     * Replace existing components.
     */
    String OPTION_REPLACE_COMPONENTS = "r"; // NOI18N
    String LONG_OPTION_REPLACE_COMPONENTS = "replace"; // NOI18N

    /**
     * Force - implies all replace + ignore options.
     */
    String OPTION_FORCE = "f";  // NOI18N
    String LONG_OPTION_FORCE = "force"; // NOI18N

    /**
     * Interpret command line parameters as files.
     */
    String OPTION_FILES = "L"; // NOI18N
    String LONG_OPTION_FILES = "local-file"; // NOI18N

    @Deprecated String OPTION_FILES_OLD = "F"; // NOI18N
    @Deprecated String LONG_OPTION_FILES_OLD = "file"; // NOI18N

    /**
     * Replace different files.
     */
    String OPTION_REPLACE_DIFFERENT_FILES = "o"; // NOI18N
    String LONG_OPTION_REPLACE_DIFFERENT_FILES = "overwrite"; // NOI18N

    /**
     * Do not terminate uninstall on failed file deletions.
     */
    String OPTION_IGNORE_FAILURES = "x"; // NOI18N
    String LONG_OPTION_IGNORE_FAILURES = "ignore"; // NOI18N

    /**
     * List files.
     */
    String OPTION_LIST_FILES = "l";
    String LONG_OPTION_LIST_FILES = "list-files";

    /**
     * Display full paths in lists.
     */
    String OPTION_FULL_PATHS = "p";
    String LONG_OPTION_FULL_PATHS = "paths";

    /**
     * Ignore open errors, but report.
     */
    String OPTION_IGNORE_OPEN_ERRORS = "r";
    String LONG_OPTION_IGNORE_OPEN_ERRORS = "ignore-open";

    /**
     * Hide download progress bar.
     */
    String OPTION_NO_DOWNLOAD_PROGRESS = "n";
    String LONG_OPTION_NO_DOWNLOAD_PROGRESS = "no-progress";

    /**
     * Verifies JAR integrity.
     */
    String OPTION_NO_VERIFY_JARS = "s";
    String LONG_OPTION_NO_VERIFY_JARS = "no-verify-jars";

    /**
     * Do not use tabular list.
     */
    String OPTION_SUPPRESS_TABLE = "t";
    String LONG_OPTION_SUPPRESS_TABLE = "no-tables";

    /**
     * Verbose option, prints more messages.
     */
    String OPTION_VERBOSE = "v";   // NOI18N
    String LONG_OPTION_VERBOSE = "verbose";   // NOI18N

    /**
     * Validate only.
     */
    String OPTION_VALIDATE = "y";
    String LONG_OPTION_VALIDATE = "only-validate";

    /**
     * Full validation, may require download of components.
     */
    String OPTION_VALIDATE_DOWNLOAD = "Y";
    String LONG_OPTION_VALIDATE_DOWNLOAD = "validate-before";

    /**
     * Print error stack traces.
     */
    String OPTION_DEBUG = "e";     // NOI18N
    String LONG_OPTION_DEBUG = "debug";     // NOI18N

    /**
     * Help.
     */
    String OPTION_HELP = "h";
    String LONG_OPTION_HELP = "help";

    /**
     * Interpret parameters as remote component IDs.
     */
    String OPTION_CATALOG = "c";
    String LONG_OPTION_CATALOG = "catalog";

    /**
     * Interpret parameters as remote component IDs, uses user-defined catalog URL.
     */
    String OPTION_FOREIGN_CATALOG = "C";
    String LONG_OPTION_FOREIGN_CATALOG = "custom-catalog";

    /**
     * Interpret parameters as URLs.
     */
    String OPTION_URLS = "u";
    String LONG_OPTION_URLS = "url";

    /**
     * When present on a command, will terminate option processing and all parameters will be passed
     * on as positionals.
     */
    String DO_NOT_PROCESS_OPTIONS = "*";

    /**
     * Fails if a component which already exists is to be installed.
     */
    String OPTION_FAIL_EXISTING = "i"; // NOI18N
    String LONG_OPTION_FAIL_EXISTING = "fail-existing"; // NOI18N

    /**
     * Automatic YES to all questions.
     */
    String OPTION_AUTO_YES = "A";
    String LONG_OPTION_AUTO_YES = "auto-yes";

    /**
     * Abort on all prompts except YES/NO.
     */
    String OPTION_NON_INTERACTIVE = "N";
    String LONG_OPTION_NON_INTERACTIVE = "non-interactive";

    /**
     * Operate on all components, irrespective of version.
     */
    String OPTION_ALL = "a";
    String LONG_OPTION_ALL = "all-versions";

    /**
     * Ignores missing components on upgrade.
     */
    String OPTION_IGNORE_MISSING_COMPONENTS = "x"; // NOI18N
    String LONG_OPTION_IGNORE_MISSING_COMPONENTS = "ignore-missing"; // NOI18N

    String OPTION_VERSION = "V";
    String LONG_OPTION_VERSION = "use-version";

    /**
     * Uninstall other components depending on the uninstalled ones.
     */
    String OPTION_UNINSTALL_DEPENDENT = "D";
    String LONG_OPTION_UNINSTALL_DEPENDENT = "remove-deps";

    /**
     * Attempt to resolve dependencies against local directories.
     */
    String OPTION_LOCAL_DEPENDENCIES = "D";
    String LONG_OPTION_LOCAL_DEPENDENCIES = "local-deps";

    /**
     * Ignore component dependencies.
     */
    String OPTION_NO_DEPENDENCIES = "M";
    String LONG_OPTION_NO_DEPENDENCIES = "no-deps";

    /**
     * Print version and exit. Non-alnum option to indicate the short form is not defined.
     */
    String OPTION_PRINT_VERSION = "@";
    String LONG_OPTION_PRINT_VERSION = "version";

    /**
     * Show version and continue. Non-alnum option to indicate the short form is not defined.
     */
    String OPTION_SHOW_VERSION = "#";
    String LONG_OPTION_SHOW_VERSION = "show-version";

    /**
     * Will not fail, if at least one of the catalogs can be read.
     */
    String OPTION_IGNORE_CATALOG_ERRORS = "E";
    String LONG_OPTION_IGNORE_CATALOG_ERRORS = "no-catalog-errors";
}
