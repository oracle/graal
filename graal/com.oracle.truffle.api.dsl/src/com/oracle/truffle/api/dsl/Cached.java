/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;

/**
 * <p>
 * A parameter annotated with {@link Cached} in a {@link Specialization} refers to a <b>cached</b>
 * value of a specialization instance. A cached parameter value is initialized once using the
 * initializer expression at specialization instantiation. For each call of the specialization
 * method the cached value is provided by using the annotated parameter from the method body. Cache
 * initializers are potentially executed before guard expressions declared in
 * {@link Specialization#guards()}.
 * </p>
 * <p>
 * A typical specialization may define multiple dynamic and multiple cached parameters. Dynamic
 * parameter values are typically provided by executing child nodes of the operation. Cached
 * parameters are initialized and stored once per specialization instantiation. Cached parameters
 * are always constant at compile time. You may verify this by invoking
 * {@link CompilerAsserts#compilationConstant(Object)} on any cached parameter. For consistency
 * between specialization declarations cached parameters must be declared last in a specialization
 * method.
 * </p>
 * <p>
 * The initializer expression of a cached parameter is defined using a subset of Java. This subset
 * includes field/parameter accesses, function calls, type exact infix comparisons (==, !=, <, <=,
 * >, >=) and integer literals. The return type of the initializer expression must be assignable to
 * the parameter type. If the annotated parameter type is derived from {@link Node} then the
 * {@link Node} instance is allowed to use the {@link Node#replace(Node)} method to replace itself.
 * Bound elements without receivers are resolved using the following order:
 * <ol>
 * <li>Dynamic and cached parameters of the enclosing specialization.</li>
 * <li>Fields defined using {@link NodeField} for the enclosing node.</li>
 * <li>Public constructors of the type of the annotated parameter using the <code>new</code> keyword
 * as method name.</li>
 * <li>Public and static methods or fields of the type of the annotated parameter.</li>
 * <li>Non-private, static or virtual methods or fields of enclosing node.</li>
 * <li>Non-private, static or virtual methods or fields of super types of the enclosing node.</li>
 * <li>Public and static methods or fields imported using {@link ImportStatic}.</li>
 * </ol>
 *
 * The following examples explain the intended use of the {@link Cached} annotation. All of the
 * examples have to be enclosed in the following node declaration:
 * </p>
 *
 * <pre>
 * @NodeChild("operand")
 * abstract TestNode extends Node {
 *   abstract void execute(Object operandValue);
 *   // ... example here ...
 * }
 * </pre>
 *
 * <ol>
 * <li>
 * This example defines one dynamic and one cached parameter. The operand parameter is representing
 * the dynamic value of the operand while the cachedOperand is initialized once at first execution
 * of the specialization (specialization instantiation time).
 *
 * <pre>
 *  &#064;Specialization
 *  void doCached(int operand, @Cached(&quot;operand&quot;) int cachedOperand) {
 *      CompilerAsserts.compilationConstant(cachedOperand);
 *      ...
 *  }
 *
 *  Example executions:
 *  execute(1) => doCached(1, 1) // new instantiation, localOperand is bound to 1
 *  execute(0) => doCached(0, 1)
 *  execute(2) => doCached(2, 1)
 *
 * </pre>
 *
 * </li>
 * <li>
 * We extend the previous example by a guard for the cachedOperand value to be equal to the dynamic
 * operand value. This specifies that the specialization is instantiated for each individual operand
 * value that is provided. There are a lot of individual <code>int</code> values and for each
 * individual <code>int</code> value a new specialization would get instantiated. The
 * {@link Specialization#limit()} property defines a limit for the number of specializations that
 * can get instantiated. If the specialization instantiation limit is reached then no further
 * specializations are instantiated. Like for other specializations if there are no more
 * specializations defined an {@link UnsupportedSpecializationException} is thrown. The default
 * specialization instantiation limit is <code>3</code>.
 *
 * <pre>
 * &#064;Specialization(guards = &quot;operand == cachedOperand&quot;)
 * void doCached(int operand, @Cached(&quot;operand&quot;) int cachedOperand) {
 *    CompilerAsserts.compilationConstant(cachedOperand);
 *    ...
 * }
 *
 * Example executions:
 * execute(0) => doCached(0, 0) // new instantiation, cachedOperand is bound to 0
 * execute(1) => doCached(1, 1) // new instantiation, cachedOperand is bound to 1
 * execute(1) => doCached(1, 1)
 * execute(2) => doCached(2, 2) // new instantiation, cachedOperand is bound to 2
 * execute(3) => throws UnsupportedSpecializationException // instantiation limit overflows
 *
 * </pre>
 *
 * </li>
 * <li>
 * To handle the limit overflow we extend our example by an additional specialization named
 * <code>doNormal</code>. This specialization has the same type restrictions but does not have local
 * state nor the operand identity guard. It is also declared after <code>doCached</code> therefore
 * it is only instantiated if the limit of the <code>doCached</code> specialization has been
 * reached. In other words <code>doNormal</code> is more generic than <code>doCached</code> . The
 * <code>doNormal</code> specialization uses <code>contains=&quot;doCached&quot;</code> to specify
 * that all instantiations of <code>doCached</code> get removed if <code>doNormal</code> is
 * instantiated. Alternatively if the <code>contains</code> relation is omitted then all
 * <code>doCached</code> instances remain but no new instances are created.
 *
 * <code>
 * &#064;Specialization(guards = &quot;operand == cachedOperand&quot;)
 * void doCached(int operand, @Cached(&quot;operand&quot;) int cachedOperand) {
 *    CompilerAsserts.compilationConstant(cachedOperand);
 *    ...
 * }
 *
 * &#064;Specialization(contains = &quot;doCached&quot;)
 * void doNormal(int operand) {...}
 *
 * Example executions with contains = &quot;doCached&quot;:
 * execute(0) => doCached(0, 0) // new instantiation, cachedOperand is bound to 0
 * execute(1) => doCached(1, 1) // new instantiation, cachedOperand is bound to 1
 * execute(1) => doCached(1, 1)
 * execute(2) => doCached(2, 2) // new instantiation, cachedOperand is bound to 2
 * execute(3) => doNormal(3)    // new instantiation of doNormal due to limit overflow; doCached gets removed.
 * execute(1) => doNormal(1)
 *
 * Example executions without contains = &quot;doCached&quot;:
 * execute(0) => doCached(0, 0) // new instantiation, cachedOperand is bound to 0
 * execute(1) => doCached(1, 1) // new instantiation, cachedOperand is bound to 1
 * execute(1) => doCached(1, 1)
 * execute(2) => doCached(2, 2) // new instantiation, cachedOperand is bound to 2
 * execute(3) => doNormal(3)    // new instantiation of doNormal due to limit overflow
 * execute(1) => doCached(1, 1)
 *
 * </code>
 *
 * </li>
 * <li>
 * This next example shows how methods from the enclosing node can be used to initialize cached
 * parameters. Please note that the visibility of transformLocal must not be <code>private</code>.
 *
 * <pre>
 * &#064;Specialization
 * void s(int operand, @Cached(&quot;transformLocal(operand)&quot;) int cachedOperand) {
 * }
 *
 * int transformLocal(int operand) {
 *     return operand & 0x42;
 * }
 *
 * </li>
 * </pre>
 * <li>
 * The <code>new</code> keyword can be used to initialize a cached parameter using a constructor of
 * the parameter type.
 *
 * <pre>
 * &#064;Specialization
 * void s(Object operand, @Cached(&quot;new()&quot;) OtherNode someNode) {
 *     someNode.execute(operand);
 * }
 *
 * static class OtherNode extends Node {
 *
 *     public String execute(Object value) {
 *         throw new UnsupportedOperationException();
 *     }
 * }
 *
 * </pre>
 *
 * </li>
 * <li>
 * Java types without public constructor but with a static factory methods can be initialized by
 * just referencing its static factory method and its parameters. In this case
 * {@link BranchProfile#create()} is used to instantiate the {@link BranchProfile} instance.
 *
 * <pre>
 * &#064;Specialization
 * void s(int operand, @Cached(&quot;create()&quot;) BranchProfile profile) {
 * }
 * </pre>
 *
 * </li>
 * </ol>
 *
 * @see Specialization#guards()
 * @see Specialization#contains()
 * @see Specialization#limit()
 * @see ImportStatic
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Cached {

    /**
     * Defines the initializer expression of the cached parameter value.
     *
     * @see Cached
     */
    String value();

}
