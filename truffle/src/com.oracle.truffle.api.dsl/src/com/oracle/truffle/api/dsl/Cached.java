/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;

// Workaround for Eclipse formatter behaving different when running on JDK 9.
// @formatter:off
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
 * >, >=), logical negation (!), logical disjunction (||), null, true, false, and integer literals.
 * The return type of the initializer expression must be assignable to the parameter type. If the
 * annotated parameter type is derived from {@link Node} then the {@link Node} instance is allowed
 * to use the {@link Node#replace(Node)} method to replace itself. Bound elements without receivers
 * are resolved using the following order:
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
 * {@link NodeChild @NodeChild}("operand")
 * abstract TestNode extends Node {
 *   abstract void execute(Object operandValue);
 *   // ... example here ...
 * }
 * </pre>
 *
 * <ol>
 * <li>This example defines one dynamic and one cached parameter. The operand parameter is
 * representing the dynamic value of the operand while the cachedOperand is initialized once at
 * first execution of the specialization (specialization instantiation time).
 *
 * <pre>
 *  &#064;Specialization
 *  void doCached(int operand, {@code @Cached}(&quot;operand&quot;) int cachedOperand) {
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
 * <li>We extend the previous example by a guard for the cachedOperand value to be equal to the
 * dynamic operand value. This specifies that the specialization is instantiated for each individual
 * operand value that is provided. There are a lot of individual <code>int</code> values and for
 * each individual <code>int</code> value a new specialization would get instantiated. The
 * {@link Specialization#limit()} property defines a limit for the number of specializations that
 * can get instantiated. If the specialization instantiation limit is reached then no further
 * specializations are instantiated. Like for other specializations if there are no more
 * specializations defined an {@link UnsupportedSpecializationException} is thrown. The default
 * specialization instantiation limit is <code>3</code>.
 *
 * <pre>
 * &#064;Specialization(guards = &quot;operand == cachedOperand&quot;)
 * void doCached(int operand, {@code @Cached}(&quot;operand&quot;) int cachedOperand) {
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
 * <li>To handle the limit overflow we extend our example by an additional specialization named
 * <code>doNormal</code>. This specialization has the same type restrictions but does not have local
 * state nor the operand identity guard. It is also declared after <code>doCached</code> therefore
 * it is only instantiated if the limit of the <code>doCached</code> specialization has been
 * reached. In other words <code>doNormal</code> is more generic than <code>doCached</code> . The
 * <code>doNormal</code> specialization uses <code>replaces=&quot;doCached&quot;</code> to specify
 * that all instantiations of <code>doCached</code> get removed if <code>doNormal</code> is
 * instantiated. Alternatively if the <code>replaces</code> relation is omitted then all
 * <code>doCached</code> instances remain but no new instances are created.
 *
 * <pre>
 * &#064;Specialization(guards = &quot;operand == cachedOperand&quot;)
 * void doCached(int operand, {@code @Cached}(&quot;operand&quot;) int cachedOperand) {
 *    CompilerAsserts.compilationConstant(cachedOperand);
 *    ...
 * }
 *
 * &#064;Specialization(replaces = &quot;doCached&quot;)
 * void doNormal(int operand) {...}
 *
 * Example executions with replaces = &quot;doCached&quot;:
 * execute(0) => doCached(0, 0) // new instantiation, cachedOperand is bound to 0
 * execute(1) => doCached(1, 1) // new instantiation, cachedOperand is bound to 1
 * execute(1) => doCached(1, 1)
 * execute(2) => doCached(2, 2) // new instantiation, cachedOperand is bound to 2
 * execute(3) => doNormal(3)    // new instantiation of doNormal due to limit overflow; doCached gets removed.
 * execute(1) => doNormal(1)
 *
 * Example executions without replaces = &quot;doCached&quot;:
 * execute(0) => doCached(0, 0) // new instantiation, cachedOperand is bound to 0
 * execute(1) => doCached(1, 1) // new instantiation, cachedOperand is bound to 1
 * execute(1) => doCached(1, 1)
 * execute(2) => doCached(2, 2) // new instantiation, cachedOperand is bound to 2
 * execute(3) => doNormal(3)    // new instantiation of doNormal due to limit overflow
 * execute(1) => doCached(1, 1)
 *
 * </pre>
 *
 * </li>
 * <li>This next example shows how methods from the enclosing node can be used to initialize cached
 * parameters. Please note that the visibility of transformLocal must not be <code>private</code>.
 *
 * <pre>
 * &#064;Specialization
 * void s(int operand, {@code @Cached}(&quot;transformLocal(operand)&quot;) int cachedOperand) {
 * }
 *
 * int transformLocal(int operand) {
 *     return operand & 0x42;
 * }
 *
 * </li>
 * </pre>
 *
 * <li>The <code>new</code> keyword can be used to initialize a cached parameter using a constructor
 * of the parameter type.
 *
 * <pre>
 * &#064;Specialization
 * void s(Object operand, {@code @Cached}(&quot;new()&quot;) OtherNode someNode) {
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
 * <li>Java types without public constructor but with a static factory methods can be initialized by
 * just referencing its static factory method and its parameters. In this case
 * {@link com.oracle.truffle.api.profiles.BranchProfile#create()} is used to instantiate the
 * {@link com.oracle.truffle.api.profiles.BranchProfile} instance.
 *
 * <pre>
 * &#064;Specialization
 * void s(int operand, {@code @Cached}(&quot;create()&quot;) BranchProfile profile) {
 * }
 * </pre>
 *
 * </li>
 * </ol>
 *
 * @see Specialization#guards()
 * @see Specialization#replaces()
 * @see Specialization#limit()
 * @see ImportStatic
 * @see CachedContext @CachedContext to access the current context.
 * @see CachedLanguage @CachedLanguage to access the current Truffle language.
 * @since 0.8 or earlier
 */
// @formatter:on
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER})
public @interface Cached {

    /**
     * Defines the initializer expression of the cached parameter value.
     *
     * @see Cached
     * @since 0.8 or earlier
     */
    String value() default "create($parameters)";

    /**
     * Defines the initializer that is used for {@link GenerateUncached uncached} nodes or uncached
     * versions of exported library messages.
     *
     * @see GenerateUncached
     * @since 19.0
     */
    String uncached() default "getUncached($parameters)";

    /**
     * Specifies the number of array dimensions to be marked as {@link CompilationFinal compilation
     * final}. This value must be specified for all array-typed cached values except {@link Node
     * node} arrays and must be left unspecified in other cases where it has no meaning.
     *
     * The allowed range is from 0 to the number of declared array dimensions (inclusive).
     * Specifically, a {@code dimensions} value of 0 marks only the reference to the (outermost)
     * array as final but not its elements, a value of 1 marks the outermost array and all its
     * elements as final but not the elements of any nested arrays.
     *
     * If not specified and the cached value type is an array type then this will cause a warning
     * and in later releases an error.
     *
     * @since 0.26
     * @see CompilationFinal#dimensions()
     */
    int dimensions() default -1;

    /**
     * Allows the {@link #value()} to be used for {@link #uncached()}. This is useful if the
     * expression is the same for {@link #value()} and {@link #uncached()}. By setting
     * {@link #allowUncached()} to <code>true</code> it is not necessary to repeat the
     * {@link #value()} expression in the {@link #uncached()} expression. This flag cannot be set in
     * combination with {@link #uncached()}.
     *
     * @since 19.0
     */
    boolean allowUncached() default false;

    /**
     * Specifies the bindings used for the $parameters variable in cached or uncached initializers.
     *
     * @since 19.0
     */
    String[] parameters() default {};

    /**
     * If set to <code>true</code> then weak references will be used to refer to this cached value
     * in the generated node. The default value is <code>false</code>. The weak cached parameter is
     * guaranteed to not become <code>null</code> in guards or specialization method invocations. If
     * a weak cached parameter gets collected by the GC, then any compiled code remain unaffected
     * and the specialization instance will not be removed. Specializations with collected cached
     * references continue to count to the specialization limit. This is necessary to provide an
     * upper bound for the number of invalidations that may happen for this specialization.
     * <p>
     * A weak cached parameter implicitly adds a <code>weakRef.get() != null</code> guard that is
     * invoked before the cached value is referenced for the first time. This means that
     * specializations which previously did not result in fall-through behavior may now
     * fall-through. This is important if used in combination with {@link Fallback}. Weak cached
     * parameters that are used as part of {@link GenerateUncached uncached} nodes, execute the
     * cached initializer for each execution and therefore implicitly do not use a weak reference.
     * <p>
     * Example usage:
     *
     * <pre>
     * &#64;GenerateUncached
     * abstract class WeakInlineCacheNode extends Node {
     *
     *     abstract Object execute(Object arg0);
     *
     *     &#64;Specialization(guards = "cachedArg.equals(arg)", limit = "3")
     *     Object s0(String arg,
     *                     &#64;Cached(value = "arg", weak = true) String cachedArg) {
     *         assertNotNull(cachedStorage);
     *         return arg;
     *     }
     * }
     * </pre>
     *
     * @see com.oracle.truffle.api.utilities.TruffleWeakReference
     * @since 20.2
     */
    boolean weak() default false;

    /**
     * Specifies whether the cached parameter values of type {@link NodeInterface} should be adopted
     * as its child by the current node. The default value is <code>true</code>, therefore all
     * cached values of type {@link NodeInterface} and arrays of the same type are adopted. If the
     * value is set to <code>false</code>, then no adoption is performed. It is useful to set adopt
     * to <code>false</code> when nodes need to be referenced more than once in the AST.
     * <p>
     * If the type of the field is an {@link NodeInterface} array and adopt is set to
     * <code>false</code>, then the compilation final {@link Cached#dimensions() dimensions}
     * attribute needs to be specified explicitly.
     *
     * @since 20.2
     */
    boolean adopt() default true;

    /**
     * Allows sharing between multiple Cached parameters between multiple specializations or
     * exported library messages. If no sharing is desired then the {@link Cached cached} parameter
     * can be annotated with {@link Exclusive exclusive}. The DSL will indicate sharing
     * opportunities to the user by showing a warning.
     *
     * @see Exclusive
     * @since 19.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER})
    public @interface Shared {

        /**
         * Specifies the sharing group of the shared cached element.
         *
         * @since 19.0
         */
        String value();

    }

    /**
     * Disallows any sharing with other cached parameters. The DSL will indicate sharing
     * opportunities to the user by showing a warning.
     *
     * @since 19.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface Exclusive {
    }

}
