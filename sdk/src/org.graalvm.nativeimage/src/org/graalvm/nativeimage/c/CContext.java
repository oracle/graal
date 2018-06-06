/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.c.function.CLibrary;

/**
 * Defines the context for most other annotations of the C interface: the C header files that need
 * to be imported, the C macros that need to be defined to properly configure these headers, and
 * additional flags that should be passed to the C compiler when analyzing the definitions.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CContext {

    /**
     * Specifies which directives are used with the annotated element.
     *
     * @since 1.0
     */
    Class<? extends Directives> value();

    /**
     * Describes a C context.
     *
     * @since 1.0
     */
    interface Directives {

        /**
         * This method is called immediately after the constructor, to check whether the context is
         * part of the configuration or not. If this method returns false, all elements registered
         * inside this context are ignored.
         *
         * @since 1.0
         */
        default boolean isInConfiguration() {
            return true;
        }

        /**
         * All header files used in this context. C allows two kinds of imports: header files can be
         * surrounded with &lt;...&gt;, or "...". One of them must be used for every element in the
         * returned list.
         *
         * @since 1.0
         */
        default List<String> getHeaderFiles() {
            return Collections.emptyList();
        }

        /**
         * Unparameterized macro-definitions. Each entry is in the form of <macro-name> or
         * <macro-name> <macro-value>.
         *
         * @since 1.0
         */
        default List<String> getMacroDefinitions() {
            return Collections.emptyList();
        }

        /**
         * Returns options to be passed to the C compiler when processing the directives. For
         * example, the option "-Ipath" can be used to add a path for the lookup of header files.
         *
         * @since 1.0
         */
        default List<String> getOptions() {
            return Collections.emptyList();
        }

        /**
         * Returns a collection of libraries. They are treated the same way as libraries added via
         * the {@link CLibrary} annotation.
         *
         * @since 1.0
         */
        default List<String> getLibraries() {
            return Collections.emptyList();
        }

        /**
         * Returns a list of library paths.
         *
         * @since 1.0
         */
        default List<String> getLibraryPaths() {
            return Collections.emptyList();
        }
    }
}
