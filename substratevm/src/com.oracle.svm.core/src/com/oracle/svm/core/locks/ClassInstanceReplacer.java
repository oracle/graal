/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.locks;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
public abstract class ClassInstanceReplacer<S, T> implements Function<Object, Object> {

    private final Class<S> sourceClass;
    private final Map<S, T> replacements = Collections.synchronizedMap(new IdentityHashMap<>());
    private boolean sealed;

    protected ClassInstanceReplacer(Class<S> sourceClass) {
        this.sourceClass = sourceClass;
    }

    @Override
    public Object apply(Object object) {
        if (object == null || object.getClass() != sourceClass) {
            return object;
        }
        return replacements.computeIfAbsent(sourceClass.cast(object), this::doReplace);
    }

    private T doReplace(S object) {
        VMError.guarantee(!sealed, "new object introduced after static analysis");
        T replacement = createReplacement(object);
        assert replacement.getClass() != sourceClass : "leads to recursive replacement";
        return replacement;
    }

    public Collection<T> getReplacements() {
        sealed = true;
        return replacements.values();
    }

    protected abstract T createReplacement(S source);
}
