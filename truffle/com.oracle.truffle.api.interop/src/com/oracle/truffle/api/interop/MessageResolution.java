/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.TruffleLanguage;

/**
 * Annotation to put on your node to simplify handling of incoming inter-operability {@link Message
 * messages}.
 *
 * This class contains the node implementations for all messages that the receiver object should
 * resolve. Elements in the super class that are annotated with {@link Resolve} will be ignored. For
 * example:
 *
 * {@link com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObjectMR}
 *
 * The receiver object needs to implement a static method <code>isInstance</code>, which checks if a
 * given foreign object is an instance of the given receiver type and can therefore be accessed by
 * this node. For example:
 *
 * {@link com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObject#isInstanceCheck}
 *
 * Alternatively, one can also define a language check node (see {@link CanResolve}.
 *
 * From this class a {@link ForeignAccess} will be generated. The receiver object can then return a
 * singleton instance of this access. For example: <br>
 *
 * {@link com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObject#getForeignAccessMethod}
 *
 * @since 0.13
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MessageResolution {

    /**
     * The receiver object class that this message implementation belongs to.
     *
     * An annotation processor generates a {@link ForeignAccess} class, which the
     * {@link TruffleObject} can use to implement {@link TruffleObject#getForeignAccess()}.
     *
     * @return class of the receiver object
     *
     * @since 0.13
     */
    Class<?> receiverType();

    /**
     * The language the message implementation belongs to.
     *
     * @return class of the language object
     *
     * @since 0.13
     * @deprecated in 0.25 without replacement
     */
    @Deprecated
    Class<?> language() default TruffleLanguage.class;

}
