/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
