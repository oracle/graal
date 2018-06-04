/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.info;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AccessorInfo extends ElementInfo {

    public enum AccessorKind {
        GETTER,
        SETTER,
        ADDRESS,
        OFFSET
    }

    private final ResolvedJavaMethod annotatedMethod;
    private final AccessorKind accessorKind;
    private final boolean isIndexed;
    private final boolean hasLocationIdentityParameter;
    private final boolean hasUniqueLocationIdentity;

    public AccessorInfo(ResolvedJavaMethod annotatedMethod, AccessorKind accessorKind, boolean isIndexed, boolean hasLocationIdentityParameter, boolean hasUniqueLocationIdentity) {
        super(annotatedMethod.getName());
        this.annotatedMethod = annotatedMethod;
        this.accessorKind = accessorKind;
        this.isIndexed = isIndexed;
        this.hasLocationIdentityParameter = hasLocationIdentityParameter;
        this.hasUniqueLocationIdentity = hasUniqueLocationIdentity;
    }

    public AccessorKind getAccessorKind() {
        return accessorKind;
    }

    public boolean isIndexed() {
        return isIndexed;
    }

    public boolean hasLocationIdentityParameter() {
        return hasLocationIdentityParameter;
    }

    public boolean hasUniqueLocationIdentity() {
        return hasUniqueLocationIdentity;
    }

    public int baseParameterNumber(boolean withReceiver) {
        assert withReceiver;
        /* Convention: the accessed pointer is the receiver of the accessor method. */
        return 0;
    }

    public int indexParameterNumber(boolean withReceiver) {
        assert isIndexed();
        /* Convention: the index is always the first parameter of an accessor method. */
        return withReceiver ? 1 : 0;
    }

    public int valueParameterNumber(boolean withReceiver) {
        assert accessorKind == AccessorKind.SETTER;
        /* Convention: the value to be stored is after the index (if the index is present). */
        return (withReceiver ? 1 : 0) + (isIndexed() ? 1 : 0);
    }

    public int locationIdentityParameterNumber(boolean withReceiver) {
        assert hasLocationIdentityParameter();
        /* Convention: the location identity is always the last parameter of an accessor method. */
        return parameterCount(withReceiver) - 1;
    }

    public int parameterCount(boolean withReceiver) {
        return (withReceiver ? 1 : 0) + (isIndexed() ? 1 : 0) + (getAccessorKind() == AccessorKind.SETTER ? 1 : 0) + (hasLocationIdentityParameter() ? 1 : 0);
    }

    @Override
    public Object getAnnotatedElement() {
        return annotatedMethod;
    }

    @Override
    public void accept(InfoTreeVisitor visitor) {
        visitor.visitAccessorInfo(this);
    }
}
