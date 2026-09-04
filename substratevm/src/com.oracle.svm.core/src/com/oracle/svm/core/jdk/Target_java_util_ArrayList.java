/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.vector.replacements.CopyOfNode;

/**
 * This substitution is necessary to enable the {@link CopyOfNode} analysis optimization to prove
 * that the type of {@code ArrayList.elementData} is always {@code Object[]} and enable store checks
 * elimination.
 * <p>
 * The {@code CopyOfNode} analysis optimization is based on the observation that often times the
 * {@code newType} argument of calls to {@link Arrays#copyOf(Object[], int, Class)} is either the
 * result of a call to {@link Object#getClass()} or is a {@link Class} literal. In the first case
 * the type state of the result is the same as the type state of the receiver of the
 * {@link Object#getClass()} call. In the second case the type state is exactly the {@link Class}
 * literal. By specializing these cases the points-to analysis can prove that the calls to
 * {@link Arrays#copyOf(Object[], int, Class)} in {@link ArrayList}, and in other collection
 * classes, will always return {@code Object[]}, however it cannot immediately conclude that
 * {@code ArrayList.elementData} is always {@code Object[]}.
 * <p>
 * The problem is that the original implementation of {@link ArrayList#ArrayList(Collection)} first
 * calls {@link Collection#toArray()} on the collection argument and if the argument was indeed an
 * {@link ArrayList} just stores the resulting value to {@code elementData}. This is an optimization
 * because {@link ArrayList#toArray()} is implemented as {@code Arrays.copyOf(elementData, size)},
 * so no additional array copy is necessary. Alas, for some implementations of
 * {@link Collection#toArray()}, e.g., {@link ArrayDeque#toArray()}, the analysis cannot prove that
 * they always return {@code Object[]}. Therefore, the analysis concludes that
 * {@code ArrayList.elementData} can contain any subtype of {@code Object[]}.
 * <p>
 * To help the analysis the substitution below explicitly replaces the call to
 * {@link ArrayList#toArray()} with the {@code Arrays.copyOf(elementData, size)}, effectively
 * inlining it. Now every store to {@code elementData} is explicitly preceded to a call to
 * {@code Arrays.copyOf()}, thus enabling the {@code CopyOfNode} analysis optimization.
 */
@TargetClass(java.util.ArrayList.class)
public final class Target_java_util_ArrayList {

    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = true) //
    private static Object[] EMPTY_ELEMENTDATA;
    // Checkstyle: start
    @Alias Object[] elementData;
    @Alias private int size;

    /**
     * Constructs an array list while preserving a precise {@code Object[]} allocation in the
     * analysis graph.
     */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/java.base/share/classes/java/util/ArrayList.java#L181-L192")
    public Target_java_util_ArrayList(Collection<?> collection) {
        if (collection.getClass() == ArrayList.class) {
            Target_java_util_ArrayList arrayList = SubstrateUtil.cast(collection, Target_java_util_ArrayList.class);
            if ((size = arrayList.size) != 0) {
                elementData = Arrays.copyOf(arrayList.elementData, arrayList.size);
            } else {
                elementData = EMPTY_ELEMENTDATA;
            }
        } else {
            Object[] array = collection.toArray();
            if ((size = array.length) != 0) {
                elementData = Arrays.copyOf(array, size, Object[].class);
            } else {
                elementData = EMPTY_ELEMENTDATA;
            }
        }
    }
}
