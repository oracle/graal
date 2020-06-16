/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CContext {

    /**
     * Specifies which directives are used with the annotated element.
     *
     * @since 19.0
     */
    Class<? extends Directives> value();

    /**
     * Describes a C context.
     *
     * @since 19.0
     */
    interface Directives {

        /**
         * This method is called immediately after the constructor, to check whether the context is
         * part of the configuration or not. If this method returns false, all elements registered
         * inside this context are ignored.
         *
         * @since 19.0
         */
        default boolean isInConfiguration() {
            return true;
        }

        /**
         * All header files used in this context. C allows two kinds of imports: header files can be
         * surrounded with &lt;...&gt;, or "...". One of them must be used for every element in the
         * returned list.
         *
         * @since 19.0
         */
        default List<String> getHeaderFiles() {
            return Collections.emptyList();
        }

        /**
         * Unparameterized macro-definitions. Each entry is in the form of <macro-name> or
         * <macro-name> <macro-value>.
         *
         * @since 19.0
         */
        default List<String> getMacroDefinitions() {
            return Collections.emptyList();
        }

        /**
         * Returns options to be passed to the C compiler when processing the directives. For
         * example, the option "-Ipath" can be used to add a path for the lookup of header files.
         *
         * @since 19.0
         */
        default List<String> getOptions() {
            return Collections.emptyList();
        }

        /**
         * Returns a collection of libraries. They are treated the same way as libraries added via
         * the {@link CLibrary} annotation.
         *
         * @since 19.0
         */
        default List<String> getLibraries() {
            return Collections.emptyList();
        }

        /**
         * Returns a list of library paths.
         *
         * @since 19.0
         */
        default List<String> getLibraryPaths() {
            return Collections.emptyList();
        }
    }
}
