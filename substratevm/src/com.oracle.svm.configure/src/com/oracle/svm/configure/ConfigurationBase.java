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
package com.oracle.svm.configure;

import java.util.function.Consumer;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.json.JsonPrintable;

public abstract class ConfigurationBase<T extends ConfigurationBase<T, P>, P> implements JsonPrintable {

    public abstract boolean isEmpty();

    public abstract T copy();

    protected abstract void merge(T other);

    public abstract void mergeConditional(ConfigurationCondition condition, T other);

    protected abstract void subtract(T other);

    protected abstract void intersect(T other);

    protected abstract void removeIf(P predicate);

    protected T copyAnd(Consumer<T> consumer) {
        T copy = copy();
        consumer.accept(copy);
        return copy;
    }

    public T copyAndMerge(T other) {
        return copyAnd(copy -> copy.merge(other));
    }

    public T copyAndSubtract(T other) {
        return copyAnd(copy -> copy.subtract(other));
    }

    public T copyAndIntersect(T other) {
        return copyAnd(copy -> copy.intersect(other));
    }

    public T copyAndFilter(P predicate) {
        return copyAnd(copy -> copy.removeIf(predicate));
    }

    public abstract ConfigurationParser createParser(boolean strictMetadata);
}
