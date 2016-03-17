/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.boot;

/**
 * Runtime support for the execution of Truffle interpreters. Contains various methods to obtain
 * support for specific areas. These methods may return <code>null</code> if particular feature of
 * the runtime isn't supported.
 * <p>
 * There are two known implementations of the runtime. The default, fallback one, provided by the
 * <em>Truffle</em> API itself. The <em>optimized one</em> provided by the
 * <em>Graal virtual machine</em>. When evolving this interface it is essential to do so with care
 * and respect to these two implementations. Keep in mind, that different version of
 * <em>Truffle</em> API may be executed against different version of <em>Graal</em>.
 *
 * @since 0.12
 */
public abstract class TruffleServices {
    static TruffleServices DEFAULT;
    private final String name;

    /**
     * Constructor for subclasses.
     * 
     * @param name simplified, programmatic name of this implementation to be returned from
     *            {@link #getName()}
     * @since 0.12
     */
    protected TruffleServices(String name) {
        this.name = name;
        if (DEFAULT != null) {
            // throw new IllegalStateException();
        }
        DEFAULT = this;
    }

    /**
     * Obtains programmatic name of this implementation.
     *
     * @return the name identifying this implementation
     * @since 0.12
     */
    public final String getName() {
        return name;
    }

    /**
     * Provides support for additional optimizations of loops.
     *
     * @return <code>null</code> by default, override to return something meaningful
     * @since 0.12
     */
    protected LoopCountSupport<?> loopCount() {
        return null;
    }

    /**
     * Information about truffle API useful for runtime support.
     * 
     * @return instance of the info class, never <code>null</code>
     * @since 0.12
     */
    @SuppressWarnings("static-method")
    protected final TruffleInfo info() {
        return TruffleInfo.DEFAULT;
    }
}
