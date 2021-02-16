/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Generates an uncached version of a node with specializations. Uncached versions of nodes don't
 * specialize and don't use any profiling state. This allows to store them statically and to use
 * them whenever no specialization/profiling is desired. The uncached version of the node is
 * accessible using a static method called <code>getUncached()</code> on the generated node.
 * GenerateUncached is inherited to subclasses if {@link #inherit()} is set to <code>true</code>
 * (default <code>false</code>).
 * <p>
 * The generated code for the uncached version is based on the specialization closure. The
 * specialization closure only includes specializations that were are not replaced by others. This,
 * for example, automatically excludes inline caches from the closure. Uses of the {@link Cached}
 * annotation will automatically use {@linkplain Cached#uncached() getUncached} instead of a cached
 * version to initialize the cache.
 * <p>
 * A node subclass must fullfill the following requirements in order to be uncachable:
 * <ul>
 * <li>At least one specialization and one execute method must be specified.
 * <li>The node has no instance fields.
 * <li>All {@link Cached} parameters provide valid {@linkplain Cached#uncached() uncached}
 * initializers.
 * <li>All specializations of the closure must not use {@linkplain Specialization#rewriteOn()
 * rewriteOn} attribute.
 * <li>All guards/cache/limit expressions must not bind the node receiver.
 * </ul>
 * If any of these requirements are violated then an error will be shown. If node uses the
 * {@link NodeChild} or {@link NodeField} annotations then they will return constant
 * <code>null</code> or the primitive equivalent for the uncached node.
 * <p>
 * <b>Example:</b>
 *
 * <pre>
 * &#64;GenerateUncached
 * abstract static class UncachableNode extends Node {
 *
 *     abstract Object execute(Object arg);
 *
 *     &#64;Specialization(guards = "v == cachedV")
 *     static Object doCached(int v, &#64;Cached("v") int cachedV) {
 *         // do cached
 *     }
 *
 *     &#64;Specialization(replaces = "doCached")
 *     static String doGeneric(int v) {
 *         // do uncached
 *     }
 *
 * }
 * </pre>
 *
 * This node produces the following uncached version of the execute method:
 *
 * <pre>
 * &#64;Override
 * Object execute(Object arg0Value) {
 *     if (arg0Value instanceof Integer) {
 *         int arg0Value_ = (int) arg0Value;
 *         return UncachableNode.doGeneric(arg0Value_);
 *     }
 *     throw new UnsupportedSpecializationException(this, new Node[]{null}, arg0Value);
 * }
 * </pre>
 *
 * @see Cached
 * @since 19.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateUncached {

    /**
     * Inherits the semantics of the annotation to subclasses.
     *
     * @since 19.1.0
     */
    boolean inherit() default false;

}
