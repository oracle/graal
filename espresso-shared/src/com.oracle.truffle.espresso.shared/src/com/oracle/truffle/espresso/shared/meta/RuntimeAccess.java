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

import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;

/**
 * Provides access to some VM-specific capabilities, such as throwing exceptions, or obtaining the
 * implementor's supported {@link JavaVersion}.
 *
 * <h2 id="simpleFormat">Simple message format</h2>
 * <p>
 * A strict subset of {@link String#format(String, Object...)} that <b>ONLY</b> supports:
 * <ul>
 * <li>"%s" -> {@link Object#toString()}</li>
 * <li>"%n" -> {@link System#lineSeparator()}</li>
 * <li>"%%" -> "%"</li> The number of arguments and modifiers must match exactly.
 * </ul>
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface RuntimeAccess<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    /**
     * This runtime's supported {@link JavaVersion}.
     */
    JavaVersion getJavaVersion();

    /**
     * Signals to the runtime that a Java error should be thrown. The type of the error to be thrown
     * is given by the passed {@link ErrorType}.
     * <p>
     * The caller provides an error message that can be constructed using
     * <a href="#simpleFormat">Simple message format</a>
     */
    RuntimeException throwError(ErrorType error, String messageFormat, Object... args);

    /**
     * If {@code error} is an exception that can be thrown by {@link #throwError}, returns the
     * corresponding {@link ErrorType}. Returns null otherwise.
     */
    ErrorType getErrorType(Throwable error);

    /**
     * Performs class loading on behalf of the given accessing class.
     * <p>
     * Its defining class loader is the one to be used for loading.
     *
     * @return The loaded class.
     */
    C lookupOrLoadType(Symbol<Type> type, C accessingClass);

    /**
     * Obtains and returns an object containing certain VM-known classes.
     */
    KnownTypes<C, M, F> getKnownTypes();

    /**
     * Obtains and returns an object containing the various symbol pools for this runtime.
     */
    SymbolPool getSymbolPool();

    /**
     * Signals that an unexpected state has been reached and that the current operation must be
     * aborted.
     * <p>
     * The caller provides an error message that can be constructed using
     * <a href="#simpleFormat">Simple message format</a>
     */
    RuntimeException fatal(String messageFormat, Object... args);

    /**
     * Signals that an unexpected exception was seen and that the current operation must be aborted.
     * <p>
     * The caller provides the unexpected exception and an error message that can be constructed
     * using <a href="#simpleFormat">Simple message format</a>
     */
    RuntimeException fatal(Throwable t, String messageFormat, Object... args);
}
