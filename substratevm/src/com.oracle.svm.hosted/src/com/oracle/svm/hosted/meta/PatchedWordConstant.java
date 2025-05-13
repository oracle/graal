/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Wraps words for which the value is not known during analysis.
 *
 * Such words can be offsets such as {@link MethodOffset} that are patched later when the image is
 * written, or {@linkplain RelocatedPointer relocated pointers} for which linker relocations are
 * created.
 */
public class PatchedWordConstant implements JavaConstant, TypedConstant {

    private final WordBase word;
    private final AnalysisType type;

    public PatchedWordConstant(WordBase word, AnalysisType type) {
        this.word = word;
        this.type = type;
    }

    public WordBase getWord() {
        return word;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        throw new IllegalArgumentException();
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }

    @Override
    public AnalysisType getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PatchedWordConstant rc) {
            return word == rc.word;
        }
        return false;
    }

    @Override
    public String toValueString() {
        if (word instanceof MethodPointer mp) {
            return "relocatable method pointer: " + mp.getMethod().format("%H.%n(%p)") + ", permitsRewriteToPLT: " + mp.permitsRewriteToPLT();
        } else if (word instanceof MethodOffset mo) {
            return "method offset: " + mo.getMethod().format("%H.%n(%p)");
        }
        return "patched word constant";
    }
}
