/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Specifies additional properties for an element also annotated with {@link Alias}, {@link Delete},
 * {@link Substitute}, {@link AnnotateOriginal}, or {@link KeepOriginal}.
 * <p>
 * See {@link TargetClass} for an overview of the annotation system.
 * 
 * @since 22.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface TargetElement {

    /**
     * @since 22.3
     */
    String CONSTRUCTOR_NAME = "<init>";

    /**
     * The name of the field or method in the original class. If the default value is specified for
     * this element, then the name of the annotated field or method is used.
     * <p>
     * To make a reference to a constructor, use the name {@link #CONSTRUCTOR_NAME}.
     * 
     * @since 22.3
     */
    String name() default "";

    /**
     * Substitute only if all provided predicates are true (default: unconditional substitution that
     * is always included).
     *
     * The classes must either implement {@link BooleanSupplier} or {@link Predicate}&lt;Class&gt;
     * (the parameter for {@link Predicate#test} is the "original" class as specified by the
     * {@link TargetClass} annotation, as a {@link Class}).
     * 
     * @since 22.3
     */
    Class<?>[] onlyWith() default TargetClass.AlwaysIncluded.class;
}
