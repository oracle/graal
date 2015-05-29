/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import java.util.*;

import com.oracle.truffle.api.nodes.NodeUtil.FieldOffsetProvider;
import com.oracle.truffle.api.object.Layout.ImplicitCast;

public class LayoutBuilder {
    private EnumSet<ImplicitCast> allowedImplicitCasts;
    private FieldOffsetProvider fieldOffsetProvider;

    public LayoutBuilder() {
        this.allowedImplicitCasts = Layout.NONE;
        this.fieldOffsetProvider = null;
    }

    public Layout build() {
        return Layout.getFactory().createLayout(this);
    }

    public LayoutBuilder setAllowedImplicitCasts(EnumSet<ImplicitCast> allowedImplicitCasts) {
        this.allowedImplicitCasts = allowedImplicitCasts;
        return this;
    }

    public LayoutBuilder setFieldOffsetProvider(FieldOffsetProvider fieldOffsetProvider) {
        this.fieldOffsetProvider = fieldOffsetProvider;
        return this;
    }

    public EnumSet<ImplicitCast> getAllowedImplicitCasts() {
        return allowedImplicitCasts;
    }

    public FieldOffsetProvider getFieldOffsetProvider() {
        return fieldOffsetProvider;
    }
}
