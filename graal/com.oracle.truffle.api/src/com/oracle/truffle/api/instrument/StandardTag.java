/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

/**
 * A somewhat language-agnostic set of phylum categories, suitable for conventional imperative
 * languages, and is being developed incrementally.
 * <p>
 * The need for alternative sets of tags is likely to arise, perhaps for other families of languages
 * (for example for mostly expression-oriented languages) or even for specific languages.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 *
 * @see Probe
 * @see Wrapper
 */
public enum StandardTag implements PhylumTag {

    /**
     * Marker for a variable assignment.
     */
    ASSIGNMENT("assignment", "a variable assignment"),

    /**
     * Marker for a call site.
     */
    CALL("call", "a method/procedure call site"),

    /**
     * Marker for a location where a guest language exception is about to be thrown.
     */
    THROW("throw", "creator of an exception"),

    /**
     * Marker for a location where ordinary "stepping" should halt.
     */
    STATEMENT("statement", "basic unit of the language, suitable for \"stepping\" in a debugger");

    private final String name;
    private final String description;

    private StandardTag(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}
