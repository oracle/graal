/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.lang.annotation.Annotation;
import java.util.Objects;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public abstract class EntryPointCallStubMethod extends NonBytecodeStaticMethod {

    protected EntryPointCallStubMethod(String name, ResolvedJavaType declaringClass, Signature signature, ConstantPool constantPool) {
        super(name, declaringClass, signature, constantPool);
    }

    /**
     * Defines the {@link Uninterruptible} annotation returned for all call stub methods. The
     * synthetic graphs set up the the fixed registers used for safepoint and stack overflow checks,
     * so they must be uninterruptible. The method then called by the stub does not need to be
     * uninterruptible itself.
     */
    @Uninterruptible(reason = "Entry point", calleeMustBe = false)
    @SuppressWarnings("unused")
    private static void uninterruptibleAnnotationHolder() {
    }

    private static final Uninterruptible UNINTERRUPTIBLE_ANNOTATION = Objects.requireNonNull(
                    ReflectionUtil.lookupMethod(EntryPointCallStubMethod.class, "uninterruptibleAnnotationHolder").getAnnotation(Uninterruptible.class));

    @Override
    public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass == Uninterruptible.class) {
            return annotationClass.cast(UNINTERRUPTIBLE_ANNOTATION);
        }
        return null;
    }

    @Override
    public final Annotation[] getAnnotations() {
        return new Annotation[]{UNINTERRUPTIBLE_ANNOTATION};
    }

    @Override
    public final Annotation[] getDeclaredAnnotations() {
        return new Annotation[]{UNINTERRUPTIBLE_ANNOTATION};
    }
}
