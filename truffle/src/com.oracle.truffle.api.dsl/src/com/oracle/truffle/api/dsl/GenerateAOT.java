/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Allows nodes with specializations to be prepared for AOT. The annotation is automatically
 * inherited by all subclasses. Specializations of annotated nodes will be validated for their
 * suitability for AOT preparation. By default all specializations that are not
 * {@link Specialization#replaces() replaced} by an AOT capable specializations are included for AOT
 * validation and preparation. If a specialization is not suitable a compilation error will be
 * shown.
 * <p>
 * The following properties make a specialization incapable of being used for AOT preparation:
 * <ul>
 * <li>Dynamic parameters bound in cached initializers. At AOT preparation time no dynamic
 * parameters are available, therefore the the caches not be initialized. Values read from the node
 * instance are supported.
 * <li>If a Truffle library is used that is automatically dispatched or where the expression
 * initializer is bound to a dynamic parameter value.
 * <li>If a cached node is created that does not itself support {@link GenerateAOT}.
 * </ul>
 *
 * Additionally, to resolve compilation errors related to AOT the following resolutions may be
 * applied:
 * <ul>
 * <li>Exclude this specialization from AOT with {@link Exclude}, if it is acceptable to deoptimize
 * for this specialization in AOT compiled code.
 * <li>Configure the specialization to be {@link Specialization#replaces()} with a more generic
 * specialization.
 * </ul>
 * <p>
 * After a node generates code for AOT preparation {@link AOTSupport#prepareForAOT(RootNode)} may be
 * used to prepare a root node in {@link RootNode#prepareForAOT}.
 * <p>
 * This annotation can also be used for classes annotated with Truffle library declarations and
 * exports. The same rules, as described here, for specializations declared in exported libraries
 * apply. Multiple AOT exports may be specified for each library, but there must be at least one,
 * otherwise an {@link IllegalStateException} is thrown at runtime when AOT is prepared.
 *
 * <p>
 * See also the <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/AOT.md">usage
 * tutorial</a> on the website.
 *
 * @see RootNode#prepareForAOT
 * @since 21.1
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateAOT {

    /**
     * Excludes the annotated {@link Specialization} from AOT preparation.
     *
     * @since 21.1
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface Exclude {
    }

    /**
     * Implemented by generated code. Do not reference directly
     *
     * @since 21.1
     */
    public interface Provider {

        /**
         * Called and implemented by framework code. Do not use directly.
         *
         * @since 21.1
         */
        void prepareForAOT(TruffleLanguage<?> language, RootNode root);

    }

}
