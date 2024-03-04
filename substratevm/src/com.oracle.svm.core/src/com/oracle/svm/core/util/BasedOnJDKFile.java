/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Documents that the element is based on a JDK source file. This is mainly useful for non-Java
 * sources like C++ files. For Java classes, {@link BasedOnJDKClass} might be more appropriate.
 */
@Repeatable(BasedOnJDKFile.List.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface BasedOnJDKFile {

    /**
     * Link to the source file.
     *
     * Currently, only GitHub links to the <a href="https://github.com/openjdk/jdk">openjdk</a> are
     * supported. The format is as follows:
     *
     * <pre>
     *     https://github.com/openjdk/jdk/blob/(tag or revision)/(path/to/the/source/file)(#L(line_start)-L(line_end))?
     * </pre>
     *
     * To specify a line range, a suffix of the form {@code #L[0-9]+-L[0-9]+} might be added. Full
     * example:
     *
     * <pre>
     *     &#64;BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+8/src/hotspot/cpu/x86/vm_version_x86.hpp#L40-L304")
     * </pre>
     */
    String value();

    /**
     * Support for making {@link BasedOnJDKFile} {@linkplain Repeatable repeatable}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = {ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Platforms(Platform.HOSTED_ONLY.class)
    @interface List {
        BasedOnJDKFile[] value();
    }
}
