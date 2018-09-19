/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @since 1.0
 */
public interface OptionValues {

    /**
     * Returns all available options.
     *
     * @since 1.0
     */
    OptionDescriptors getDescriptors();

    /**
     * Sets the value of {@code optionKey} to {@code value}.
     *
     * @throws IllegalArgumentException if the given value is not {@link OptionType#validate(Object)
     *             validated} by the {@link OptionKey#getType() option type} of the key. Note that
     *             the operation succeeds if the option key is not described by any of the
     *             associated {@link #getDescriptors() descriptors}.
     *
     * @since 1.0
     */
    <T> void set(OptionKey<T> optionKey, T value);

    /**
     * Returns the value of a given option. If no value is set or the key is not described by any
     * {@link #getDescriptors() descriptors} the {@link OptionType#getDefaultValue() default value}
     * of the given key is returned.
     *
     * @since 1.0
     */
    <T> T get(OptionKey<T> optionKey);

    /**
     * Determines if a value for {@code optionKey} has been {@link #set} in this set of option
     * values.
     *
     * @since 1.0
     */
    boolean hasBeenSet(OptionKey<?> optionKey);

    /**
     * Determines if a value for any of the option keys in {@link #getDescriptors() option
     * descriptors} has been {@link #set} in this set of option values.
     *
     * @since 1.0
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
