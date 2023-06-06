/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.common;

import java.lang.invoke.MethodType;
import java.util.List;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Note this is a duplicate of JVMCI 22.1's jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation
 * and is needed until this JVMCI version within OpenJDK is fully backported.
 *
 * The details for invoking a bootstrap method associated with a {@code CONSTANT_Dynamic_info} or
 * {@code CONSTANT_InvokeDynamic_info} pool entry .
 *
 * @jvms 4.4.10 The {@code CONSTANT_Dynamic_info} and {@code CONSTANT_InvokeDynamic_info} Structures
 * @jvms 4.7.23 The {@code BootstrapMethods} Attribute
 */
public interface BootstrapMethodIntrospection {
    /**
     * Gets the bootstrap method that will be invoked.
     */
    ResolvedJavaMethod getMethod();

    /**
     * Returns {@code true} if this bootstrap method invocation is for a
     * {@code CONSTANT_InvokeDynamic_info} pool entry, {@code false} if it is for a
     * {@code CONSTANT_Dynamic_info} entry.
     */
    boolean isInvokeDynamic();

    /**
     * Gets the name of the pool entry.
     */
    String getName();

    /**
     * Returns a reference to the {@link MethodType} ({@code this.isInvokeDynamic() == true}) or
     * {@link Class} ({@code this.isInvokeDynamic() == true}) resolved for the descriptor of the
     * pool entry.
     */
    JavaConstant getType();

    /**
     * Gets the static arguments with which the bootstrap method will be invoked.
     *
     * @jvms 5.4.3.6
     */
    List<JavaConstant> getStaticArguments();
}
