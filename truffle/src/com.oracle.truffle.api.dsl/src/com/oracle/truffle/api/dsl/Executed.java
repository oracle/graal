/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

/**
 * This annotation declares a {@link Child child} field to be executed and used as dynamic input
 * values for {@link Specialization specializations}. The field must have package protected,
 * protected or public visibility for the generated subclass to access the field. The field must be
 * manually initialized. If the value was not initialized the execution of the node will fail with a
 * {@link NullPointerException}. A field must also be annotated with {@link Child} or
 * {@link Children}.
 * <p>
 *
 * <b>Example usage:</b>
 *
 * <pre>
 * abstract class ExpressionNode extends Node {
 *     abstract Object execute();
 * }
 *
 * abstract class AddNode extends ExpressionNode {
 *
 *     &#64;Child &#64;Executed ExpressionNode leftNode;
 *     &#64;Child &#64;Executed ExpressionNode rightNode;
 *
 *     AddNode(ExpressionNode leftNode, ExpressionNode rightNode) {
 *         this.leftNode = leftNode;
 *         this.rightNode = rightNode;
 *     }
 *
 *     &#64;Specialization
 *     protected int doAdd(int left, int right) {
 *         return left + right;
 *     }
 *
 * }
 * </pre>
 *
 * @since 0.33
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD})
public @interface Executed {

    /**
     * The {@link #with()} property allows a node to pass the result of one child's executable as an
     * input to another child's executable. These referenced children must be defined before the
     * current node in the execution order. The static field type must declare execute methods with
     * as many arguments as there values specified for {@link #with()}. {@link Frame},
     * {@link VirtualFrame} and {@link MaterializedFrame} instances are passed along implicitly and
     * do not count as argument.
     *
     * <b>Example usage:</b>
     *
     * <pre>
     * abstract class ExpressionNode extends Node {
     *     abstract Object execute();
     * }
     *
     * abstract class ExecuteWithTargetNode extends Node {
     *     abstract Object execute(Object target);
     * }
     *
     * abstract class CallNode extends ExpressionNode {
     *
     *     &#64;Child &#64;Executed ExpressionNode targetNode;
     *     &#64;Child &#64;Executed(with = "targetNode") ExecuteWithTargetNode readProperty;
     *
     *     CallNode(ExpressionNode targetNode, ExecuteWithTargetNode readProperty) {
     *         this.targetNode = targetNode;
     *         this.readProperty = readProperty;
     *     }
     *
     *     &#64;Specialization
     *     protected Object doCall(Object target, Object property) {
     *         // targetNode is executed once and its value is usable in readProperty
     *     }
     * }
     *
     * </pre>
     *
     * @since 0.33
     */
    String[] with() default {
    };
}
