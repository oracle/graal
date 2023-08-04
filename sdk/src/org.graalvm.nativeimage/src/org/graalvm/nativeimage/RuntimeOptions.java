/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.util.List;

import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;

/**
 * Used for manipulating options at run time.
 *
 * @since 19.0
 */
public final class RuntimeOptions {

    private RuntimeOptions() {
    }

    /**
     * Set the value of the option with the provided name to the new value.
     *
     * @since 19.0
     */
    public static void set(String optionName, Object value) {
        ImageSingletons.lookup(RuntimeOptionsSupport.class).set(optionName, value);
    }

    /**
     * Get the value of the option with the provided name.
     *
     * @since 19.0
     */
    public static <T> T get(String optionName) {
        return ImageSingletons.lookup(RuntimeOptionsSupport.class).get(optionName);
    }

    /**
     * Lists all runtime option descriptors available.
     *
     * @since 23.1
     */
    public static List<Descriptor> listDescriptors() {
        return ImageSingletons.lookup(RuntimeOptionsSupport.class).listDescriptors();
    }

    /**
     * Looks up a single descriptor given an option name. Returns <code>null</code> if no descriptor
     * could be found.
     *
     * @since 23.1
     */
    public static Descriptor getDescriptor(String optionName) {
        return ImageSingletons.lookup(RuntimeOptionsSupport.class).getDescriptor(optionName);
    }

    public interface Descriptor {
        /**
         * Returns the name of the option that this descriptor represents.
         *
         * @since 23.1
         */
        String name();

        /**
         * Returns a human-readable description on how to use the option. For newlines, use
         * <code>%n</code>.
         *
         * @since 23.1
         */
        String help();

        /**
         * Returns <code>true</code> if this option was marked deprecated. This indicates that the
         * option is going to be removed in a future release or its use is not recommended.
         *
         * @since 23.1
         */
        boolean deprecated();

        /**
         * Returns the deprecation reason and the recommended fix. For newlines, use
         * <code>%n</code>.
         *
         * @since 23.1
         */
        String deprecatedMessage();

        /**
         * Returns the option type of this key. Typical values are {@link String}, {@link Boolean},
         * {@link Integer}. The result of {@link #convertValue(String)} is guaranteed to be
         * assignable to this type.
         *
         * @since 23.1
         */
        Class<?> valueType();

        /**
         * Returns the default value of type {@link #valueType()} for this option.
         *
         * @since 23.1
         */
        Object defaultValue();

        /**
         * Converts a string value, validates it, and converts it to an object of this type. For
         * option maps includes the previous map stored for the option and the key.
         *
         * @throws IllegalArgumentException if the value is invalid or cannot be converted.
         * @since 23.1
         */
        Object convertValue(String value) throws IllegalArgumentException;
    }

}
