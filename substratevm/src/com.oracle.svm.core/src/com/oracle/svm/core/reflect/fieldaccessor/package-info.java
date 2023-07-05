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

/**
 * This package includes the implementation of the
 * {@link com.oracle.svm.core.reflect.fieldaccessor.UnsafeFieldAccessorImpl}.
 *
 * Background: JDK 19 ships with JEP 416, which
 * <a href="https://bugs.openjdk.org/browse/JDK-8271820">reimplements core reflection with method
 * handles</a>. However, the old Unsafe-based implementation was still available and used by Native
 * Image. In JDK 22, the <a href="https://bugs.openjdk.org/browse/JDK-8305104">old core reflection
 * implementation was removed</a>. Since the method handle based implementation is designed with
 * Just-in-Time compilation in mind, and would have bad performance in an Ahead-of-Time scenario, we
 * decided to keep old implementation.
 *
 * This package contains the Unsafe-based core reflection implementation as available until <a href=
 * "https://github.com/openjdk/jdk/blob/jdk-22%2B1/src/java.base/share/classes/jdk/internal/reflect/UnsafeFieldAccessorImpl.java">JDK
 * 22 build +1</a>.
 */
package com.oracle.svm.core.reflect.fieldaccessor;
