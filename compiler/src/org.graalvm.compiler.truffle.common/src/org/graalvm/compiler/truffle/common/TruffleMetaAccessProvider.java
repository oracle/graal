/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import jdk.vm.ci.meta.JavaConstant;

/**
 * An interface for communication between the compiler and runtime for Inlining related information.
 */
public interface TruffleMetaAccessProvider {

    /**
     * Gets the runtime representation of the call node constant.
     */
    TruffleCallNode findCallNode(JavaConstant callNode);

    /**
     * If {@code node} represents an AST Node then return the nearest source information for it.
     * Otherwise simply return null.
     */
    TruffleSourceLanguagePosition getPosition(JavaConstant node);

    /**
     * Records the given target to be dequed from the compilation queue at the end of the current
     * compilation.
     */
    void addTargetToDequeue(CompilableTruffleAST target);

    /**
     * To be used from the compiler side. Sets how many calls in total are in the related
     * compilation unit. Includes both calls that were inlined and calls that were not (ie. remained
     * calls after inlining).
     */
    void setCallCount(int count);

    /**
     * To be used from the compiler side. Sets how many calls in total were inlined into the
     * compilation unit.
     */
    void setInlinedCallCount(int count);

    /**
     * To be used from the compiler side.
     *
     * @param target register this target as inlined.
     */
    void addInlinedTarget(CompilableTruffleAST target);
}
