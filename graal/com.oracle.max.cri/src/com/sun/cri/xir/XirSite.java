/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.xir;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Encapsulates the notion of a site where XIR can be supplied. It is supplied to the {@link RiXirGenerator} by the
 * compiler for each place where XIR can be generated. This interface allows a number of queries, including the
 * bytecode-level location and optimization hints computed by the compiler.
 */
public interface XirSite {

    /**
     * Gets the {@link CiCodePos code position} associated with this site. This is useful for inserting
     * instrumentation at the XIR level.
     * @return the code position if it is available; {@code null} otherwise
     */
    CiCodePos getCodePos();

    /**
     * Checks whether the specified argument is guaranteed to be non-null at this site.
     * @param argument the argument
     * @return {@code true} if the argument is non null at this site
     */
    boolean isNonNull(XirArgument argument);

    /**
     * Checks whether this site requires a null check.
     * @return {@code true} if a null check is required
     */
    boolean requiresNullCheck();

    /**
     * Checks whether this site requires a range check.
     * @return {@code true} if a range check is required
     */
    boolean requiresBoundsCheck();

    /**
     * Checks whether this site requires a read barrier.
     * @return {@code true} if a read barrier is required
     */
    boolean requiresReadBarrier();

    /**
     * Checks whether this site requires a write barrier.
     * @return {@code true} if a write barrier is required
     */
    boolean requiresWriteBarrier();

    /**
     * Checks whether this site requires an array store check.
     * @return {@code true} if an array store check is required
     */
    boolean requiresArrayStoreCheck();

    /**
     * Checks whether an approximation of the type for the specified argument is available.
     * @param argument the argument
     * @return an {@link RiType} indicating the most specific type known for the argument, if any;
     * {@code null} if no particular type is known
     */
    RiType getApproximateType(XirArgument argument);

    /**
     * Checks whether an exact type is known for the specified argument.
     * @param argument the argument
     * @return an {@link RiType} indicating the exact type known for the argument, if any;
     * {@code null} if no particular type is known
     */
    RiType getExactType(XirArgument argument);
}
