/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.heap;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Common snippet reflection support for analysis-time providers that use the image heap as the
 * source of object constants.
 */
public abstract class AbstractImageHeapSnippetReflectionProvider implements SnippetReflectionProvider {
    private ImageHeapScanner heapScanner;
    private final WordTypes wordTypes;

    protected AbstractImageHeapSnippetReflectionProvider(ImageHeapScanner heapScanner, WordTypes wordTypes) {
        this.heapScanner = heapScanner;
        this.wordTypes = wordTypes;
    }

    /**
     * Installs the heap scanner after both components have been constructed.
     */
    public void setHeapScanner(ImageHeapScanner heapScanner) {
        this.heapScanner = heapScanner;
    }

    protected final ImageHeapScanner getHeapScanner() {
        VMError.guarantee(heapScanner != null, "Heap scanner not installed yet.");
        return heapScanner;
    }

    protected final WordTypes getWordTypes() {
        return wordTypes;
    }

    @Override
    public JavaConstant forObject(Object object) {
        return getHeapScanner().createImageHeapConstant(object, OtherReason.UNKNOWN);
    }

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        }
        return forBoxedPrimitive(kind, value);
    }

    protected JavaConstant forBoxedPrimitive(JavaKind kind, Object value) {
        VMError.guarantee(kind != JavaKind.Object, "Object values must be redirected through the image heap.");
        return JavaConstant.forBoxedPrimitive(value);
    }

    @Override
    public final <T> T asObject(Class<T> type, JavaConstant constant) {
        JavaConstant unwrapped = constant;
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            unwrapped = imageHeapConstant.getHostedObject();
            if (unwrapped == null) {
                /*
                 * Simulated image-heap object without a hosted backing object.
                 */
                return null;
            }
        }
        VMError.guarantee(!(unwrapped instanceof ImageHeapConstant));
        return asObjectFromUnwrapped(type, unwrapped);
    }

    protected abstract <T> T asObjectFromUnwrapped(Class<T> type, JavaConstant constant);

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        if (type.isAssignableFrom(WordTypes.class)) {
            return type.cast(wordTypes);
        }
        return null;
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Executable originalMethod(ResolvedJavaMethod method) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Field originalField(ResolvedJavaField field) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }
}
