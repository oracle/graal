/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.infrastructure;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

import com.oracle.graal.pointsto.api.HostVM;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public interface Universe {

    HostVM hostVM();

    SnippetReflectionProvider getSnippetReflection();

    ResolvedJavaType lookup(JavaType type);

    JavaType lookupAllowUnresolved(JavaType type);

    ResolvedJavaField lookup(JavaField field);

    JavaField lookupAllowUnresolved(JavaField field);

    ResolvedJavaMethod lookup(JavaMethod method);

    JavaMethod lookupAllowUnresolved(JavaMethod method);

    WrappedSignature lookup(Signature signature, WrappedJavaType defaultAccessingClass);

    WrappedConstantPool lookup(ConstantPool constantPool, WrappedJavaType defaultAccessingClass);

    JavaConstant lookup(JavaConstant constant);

    ResolvedJavaMethod resolveSubstitution(ResolvedJavaMethod method);

    ResolvedJavaType objectType();
}
