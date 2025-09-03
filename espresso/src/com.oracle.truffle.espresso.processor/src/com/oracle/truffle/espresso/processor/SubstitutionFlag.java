/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.processor;

/**
 * Provides additional flags to a {@code JavaSubstitution}, through the annotation attribute
 * {@code Substitution#flags()}.
 *
 * Must be kept in sync with its main espresso project counterpart.
 */
public final class SubstitutionFlag {
    /**
     * If the substitution is trivial.
     *
     * <p>
     * Trivial methods are considered part of the caller and will be inlined whenever possible (even
     * if inlining is disabled).
     * </p>
     * <p>
     * Inlining a trivial method should not increase code size compared to the call, in general
     * trivial methods should:
     * <ul>
     * <li>Be reasonably small</li>
     * <li>Not contain guest calls (leaf method)</li>
     * <li>Not contain loops</li>
     * </ul>
     */
    public static final byte IsTrivial = 0b00000001;

    /**
     * Marks a method that should be inlined in bytecode. Will be set automatically by the
     * annotation {@code InlineInBytecode}.
     */
    public static final byte InlineInBytecode = 0b00000010;

    private SubstitutionFlag() {
    }
}
