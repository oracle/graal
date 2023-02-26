/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * When used to annotate a method, it indicates that a method declaration is intended to be a
 * substitute for a method declaration in another class. A substitute method must be declared in a
 * class annotated with {@link TargetClass} as the {@link TargetClass#value() value} element of that
 * annotation specifies the class containing the method to be substituted (the <i>substitutee</i>
 * class).
 * <p>
 * The method to be substituted is determined based on a name and a list of parameter types. The
 * name is specified by an optional {@link TargetElement#name()} element of this annotation. If this
 * element is not specified, then the name of the substitution method is used. The parameter types
 * are those of the substitution method.
 * <p>
 * There must never be an explicit call to a non-static method annotated with {@link Substitute}
 * unless it is from another non-static method in the same class.
 * <p>
 * When used to annotate a class, it indicates that the class is intended to be a full substitute
 * for the class specified via {@link TargetClass}. All methods in the target class that are not
 * substituted in the annotated class are implicitly treated as {@link Delete}d.
 * <p>
 * See {@link TargetClass} for an overview of the annotation system.
 * 
 * @since 22.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.FIELD})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface Substitute {
    /**
     * @since 22.3
     */
    boolean polymorphicSignature() default false;
}
