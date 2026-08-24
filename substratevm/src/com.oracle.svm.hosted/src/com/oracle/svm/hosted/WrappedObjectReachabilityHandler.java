/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.Objects;
import java.util.function.Consumer;

import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Delegates reachable-object notifications to a guest {@link Consumer}. The builder-side
 * {@link #accept(JavaConstant)} contract exposes only the reachable object; analysis access and scan
 * reasons never cross into the guest.
 */
public final class WrappedObjectReachabilityHandler implements Consumer<JavaConstant> {
    private final JavaConstant handler;

    /**
     * Wraps the guest {@code handler} as a builder-side constant consumer.
     *
     * @param handler a {@link JavaConstant} representing the guest consumer
     */
    public WrappedObjectReachabilityHandler(JavaConstant handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public void accept(JavaConstant object) {
        GuestAccess access = GuestAccess.get();
        access.invoke(access.elements.java_util_function_Consumer_accept, handler, object);
    }
}
