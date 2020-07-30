/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.options;

/**
 * Represents a set of option values based on an {@link OptionDescriptor}.
 *
 * @since 19.0
 */
public interface OptionValues {

    /**
     * Returns all available options.
     *
     * @since 19.0
     */
    OptionDescriptors getDescriptors();

    /**
     * Sets the value of {@code optionKey} to {@code value}.
     *
     * @throws UnsupportedOperationException because this operation has been deprecated and is no
     *             longer supported, in order for OptionValues to be read-only.
     *
     * @since 19.0
     * @deprecated {@link OptionValues} should be read-only. If the value of an option needs to be
     *             altered after options are set, then the new value should be stored in the
     *             language's context or instrument fields and read from there.
     */
    @Deprecated
    <T> void set(OptionKey<T> optionKey, T value);

    /**
     * Returns the value of a given option. {@link #hasBeenSet(OptionKey)} can be used to know
     * whether the value was explicitly set, or is the {@link OptionKey#getDefaultValue() default
     * value}.
     *
     * @since 19.0
     */
    <T> T get(OptionKey<T> optionKey);

    /**
     * Determines if a value for {@code optionKey} has been set explicitly by the {@code Context} or
     * {@code Engine}, and therefore {@link #get(OptionKey)} does not call
     * {@link OptionKey#getDefaultValue()}.
     *
     * @since 19.0
     */
    boolean hasBeenSet(OptionKey<?> optionKey);

    /**
     * Determines if a value for any of the option keys in {@link #getDescriptors() option
     * descriptors} {@link #hasBeenSet(OptionKey) has been set}.
     *
     * @since 19.0
     */
    default boolean hasSetOptions() {
        for (OptionDescriptor descriptor : getDescriptors()) {
            if (hasBeenSet(descriptor.getKey())) {
                return true;
            }
        }
        return false;
    }

}
