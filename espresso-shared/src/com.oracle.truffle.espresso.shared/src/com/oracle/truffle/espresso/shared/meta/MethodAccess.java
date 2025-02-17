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

import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.shared.vtable.PartialMethod;

/**
 * Represents a {@link java.lang.reflect.Method}, and provides access to various runtime metadata.
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface MethodAccess<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends MemberAccess<C, M, F>, Signed, PartialMethod<C, M, F> {
    /**
     * Obtains the parsed signature for this method.
     * <p>
     * A default implementation is provided, but it is encouraged to override this method if the
     * representation of methods allows for a simpler computation (for example, if the method caches
     * its parsed signature).
     *
     * @param symbolPool The symbol pool from which this method draws its symbols.
     * @return The parsed signature of this method.
     */
    default Symbol<Type>[] getParsedSymbolicSignature(SymbolPool symbolPool) {
        return symbolPool.getSignatures().parsed(getSymbolicSignature());
    }

    /**
     * Whether loading constraints checks should be skipped for this method. An example of method
     * which should skip loading constraints are the polymorphic signature methods.
     */
    boolean shouldSkipLoadingConstraints();

    /**
     * Whether this method appears in a VTable, and its VTable index is initialized.
     */
    boolean hasVTableIndex();

    /**
     * The {@link CodeAttribute} associated with this method.
     */
    CodeAttribute getCodeAttribute();

    /**
     * The {@link ExceptionHandler exception handlers} associated with this method.
     */
    ExceptionHandler[] getSymbolicExceptionHandlers();
}
