/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.Node;

/**
 * Allows to access the current language execution context in specializations or exported messages.
 * The cached context annotation requires to specify a concrete {@link TruffleLanguage} subclass in
 * order to identify the language uniquely. The parameter type must be the language context type of
 * the language or a {@link ContextReference} that provides it. The latter parameter allows to
 * lookup the context lazily, e.g. in a conditional branch. Using this annotation always allows to
 * generate {@link GenerateUncached uncached} versions of the node.
 * <p>
 * This annotation can be used in two different ways:
 * <ol>
 * <li>Direct context access:</li>
 *
 * <pre>
 * &#64;GenerateUncached
 * abstract static class ExampleNode extends Node {
 *
 *     abstract Object execute(Object argument);
 *
 *     &#64;Specialization
 *     static int doInt(int value,
 *                     &#64;CachedContext(MyLanguage.class) MyContext context) {
 *         // use context
 *         return value;
 *     }
 *
 * }
 * </pre>
 *
 * <li>Conditional context access:</li>
 *
 * <pre>
 * &#64;GenerateUncached
 * abstract class ExampleNode extends Node {
 *
 *     abstract Object execute(Object argument);
 *
 *     &#64;Specialization
 *     static int doInt(int value,
 *                     &#64;CachedContext(MyLanguage.class) ContextReference<MyContext> ref) {
 *         if (value == 42) {
 *             // use context conditionally
 *             MyContext context = ref.get();
 *
 *         }
 *         return value;
 *     }
 * }
 * </pre>
 * </ol>
 * <p>
 * The generated code uses the {@link Node#lookupContextReference(Class)} method to implement this
 * feature. This method may also be used manually.
 *
 * @see Cached @Cached for more information on using this annotation.
 * @see CachedLanguage @CachedLanguage to access the language instance.
 * @since 19.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER})
public @interface CachedContext {

    /**
     * Specifies the language the context should be looked up for. Must be the exact language class
     * annotated by {@link Registration}.
     *
     * @since 19.0
     */
    @SuppressWarnings("rawtypes")
    Class<? extends TruffleLanguage> value();

}
