/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * <p>
 * Defines a method of a node subclass to represent one specialization of an operation. Multiple
 * specializations can be defined in a node representing an operation. A specialization defines
 * which kind of input is expected using the method signature and the annotation attributes. The
 * specialized semantics of the operation are defined using the body of the annotated Java method. A
 * specialization method must be declared in a class that is derived from {@link Node} that
 * references a {@link TypeSystem}. At least one specialization must be defined per operation. If no
 * specialization is valid for the given set of input values then an
 * {@link UnsupportedSpecializationException} is thrown instead of invoking any specialization
 * method.
 * </p>
 * <p>
 * A specialization must have at least as many parameters as there are {@link NodeChild} annotations
 * declared for the enclosing operation node. These parameters are declared in the same order as the
 * {@link NodeChild} annotations (linear execution order). We call such parameters dynamic input
 * parameters. Every specialization that is declared within an operation must have an equal number
 * of dynamic input parameters.
 * </p>
 * <p>
 * The supported kind of input values for a specialization are declared using guards. A
 * specialization may provide declarative specifications for four kinds of guards:
 * <ul>
 * <li><b>Type guards</b> optimistically assume the type of an input value. A value that matches the
 * type is cast to its expected type automatically. Type guards are modeled using the parameter type
 * of the specialization method. Types used for type guards must be defined in the
 * {@link TypeSystem}. If the type of the parameter is {@link Object} then no type guard is used for
 * the dynamic input parameter.</li>
 *
 * <li><b>Expression guards</b> optimistically assume the return value of a user-defined expression
 * to be <code>true</code>. Expression guards are modeled using Java expressions that return a
 * <code>boolean</code> value. If the guard expression returns <code>false</code>, the
 * specialization is no longer applicable and the operation is re-specialized. Guard expressions are
 * declared using the {@link #guards()} attribute.</li>
 *
 * <li><b>Event guards</b> trigger re-specialization in case an exception is thrown in the
 * specialization body. The {@link #rewriteOn()} attribute can be used to declare a list of such
 * exceptions. Guards of this kind are useful to avoid calculating a value twice when it is used in
 * the guard and its specialization.</li>
 *
 * <li><b>Assumption guards</b> optimistically assume that the state of an {@link Assumption}
 * remains <code>true</code>. Assumptions can be assigned to specializations using the
 * {@link #assumptions()} attribute.</li>
 * </ul>
 * </p>
 * <p>
 * The enclosing {@link Node} of a specialization method must have at least one <code>public</code>
 * and non-<code>final</code> execute method. An execute method is a method that starts with
 * 'execute'. If all execute methods declare the first parameter type as {@link Frame},
 * {@link VirtualFrame} or {@link MaterializedFrame} then the same frame type can be used as
 * optional first parameter of the specialization. This parameter does not count to the number of
 * dynamic parameters.
 * </p>
 * <p>
 * A specialization method may declare multiple parameters annotated with {@link Cached}. Cached
 * parameters are initialized and stored once per specialization instantiation. For consistency
 * between specialization declarations cached parameters must be declared last in a specialization
 * method.
 * </p>
 * <p>
 * If the operation is re-specialized or if it is executed for the first time then all declared
 * specializations of the operation are tried in declaration order until the guards of the first
 * specialization accepts the current input values. The new specialization is then added to the
 * chain of current specialization instances which might consist of one (monomorph) or multiple
 * instances (polymorph). If an assumption of an instantiated specialization is violated then
 * re-specialization is triggered again.
 * </p>
 * <p>
 * With guards in combination with cached parameters it is possible that multiple instances of the
 * same specialization are created. The {@link #limit()} attribute can be used to limit the number
 * of instantiations per specialization.
 * </p>
 *
 * @see NodeChild
 * @see Fallback
 * @see Cached
 * @see TypeSystem
 * @see TypeSystemReference
 * @see UnsupportedSpecializationException
 * @since 0.8 or earlier
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Specialization {
    /**
     * References a specialization of a super class by its method name where this specialization is
     * inserted before. The declaration order of a specialization is not usable for nodes where
     * specializations are partly declared in the super class and partly declared in a derived
     * class. By default all specializations declared in the derived class are appended to those in
     * the super class. This attribute can be used to override the default behavior.
     *
     * @since 0.8 or earlier
     */
    String insertBefore() default "";

    /**
     * Declares an event guards that trigger re-specialization in case an exception is thrown in the
     * specialization body. This attribute can be used to declare a list of such exceptions. Guards
     * of this kind are useful to avoid calculating a value twice when it is used in the guard and
     * its specialization.
     * <p>
     * If an event guard exception is triggered then all instantiations of this specialization are
     * removed. If one of theses exceptions is thrown once then no further instantiations of this
     * specialization are going to be created for this node.
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>
     * &#064;Specialization(rewriteOn = ArithmeticException.class)
     * int doAddNoOverflow(int a, int b) {
     *     return Math.addExact(a, b);
     * }
     * &#064;Specialization
     * long doAddWithOverflow(int a, int b) {
     *     return a + b;
     * }
     * ...
     * Example executions:
     *   execute(Integer.MAX_VALUE - 1, 1) => doAddNoOverflow(Integer.MAX_VALUE - 1, 1)
     *   execute(Integer.MAX_VALUE, 1)     => doAddNoOverflow(Integer.MAX_VALUE, 1)
     *                                     => throws ArithmeticException
     *                                     => doAddWithOverflow(Integer.MAX_VALUE, 1)
     *   execute(Integer.MAX_VALUE - 1, 1) => doAddWithOverflow(Integer.MAX_VALUE - 1, 1)
     * </pre>
     *
     * In case of explicitly declared {@link UnexpectedResultException}s, the result from the
     * exception will be used. For all other exception types, the next available specialization will
     * be executed, so that the original specialization must ensure that no non-repeatable
     * side-effect is caused until the rewrite is triggered.
     * <p>
     * If a specialization declares an {@link UnexpectedResultException} which is
     * {@link Specialization#replaces() replaced} by another specialization with the same signature
     * and state, but without declared {@link UnexpectedResultException} then the specialization is
     * used as so-called boxing overload of the replacing specialization. Boxing overloads share the
     * same state and must be implemented in a way such that they perform exactly the same
     * operation, with the exception of the unexpected result. Boxing overloads are used as direct
     * replacements when code for boxing elimination is generated. They are only used if an execute
     * method with the return type of the boxing overload and an {@link UnexpectedResultException}
     * is declared in the node, or alternatively in the Bytecode DSL if the return type is
     * configured to be boxing eliminated. Boxing overloads share the state with its replacing
     * specialization, which makes them particularly useful to implement read nodes with boxing
     * elimination.
     * <p>
     * <b>Usage for boxing elimination:</b>
     *
     * <pre>
     * &#64;Specialization(guards = "arg == cachedArg", limit = "2",
     *                     rewriteOn = UnexpectedResultException.class)
     * int doInt(Object arg, &#64;Cached("arg") Object cachedArg)
     *                                                  throws UnexpectedResultException {
     *     if (cachedArg instanceof Integer i) {
     *         return 42;
     *     }
     *     throw new UnexpectedResultException(cachedArg);
     * }
     *
     * &#64;Specialization(guards = "arg == cachedArg", limit = "2", replaces = "doInt")
     * Object doGeneric(Object arg, &#64;Cached("arg") Object cachedArg) {
     *     return cachedArg;
     * }
     *
     * ...
     * Example executions:
     *   execute(42) => doGeneric(42, 42)
     *   executeInt(43) => doInt(43, 43)
     *   executeInt(42) => doInt(42, 42)
     *   // shared inline cache limit of 2 reached
     *   execute(44) => {@link UnsupportedSpecializationException}
     * </pre>
     *
     *
     * @see Math#addExact(int, int)
     * @since 0.8 or earlier
     */
    Class<? extends Throwable>[] rewriteOn() default {};

    /**
     * <p>
     * Declares other specializations of the same operation to be replaced by this specialization.
     * Other specializations are referenced using their unique method name. If this specialization
     * is instantiated then all replaced specialization instances are removed and never instantiated
     * again for this node instance. Therefore this specialization should handle strictly more
     * inputs than which were handled by the replaced specialization, otherwise the removal of the
     * replaced specialization will lead to unspecialized types of input values. The replaces
     * declaration is transitive for multiple involved specializations.
     * </p>
     * <b>Example usage:</b>
     *
     * <pre>
     * &#064;Specialization(guards = "b == 2")
     * void doDivPowerTwo(int a, int b) {
     *     return a >> 1;
     * }
     * &#064;Specialization(replaces ="doDivPowerTwo", guards = "b > 0")
     * void doDivPositive(int a, int b) {
     *     return a / b;
     * }
     * ...
     * Example executions with replaces="doDivPowerTwo":
     *   execute(4, 2) => doDivPowerTwo(4, 2)
     *   execute(9, 3) => doDivPositive(9, 3) // doDivPowerTwo instances get removed
     *   execute(4, 2) => doDivPositive(4, 2)
     * Same executions without replaces="doDivPowerTwo"
     *   execute(4, 2) => doDivPowerTwo(4, 2)
     *   execute(9, 3) => doDivPositive(9, 3)
     *   execute(4, 2) => doDivPowerTwo(4, 2)
     * </pre>
     *
     * </p>
     *
     * @see #guards()
     * @since 0.22
     */
    String[] replaces() default {};

    /**
     * <p>
     * Declares <code>boolean</code> expressions that define whether or not input values are
     * applicable to this specialization instance. Guard expressions must always return the same
     * result for each combination of the enclosing node instance and the bound input values.
     * </p>
     * <p>
     * If a guard expression does not bind any dynamic input parameters then the DSL, by default,
     * assumes that the result will not change for this node after specialization instantiation. In
     * other words the DSL assumes idempotence for this guard on the fast-path, by default. The
     * {@link Idempotent} and {@link NonIdempotent} annotations may be used to configure this
     * explicitly. The DSL will also emit warnings in case the use of such annotations is
     * recommended. If assertions are enabled (-ea), then the DSL will assert that the idempotence
     * property does hold at runtime.
     * </p>
     * <p>
     * Guard expressions are defined using a subset of Java. This subset includes field/parameter
     * accesses, function calls, type exact infix comparisons (==, !=, &lt;, &lt;=, &gt;, &gt;=),
     * logical negation (!), logical disjunction (||), null, true, false, and integer literals. The
     * return type of guard expressions must be <code>boolean</code>. Bound elements without
     * receivers are resolved using the following order:
     * <ol>
     * <li>Dynamic and cached parameters of the enclosing specialization.</li>
     * <li>Fields defined using {@link NodeField} for the enclosing node.</li>
     * <li>Non-private, static or virtual methods or fields of enclosing node.</li>
     * <li>Non-private, static or virtual methods or fields of super types of the enclosing node.
     * </li>
     * <li>Public and static methods or fields imported using {@link ImportStatic}.</li>
     * </ol>
     * </p>
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>
     * static boolean acceptOperand(int operand) {
     *     assert operand &lt;= 42;
     *     return (operand &amp; 1) == 1;
     * }
     *
     * &#64;Specialization(guards = {"operand &lt;= 42", "acceptOperand(operand)"})
     * void doSpecialization(int operand) {...}
     * </pre>
     *
     * </p>
     *
     * @see Cached
     * @see ImportStatic
     * @since 0.8 or earlier
     */
    String[] guards() default {};

    /**
     * <p>
     * Declares assumption guards that optimistically assume that the state of an {@link Assumption}
     * remains valid. Assumption expressions are cached once per specialization instantiation. If
     * one of the returned assumptions gets invalidated then the specialization instance is removed.
     * If the assumption expression returns an array of assumptions then all assumptions of the
     * array are checked. This is limited to one-dimensional arrays.
     * </p>
     * <p>
     * Assumption expressions are defined using a subset of Java. This subset includes
     * field/parameter accesses, function calls, type exact infix comparisons (==, !=, &lt;, &lt;=,
     * &gt;, &gt;=), logical negation (!), logical disjunction (||), null, true, false, and integer
     * literals. The return type of the expression must be {@link Assumption} or an array of
     * {@link Assumption} instances. Assumption expressions are not allowed to bind to dynamic
     * parameter values of the specialization. Bound elements without receivers are resolved using
     * the following order:
     * <ol>
     * <li>Cached parameters of the enclosing specialization.</li>
     * <li>Fields defined using {@link NodeField} for the enclosing node.</li>
     * <li>Non-private, static or virtual methods or fields of enclosing node.</li>
     * <li>Non-private, static or virtual methods or fields of super types of the enclosing node.
     * </li>
     * <li>Public and static methods or fields imported using {@link ImportStatic}.</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>
     * abstract static class DynamicObject() { abstract Shape getShape(); ... }
     * abstract static class Shape() { abstract Assumption getUnmodifiedAssuption(); ... }
     *
     * &#64;Specialization(guards = "operand.getShape() == cachedShape", assumptions = "cachedShape.getUnmodifiedAssumption()")
     * void doAssumeUnmodifiedShape(DynamicObject operand, @Cached("operand.getShape()") Shape cachedShape) {...}
     * </pre>
     *
     * </p>
     *
     * @see Cached
     * @see ImportStatic
     * @since 0.8 or earlier
     */
    String[] assumptions() default {};

    /**
     * <p>
     * Declares the expression that limits the number of specialization instantiations. The default
     * limit for specialization instantiations is defined as <code>"3"</code>. If the limit is
     * exceeded no more instantiations of the enclosing specialization method are created. Please
     * note that the existing specialization instantiations are <b>not</b> removed from the
     * specialization chain. You can use {@link #replaces()} to remove unnecessary specializations
     * instances.
     * </p>
     * <p>
     * The limit expression is defined using a subset of Java. This subset includes field/parameter
     * accesses, function calls, type exact infix comparisons (==, !=, &lt;, &lt;=, &gt;, &gt;=),
     * logical negation (!), logical disjunction (||), null, true, false, and integer literals. The
     * return type of the limit expression must be <code>int</code>. Limit expressions are not
     * allowed to bind to dynamic parameter values of the specialization. Bound elements without
     * receivers are resolved using the following order:
     * <ol>
     * <li>Cached parameters of the enclosing specialization.</li>
     * <li>Fields defined using {@link NodeField} for the enclosing node.</li>
     * <li>Non-private, static or virtual methods or fields of enclosing node.</li>
     * <li>Non-private, static or virtual methods or fields of super types of the enclosing node.
     * </li>
     * <li>Public and static methods or fields imported using {@link ImportStatic}.</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>
     * static int getCacheLimit() {
     *     return Integer.parseInt(System.getProperty("language.cacheLimit"));
     * }
     *
     * &#64;Specialization(guards = "operand == cachedOperand", limit = "getCacheLimit()")
     * void doCached(Object operand, @Cached("operand") Object cachedOperand) {...}
     * </pre>
     *
     * </p>
     *
     * @see #guards()
     * @see #replaces()
     * @see Cached
     * @see ImportStatic
     * @since 0.8 or earlier
     */
    String limit() default "";

    /**
     * Instructs the specialization to unroll a specialization with multiple instances. Unrolling
     * causes fields of the inline cache to be directly stored in the node instead of a chained
     * inline cache. At most 8 instances of a specialization can be unrolled to avoid code explosion
     * in the interpreter.
     * <p>
     * A common use-case for this feature is to unroll the first instance of an inline cache. It is
     * often the case that specializations with multiple instances are instantiated only once. By
     * unrolling the first instance we can optimize for this common situation which may lead to
     * footprint and interpreter performance improvements.
     * <p>
     * This feature is prone to cause inefficiencies if used too aggressively. Extra care should be
     * taken, e.g. the generated code should be inspected and profiled to verify that the new code
     * is better than the previous version.
     * <p>
     * Consider the following example:
     *
     * <pre>
     * class MyNode extends Node {
     *
     *     static int limit = 2;
     *
     *     abstract int execute(int value);
     *
     *     &#64;Specialization(guards = "value == cachedValue", limit = "limit", unroll = 1)
     *     int doDefault(int value,
     *                     &#64;Cached("value") int cachedValue) {
     *         return value;
     *     }
     *
     * }
     * </pre>
     *
     * In this example we unroll the first instance of an inline cache on <code>int</code> values.
     * This is equivalent to manually specifying the following specializations:
     *
     * <pre>
     * class MyUnrollNode extends Node {
     *
     *     static int limit = 2;
     *
     *     abstract int execute(int value);
     *
     *     &#64;Specialization(guards = "value == cachedValue", limit = "1")
     *     int doUnrolled0(int value,
     *                     &#64;Cached("value") int cachedValue) {
     *         return value;
     *     }
     *
     *     &#64;Specialization(guards = "value == cachedValue", limit = "limit - 1")
     *     int doDefault(int value,
     *                     &#64;Cached("value") int cachedValue) {
     *         return value;
     *     }
     * }
     *
     * </pre>
     *
     *
     * @since 23.0
     */
    int unroll() default 0;

    /**
     * Excludes this specialization from use with {@link GenerateUncached}.
     * <p>
     * By default every specialization is included for {@link GenerateUncached}, except
     * specializations that require a {@link #limit() limit} and are {@link #replaces() replaced},
     * those are excluded by default. By setting {@link Specialization#excludeForUncached()}
     * explicitly the default behavior can be overridden, e.g. to include or exclude a
     * specialization for {@link GenerateUncached}.
     * <p>
     * Mark a specialization as excluded only when it is {@link #replaces() replaced} by a more
     * generic one; otherwise cached and uncached nodes may diverge semantically. Any specialization
     * may be excluded from uncached. It is considered an error if all specializations are excluded
     * from uncached.
     *
     * @since 25.0
     */
    boolean excludeForUncached() default false;

}
