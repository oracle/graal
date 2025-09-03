/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pltgot;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.MethodAddressResolver;

/**
 * Provides necessary services for dynamic method address resolution through the PLT/GOT.
 */
public interface MethodAddressResolutionSupport {

    /**
     * Predicate that determines if an indirect GOT call should be emitted when the callee is called
     * from the caller.
     *
     */
    boolean shouldCallViaPLTGOT(SharedMethod caller, SharedMethod callee);

    /**
     * Predicate that determines if an indirect GOT call should be used when the caller of the
     * method is not known.
     */
    boolean shouldCallViaPLTGOT(SharedMethod callee);

    /**
     * Allows the resolver to augment the object file produced by the image builder. This can be
     * used, for example, to create a custom section in the resulting object file.
     */
    void augmentImageObjectFile(ObjectFile imageObjectFile);

    /**
     * Creates a resolver that will be used to resolve the addresses of methods called through the
     * PLT/GOT at runtime.
     */
    MethodAddressResolver createMethodAddressResolver();
}
