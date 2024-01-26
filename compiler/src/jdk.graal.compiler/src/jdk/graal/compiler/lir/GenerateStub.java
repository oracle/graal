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
package jdk.graal.compiler.lir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;

/**
 * Generate a stub to be called via foreign call. This annotation is valid for methods annotated
 * with {@link NodeIntrinsic} only. To trigger stub generation, a marker class annotated with
 * {@link GeneratedStubsHolder} is required. Processed by {@code IntrinsicStubProcessor}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Repeatable(GenerateStubs.class)
public @interface GenerateStub {

    /**
     * Name of the stub method. Defaults to the annotated method's name.
     */
    String name() default "";

    /**
     * Optional values for parameters annotated with {@link ConstantNodeParameter}. The string
     * content is pasted as-is into the generated code, with the only exception being enum values -
     * in that case, the enum class name and a dot is prepended to the string.
     */
    String[] parameters() default {};

    /**
     * Optional name of a static method in the current class that returns the minimum set of
     * required AMD64 CPU features as a {@link java.util.EnumSet}.
     */
    String minimumCPUFeaturesAMD64() default "";

    /**
     * Optional name of a static method in the current class that returns the minimum set of
     * required AARCH64 CPU features as a {@link java.util.EnumSet}.
     */
    String minimumCPUFeaturesAARCH64() default "";
}
