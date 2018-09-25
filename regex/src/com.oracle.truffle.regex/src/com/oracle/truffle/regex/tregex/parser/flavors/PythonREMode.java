/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

/**
 * The enumeration of different flavors of Python regular expressions.
 * <p>
 * In Python, the standard regular expression engine behaves differently based on whether the
 * pattern is a 'str' (string) or a 'bytes' (immutable byte buffer) object. Since all regexes are
 * represented as {@link String}s in TRegex, we cannot dispatch on the type of the pattern. Instead,
 * we dispatch on values of this enumeration.
 */
public enum PythonREMode {
    /**
     * String-based patterns, where the Python regular expression was given as a 'str' object.
     */
    Str,
    /**
     * Bytes-based (binary) patterns, where the Python regular expression was given as a 'bytes'
     * object.
     */
    Bytes
}
