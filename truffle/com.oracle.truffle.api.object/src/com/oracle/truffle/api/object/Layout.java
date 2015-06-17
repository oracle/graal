/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.object.Shape.Allocator;

public abstract class Layout {
    public static final EnumSet<ImplicitCast> NONE = EnumSet.noneOf(ImplicitCast.class);
    public static final EnumSet<ImplicitCast> INT_TO_DOUBLE = EnumSet.of(ImplicitCast.IntToDouble);
    public static final EnumSet<ImplicitCast> INT_TO_LONG = EnumSet.of(ImplicitCast.IntToLong);

    public static final String OPTION_PREFIX = "truffle.object.";

    private static final LayoutFactory LAYOUT_FACTORY = loadLayoutFactory();

    public enum ImplicitCast {
        IntToDouble,
        IntToLong,
    }

    public static Layout createLayout() {
        return createLayout(NONE);
    }

    public static Layout createLayout(EnumSet<ImplicitCast> allowedImplicitCasts) {
        return new LayoutBuilder().setAllowedImplicitCasts(allowedImplicitCasts).build();
    }

    public static Layout createLayout(EnumSet<ImplicitCast> allowedImplicitCasts, FieldOffsetProvider fieldOffsetProvider) {
        return new LayoutBuilder().setAllowedImplicitCasts(allowedImplicitCasts).setFieldOffsetProvider(fieldOffsetProvider).build();
    }

    public abstract DynamicObject newInstance(Shape shape);

    public abstract Class<? extends DynamicObject> getType();

    public abstract Shape createShape(ObjectType operations);

    public abstract Shape createShape(ObjectType operations, Object sharedData);

    public abstract Shape createShape(ObjectType operations, Object sharedData, int id);

    /**
     * Create an allocator for static property creation. Reserves all array extension slots.
     */
    public abstract Allocator createAllocator();

    protected static LayoutFactory getFactory() {
        return LAYOUT_FACTORY;
    }

    private static LayoutFactory loadLayoutFactory() {
        LayoutFactory bestLayoutFactory = null;

        String layoutFactoryImplClassName = System.getProperty(OPTION_PREFIX + "LayoutFactory");
        if (layoutFactoryImplClassName != null) {
            Class<?> clazz;
            try {
                clazz = Class.forName(layoutFactoryImplClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            try {
                bestLayoutFactory = (LayoutFactory) clazz.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new AssertionError(e);
            }
        } else {
            ServiceLoader<LayoutFactory> serviceLoader = ServiceLoader.load(LayoutFactory.class, Layout.class.getClassLoader());
            for (LayoutFactory currentLayoutFactory : serviceLoader) {
                if (bestLayoutFactory == null) {
                    bestLayoutFactory = currentLayoutFactory;
                } else if (currentLayoutFactory.getPriority() >= bestLayoutFactory.getPriority()) {
                    assert currentLayoutFactory.getPriority() != bestLayoutFactory.getPriority();
                    bestLayoutFactory = currentLayoutFactory;
                }
            }
        }

        if (bestLayoutFactory == null) {
            throw new AssertionError("LayoutFactory not found");
        }
        return bestLayoutFactory;
    }
}
