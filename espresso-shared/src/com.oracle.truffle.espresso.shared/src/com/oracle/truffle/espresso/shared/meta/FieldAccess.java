/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.meta;

/**
 * Represents a {@link java.lang.reflect.Field}, and provides access to various runtime metadata.
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface FieldAccess<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends MemberAccess<C, M, F> {
    /**
     * Whether resolution for this field access site should enforce final field only being written
     * in constructors or class initializer, whichever applies (see JVMS-6.5.putfield /
     * JVMS-6.5.putstatic).
     * <p>
     * In particular, this check was not enforced in java bytecodes in versions prior to Java 9.
     */
    boolean shouldEnforceInitializerCheck();
}
