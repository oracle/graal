/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import com.oracle.svm.hosted.util.DiffTool.DiffResult.Kind;

public class DiffTool {

    public record DiffResult<T>(Kind kind, int index) {

        public enum Kind {
            Equal,
            Added,
            Removed
        }

        public String toString(List<T> left, List<T> right) {
            return switch (kind) {
                case Equal -> " ";
                case Added -> "+";
                case Removed -> "-";
            } + getEntry(left, right);
        }

        public T getEntry(List<T> left, List<T> right) {
            List<T> holder = switch (kind) {
                case Equal, Removed -> left;
                case Added -> right;
            };
            return holder.get(index);
        }

        @Override
        public String toString() {
            return kind + " on " + switch (kind) {
                case Equal, Removed -> "left";
                case Added -> "right";
            } + " Index=" + index;
        }
    }

    public static <T> List<DiffResult<T>> diffResults(List<T> left, List<T> right) {
        return diffResults(left, right, T::equals);
    }

    public static <T> List<DiffResult<T>> diffResults(List<T> left, List<T> right, BiPredicate<T, T> equality) {
        int[][] length = buildLCSLength(left, right, equality);
        List<DiffResult<T>> result = new ArrayList<>();
        generateDiff(length, left, right, left.size(), right.size(), equality, result);
        return result;
    }

    private static <T> int[][] buildLCSLength(List<T> left, List<T> right, BiPredicate<T, T> equality) {
        int[][] length = new int[left.size() + 1][right.size() + 1];
        for (int leftIndex = 0; leftIndex < left.size(); leftIndex++) {
            for (int rightIndex = 0; rightIndex < right.size(); rightIndex++) {
                if (equality.test(left.get(leftIndex), right.get(rightIndex))) {
                    length[leftIndex + 1][rightIndex + 1] = length[leftIndex][rightIndex] + 1;
                } else {
                    length[leftIndex + 1][rightIndex + 1] = Math.max(length[leftIndex + 1][rightIndex], length[leftIndex][rightIndex + 1]);
                }
            }
        }
        return length;
    }

    private static <T> void generateDiff(int[][] length, List<T> left, List<T> right, int leftIndex, int rightIndex, BiPredicate<T, T> equality, List<DiffResult<T>> result) {
        if (leftIndex > 0 && rightIndex > 0 && equality.test(left.get(leftIndex - 1), right.get(rightIndex - 1))) {
            generateDiff(length, left, right, leftIndex - 1, rightIndex - 1, equality, result);
            result.add(new DiffResult<>(Kind.Equal, leftIndex - 1));
        } else if (rightIndex > 0 && (leftIndex == 0 || length[leftIndex][rightIndex - 1] >= length[leftIndex - 1][rightIndex])) {
            generateDiff(length, left, right, leftIndex, rightIndex - 1, equality, result);
            result.add(new DiffResult<>(Kind.Added, rightIndex - 1));
        } else if (leftIndex > 0 && (rightIndex == 0 || length[leftIndex][rightIndex - 1] < length[leftIndex - 1][rightIndex])) {
            generateDiff(length, left, right, leftIndex - 1, rightIndex, equality, result);
            result.add(new DiffResult<>(Kind.Removed, leftIndex - 1));
        }
    }
}
