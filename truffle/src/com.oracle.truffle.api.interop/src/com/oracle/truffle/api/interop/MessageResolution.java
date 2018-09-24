/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Annotation to put on your node to simplify handling of incoming inter-operability {@link Message
 * messages}.
 *
 * This class contains the node implementations for all messages that the receiver object should
 * resolve. Use {@link Resolve} to annotate {@link Node} implementations of messages. Those messages
 * for which no {@link Resolve} is provided are either left unsupported, or in case of
 * <code>HAS/IS</code> messages they get a default boolean value depending on presence of
 * corresponding messages. E.g. <code>HAS_SIZE</code> is true if and only if <code>GET_SIZE</code>
 * is provided, <code>IS_EXECUTABLE</code> is true if and only if <code>EXECUTE</code> is provided,
 * etc. If objects support some messages conditionally, they should provide their own implementation
 * of <code>HAS/IS</code> messages. Elements in the super class that are annotated with
 * {@link Resolve} will be ignored. For example:
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
