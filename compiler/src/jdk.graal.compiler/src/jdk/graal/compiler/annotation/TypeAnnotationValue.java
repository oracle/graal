/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.util.Arrays;
import java.util.Objects;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * This is the JVMCI analog of {@code sun.reflect.annotation.TypeAnnotation} where the encapsulated
 * annotation is an {@link AnnotationValue} instead of an {@link Annotation}. Furthermore, the
 * {@link #getTargetInfo() target info} and {@link #getTypePath() type path} are represented as
 * their raw class file bytes since that's sufficient for producing Native Image runtime metadata
 * (see {@code encodeTargetInfo()} and {@code encodeLocationInfo()} in
 * {@code AnnotationMetadataEncoder}).
 */
public final class TypeAnnotationValue {

    private final byte[] targetInfo;
    private final byte[] typePath;
    private final AnnotationValue annotation;

    public TypeAnnotationValue(byte[] targetInfo,
                    byte[] typePath,
                    AnnotationValue annotation) {
        this.targetInfo = Objects.requireNonNull(targetInfo);
        this.typePath = Objects.requireNonNull(typePath);
        this.annotation = Objects.requireNonNull(annotation);
    }

    /**
     * Sentinel for {@code target_info} when {@link #annotation} is an
     * {@link AnnotationValue#isError() error} value.
     */
    private static final byte[] TARGET_INFO_FOR_ERROR = {TypeAnnotationValueParser.FIELD};

    /**
     * Sentinel for {@code type_path} when {@link #annotation} is an
     * {@link AnnotationValue#isError() error} value.
     */
    private static final byte[] TYPE_PATH_FOR_ERROR = {0};

    /**
     * Creates a value for an error encountered when parsing the class file bytes.
     */
    public TypeAnnotationValue(AnnotationFormatError error) {
        this.targetInfo = TARGET_INFO_FOR_ERROR;
        this.typePath = TYPE_PATH_FOR_ERROR;
        this.annotation = new AnnotationValue(error);
    }

    public AnnotationValue getAnnotation() {
        return annotation;
    }

    /**
     * Gets the class file bytes of {@code target_info} ({@jvms 4.7.20.1}).
     */
    public byte[] getTargetInfo() {
        return targetInfo;
    }

    /**
     * Gets the class file bytes of {@code type_path} ({@jvms 4.7.20.2}).
     */
    public byte[] getTypePath() {
        return typePath;
    }

    @Override
    public String toString() {
        return annotation + "<target_info: 0x" + NumUtil.bytesToHexString(targetInfo) + ", type_path: 0x" + NumUtil.bytesToHexString(typePath) + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TypeAnnotationValue that) {
            return Arrays.equals(this.targetInfo, that.targetInfo) &&
                            Arrays.equals(this.typePath, that.typePath) &&
                            Objects.equals(this.annotation, that.annotation);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(targetInfo), Arrays.hashCode(typePath), annotation);
    }
}
