/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * Mechanism for referring to fields and methods otherwise inaccessible due to Java language access
 * control rules. This enables VM code to directly access a private field or invoke a private method
 * in a JDK class without using reflection. Aliases avoid the boxing/unboxing required by reflection
 * and they type check an aliased field access or method invocation statically.
 * <p>
 * The idiom for using {@link Alias} is somewhat related to the {@link Substitute} annotation, but
 * reversed; both are often used in combination. In both cases a separate class is used to declare
 * the aliased and/or substituted methods. In the substitution case occurrences of {@code this}
 * actually refer to the instance of the class being substituted. In the aliased case we pretend
 * that the class declaring the aliased method or field is an instance of the aliasee in order to
 * access its fields or invoke its methods. An alias is always called (method alias) or accessed
 * (field alias), whereas a substitution method is only implemented (usually not called directly).
 * In the body of a substitution method, aliases are often called or accessed.
 * <p>
 * The element can also be annotated with {@link TargetElement} to specify additional properties.
 * See {@link TargetClass} for an overview of the annotation system.
 * <p>
 * When aliasing *non-static* inner classes the constructors are passed a hidden argument which is
 * the outer class. When writing an @Alias for a constructor of a *non-static* inner classes, you
 * have to (a) explicitly declare that parameter, and (b) supply it in the calls.
 * 
 * @since 22.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface Alias {
}
