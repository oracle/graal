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
package com.oracle.svm.jdwp.bridge;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface SymbolicRefs {

    long NULL = 0L;

    /**
     * Returns the handle/id of the given "symbolic" type reference. If the given type is
     * {@code null}, then {@code 0L} is returned.
     *
     * @throws IllegalArgumentException if the given reference is not part of the
     *             interpreter/metadata universe e.g. an arbitrary {@link ResolvedJavaType}
     *             instance.
     */
    long toTypeRef(ResolvedJavaType resolvedJavaType);

    /**
     * Returns the handle/id of the given "symbolic" field reference. If the given type is
     * {@code null}, then {@code 0L} is returned.
     *
     * @throws IllegalArgumentException if the given reference is not part of the *
     *             interpreter/metadata universe e.g. an arbitrary {@link ResolvedJavaField}
     *             instance.
     */
    long toFieldRef(ResolvedJavaField resolvedJavaField);

    /**
     * Returns the handle/id of the given "symbolic" method reference associated. If the given type
     * is {@code null}, then {@code 0L} is returned.
     *
     * @throws IllegalArgumentException if the given reference is not part of the *
     *             interpreter/metadata universe e.g. an arbitrary {@link ResolvedJavaMethod}
     *             instance.
     */
    long toMethodRef(ResolvedJavaMethod resolvedJavaMethod);

    /**
     * Returns the "symbolic" type reference associated for the given handle/id. If the given
     * handle/id is {@code 0L}, then {@code null} is returned.
     *
     * @throws JDWPException with {@link ErrorCode#INVALID_CLASS} is handle/id does not refer to an
     *             instance {@link ResolvedJavaField} part of the interpreter/metadata universe.
     * @throws JDWPException with {@link ErrorCode#INVALID_OBJECT} if was unloaded or garbage
     *             collected
     */
    ResolvedJavaType toResolvedJavaType(long typeRefId) throws JDWPException;

    /**
     * Returns the "symbolic" field reference associated for the given handle/id. If the given
     * handle/id is {@code 0L}, then {@code null} is returned.
     *
     * @throws JDWPException with {@link ErrorCode#INVALID_FIELDID} is handle/id does not refer to
     *             an instance {@link ResolvedJavaField} part of the interpreter/metadata universe.
     * @throws JDWPException with {@link ErrorCode#INVALID_OBJECT} if was unloaded or garbage
     *             collected
     */
    ResolvedJavaField toResolvedJavaField(long fieldRefId) throws JDWPException;

    /**
     * Returns the "symbolic" method reference associated for the given handle/id. If the given
     * handle/id is {@code 0L}, then {@code null} is returned.
     * 
     * @throws JDWPException with {@link ErrorCode#INVALID_METHODID} is handle/id does not refer to
     *             an instance {@link ResolvedJavaMethod} part of the interpreter/metadata universe
     * @throws JDWPException with {@link ErrorCode#INVALID_OBJECT} if was unloaded or garbage
     *             collected
     */
    ResolvedJavaMethod toResolvedJavaMethod(long methodRefId) throws JDWPException;
}
