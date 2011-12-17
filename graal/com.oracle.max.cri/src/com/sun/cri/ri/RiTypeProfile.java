/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ri;

import java.io.*;

/**
 * This profile object represents the type profile of one call site, cast or instanceof instruction. The precision of
 * the supplied values may vary, but a runtime that provides this information should be aware that it will be used to
 * guide performance-critical decisions like speculative inlining, etc.
 */
public class RiTypeProfile implements Serializable {

    /**
     * How often the instruction was executed, which may be used to judge the maturity of this profile.
     */
    public int count;

    /**
     * An estimation of how many different receiver types were encountered. This may or may not be the same as
     * probabilities.length/types.length, as the runtime may store probabilities for a limited number of receivers.
     */
    public int morphism;

    /**
     * A list of receivers for which the runtime has recorded probability information. This array needs to have the same
     * length as {@link RiTypeProfile#probabilities}.
     */
    public RiResolvedType[] types;

    /**
     * The estimated probabilities of the different receivers. This array needs to have the same length as
     * {@link RiTypeProfile#types}.
     */
    public float[] probabilities;
}
