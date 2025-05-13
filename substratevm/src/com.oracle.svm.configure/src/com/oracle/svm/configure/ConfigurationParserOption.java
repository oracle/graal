/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

/**
 * {@link ConfigurationParser} options that control parsing behaviour. Options are not necessarily
 * supported by all parsers.
 */
public enum ConfigurationParserOption {
    /**
     * Fail when a configuration file has incorrect schema (e.g., extraneous fields).
     */
    STRICT_CONFIGURATION,

    /**
     * Log a warning for each reflection element (e.g., class, field) that could not be resolved.
     */
    PRINT_MISSING_ELEMENTS,

    /**
     * Treat the legacy "typeReachable" condition as a "typeReached" condition checked at run time.
     */
    TREAT_ALL_TYPE_REACHABLE_CONDITIONS_AS_TYPE_REACHED,

    /**
     * Treat the "name" entry in a legacy reflection configuration as a "type" entry.
     */
    TREAT_ALL_NAME_ENTRIES_AS_TYPE,

    /**
     * Parse the given type configuration file as a JNI configuration.
     */
    JNI_PARSER
}
