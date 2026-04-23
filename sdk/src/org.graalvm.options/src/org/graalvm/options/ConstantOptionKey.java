/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageInfo;

/**
 * Represents an {@link OptionKey} whose value is fixed before the polyglot runtime is initialized
 * and cannot be changed afterwards. Because the value is immutable after initialization,
 * {@link #getConstantValue()} is eligible for constant folding by the compiler, allowing branches
 * guarded by this option to be eliminated from compiled code.
 * <p>
 *
 * The option value is initialized from the system property
 * {@code -Dpolyglot.<option-name>=<value>}, which must be set before the polyglot runtime is
 * initialized (i.e. before the containing class is loaded). If the system property is absent, the
 * {@link #getDefaultValue() default value} is used. On HotSpot, this means the property must be
 * present on the JVM command line. In a GraalVM native image, the property is read during image
 * building, so the value is baked into the image.
 * <p>
 * Once the value has been set, {@link #getConstantValue()} returns the same value for the lifetime
 * of the runtime. The GraalVM compiler constant-folds calls to this method when the receiver is a
 * known heap constant (e.g. a {@code static final} field), enabling dead-branch elimination.
 *
 * @since 25.1
 */
public final class ConstantOptionKey<T> extends OptionKey<T> {

    private static final Object UNSET = new Object();

    private volatile Object constantValue = UNSET;

    /**
     * Constructs a new constant option key with the given default value. The default value is used
     * when no value is specified via the system property {@code -Dpolyglot.<option-name>}. Throws
     * {@link IllegalArgumentException} if no default {@link OptionType} could be
     * {@link OptionType#defaultType(Object) resolved} for the given type. The default value must
     * not be {@code null}.
     *
     * @since 25.1
     */
    public ConstantOptionKey(T defaultValue) {
        super(defaultValue);
    }

    /**
     * Constructs a new constant option key with an explicit option type and default value. The
     * default value is used when no value is specified via the system property
     * {@code -Dpolyglot.<option-name>}.
     *
     * @since 25.1
     */
    public ConstantOptionKey(T defaultValue, OptionType<T> type) {
        super(defaultValue, type);
    }

    /**
     * Returns the constant value of this option key. The value is fixed at class initialization
     * time and remains immutable for the lifetime of the runtime, making this method eligible for
     * constant folding by the GraalVM compiler when the receiver is a heap constant.
     *
     * @throws IllegalStateException if the value has not been set yet, which should not happen in
     *             normal usage as the generated option descriptors static initializer sets the
     *             value at class load time.
     * @since 25.1
     */
    @SuppressWarnings("unchecked")
    public T getConstantValue() {
        if (constantValue == UNSET) {
            throw new IllegalStateException("ConstantOptionKey value was not set.");
        }
        return (T) constantValue;
    }

    /**
     * Sets the constant value of this option key. This method is intended to be called from the
     * generated option descriptors static initializer and must not be called after the polyglot
     * runtime has started. In a native image, it must not be called at image run time.
     *
     * @throws IllegalStateException if the value has already been set, or if called at native image
     *             run time.
     * @since 25.1
     */
    public void setConstantValue(T value) {
        if (constantValue != UNSET) {
            throw new IllegalStateException("Constant value is already set.");
        }
        if (ImageInfo.inImageRuntimeCode()) {
            throw new IllegalStateException("ConstantOptionKey must be set at image build time, not at runtime.");
        }
        constantValue = value;
    }
}
