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
 * Provides information about Truffle runtime to implementation of {@link TruffleServices}. Obtain
 * via {@link TruffleServices#info()}.
 * 
 * @since 0.12
 */
public abstract class TruffleInfo {
    static TruffleInfo DEFAULT;
    static {
        try {
            Class.forName("com.oracle.truffle.api.impl.TruffleInfoImpl", true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Restricted constructor. Available only to selected subclasses.
     * 
     * @throws IllegalStateException usually throws {@link IllegalStateException} exception
     * @since 0.12
     */
    protected TruffleInfo() {
        if (DEFAULT != null || !getClass().getName().equals("com.oracle.truffle.api.impl.TruffleInfoImpl")) {
            throw new IllegalStateException();
        }
        DEFAULT = this;
    }

    /**
     * Allows the Truffle API to access services of the runtime. For example
     * {@link LoopCountSupport}, etc.
     *
     * @param <T> the requested type
     * @param type class of the request service
     * @return instance of the requested type or <code>null</code> if none has been found
     * @since 0.12
     */
    @SuppressWarnings("static-method")
    protected final <T> T lookup(Class<T> type) {
        Object value = null;
        if (type == LoopCountSupport.class) {
            value = TruffleServices.DEFAULT.loopCount();
        }
        return type.cast(value);
    }

    /**
     * Finds class of a {@link com.oracle.truffle.api.TruffleLanguage language} this node belongs
     * to.
     *
     * @param node instance of {@link com.oracle.truffle.api.nodes.Node}
     * @return class of the language that created the node
     * @since 0.12
     */
    public abstract Class<?> findLanguage(Object node);

    /**
     * Notifies the Truffle runtime that a call target has just been initialized.
     *
     * @param callTarget instance of {@link com.oracle.truffle.api.CallTarget}
     * @since 0.12
     */
    public abstract void initializeCallTarget(Object callTarget);
}
