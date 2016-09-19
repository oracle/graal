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

import java.util.EnumSet;
import java.util.ServiceLoader;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.object.Shape.Allocator;

/**
 * Describes layout and behavior of a {@link DynamicObject} subclass and is used to create shapes.
 *
 * An object may change its shape but only to shapes of the same layout.
 *
 * @since 0.8 or earlier
 */
public abstract class Layout {
    /** @since 0.8 or earlier */
    public static final String OPTION_PREFIX = "truffle.object.";

    private static final LayoutFactory LAYOUT_FACTORY = loadLayoutFactory();

    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected Layout() {
    }

    /**
     * Specifies the allowed implicit casts between primitive types without losing type information.
     *
     * @since 0.8 or earlier
     */
    public enum ImplicitCast {
        /** @since 0.8 or earlier */
        IntToDouble,
        /** @since 0.8 or earlier */
        IntToLong
    }

    /**
     * Creates a new {@link Builder}.
     *
     * @since 0.8 or earlier
     */
    public static Builder newLayout() {
        return new Builder();
    }

    /**
     * Equivalent to {@code Layout.newLayout().build()}.
     *
     * @since 0.8 or earlier
     */
    public static Layout createLayout() {
        return newLayout().build();
    }

    /** @since 0.8 or earlier */
    public abstract DynamicObject newInstance(Shape shape);

    /** @since 0.8 or earlier */
    public abstract Class<? extends DynamicObject> getType();

    /**
     * Create a root shape.
     *
     * @param objectType that describes the object instance with this shape.
     * @since 0.8 or earlier
     */
    public abstract Shape createShape(ObjectType objectType);

    /**
     * Create a root shape.
     *
     * @param objectType that describes the object instance with this shape.
     * @param sharedData for language-specific use
     * @since 0.8 or earlier
     */
    public abstract Shape createShape(ObjectType objectType, Object sharedData);

    /**
     * Create a root shape.
     *
     * @param objectType that describes the object instance with this shape.
     * @param sharedData for language-specific use
     * @param id for language-specific use
     * @return new instance of a shape
     * @since 0.8 or earlier
     */
    public abstract Shape createShape(ObjectType objectType, Object sharedData, int id);

    /**
     * Create an allocator for static property creation. Reserves all array extension slots.
     *
     * @since 0.8 or earlier
     */
    public abstract Allocator createAllocator();

    /** @since 0.8 or earlier */
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
            bestLayoutFactory = Truffle.getRuntime().getCapability(LayoutFactory.class);
            if (bestLayoutFactory == null) {
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
        }

        if (bestLayoutFactory == null) {
            throw new AssertionError("LayoutFactory not found");
        }
        return bestLayoutFactory;
    }

    /**
     * Layout builder.
     *
     * @see Layout
     * @since 0.8 or earlier
     */
    public static final class Builder {
        private EnumSet<ImplicitCast> allowedImplicitCasts;
        private boolean polymorphicUnboxing;

        /**
         * Create a new layout builder.
         */
        private Builder() {
            this.allowedImplicitCasts = EnumSet.noneOf(ImplicitCast.class);
        }

        /**
         * Build {@link Layout} from the configuration in this builder.
         *
         * @since 0.8 or earlier
         */
        public Layout build() {
            return Layout.getFactory().createLayout(this);
        }

        /**
         * Set the allowed implicit casts in this layout.
         *
         * @see Layout.ImplicitCast
         * @since 0.8 or earlier
         */
        public Builder setAllowedImplicitCasts(EnumSet<ImplicitCast> allowedImplicitCasts) {
            this.allowedImplicitCasts = allowedImplicitCasts;
            return this;
        }

        /**
         * Add an allowed implicit cast in this layout.
         *
         * @see Layout.ImplicitCast
         * @since 0.8 or earlier
         */
        public Builder addAllowedImplicitCast(ImplicitCast allowedImplicitCast) {
            this.allowedImplicitCasts.add(allowedImplicitCast);
            return this;
        }

        /**
         * If {@code true}, try to keep properties with polymorphic primitive types unboxed.
         *
         * @since 0.8 or earlier
         */
        public Builder setPolymorphicUnboxing(boolean polymorphicUnboxing) {
            this.polymorphicUnboxing = polymorphicUnboxing;
            return this;
        }
    }

    /** @since 0.8 or earlier */
    protected static EnumSet<ImplicitCast> getAllowedImplicitCasts(Builder builder) {
        return builder.allowedImplicitCasts;
    }

    /** @since 0.8 or earlier */
    protected static boolean getPolymorphicUnboxing(Builder builder) {
        return builder.polymorphicUnboxing;
    }
}
