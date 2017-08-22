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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to obtain fine grain control over behavior of
 * {@link JavaInterop#asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject)}
 * wrapper interfaces. The interface created by
 * {@link JavaInterop#asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject)}
 * method implements its methods by sending {@link Message messsages} to {@link TruffleObject}
 * provided at the time of construction. There is a default sequence of operations for each method,
 * which is good enough to read fields or invoke methods. However the
 * {@link com.oracle.truffle.api.interop Interop API} is far richer and supports additional
 * {@link Message messages} (not only the well known ones, but also arbitrary custom ones). To
 * control which {@link Message} is sent one can annotate each method by this annotation.
 * <h5>Writing to a field</h5> For example to write to field x of a JSON object:
 * 
 * <pre>
 * var obj = { 'x' : 5 }
 * </pre>
 * 
 * one can define the appropriate wrapper interface as:
 * 
 * <pre>
 * <b>interface</b> ObjInterop {
 *   {@link MethodMessage @MethodMessage}(message = <em>"WRITE"</em>)
 *   <b>void</b> x(int value);
 * }
 * </pre>
 * 
 * Then one can change the value of field <em>x</em> in <em>obj</em> from Java by calling:
 * 
 * <pre>
 * {@link JavaInterop#asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject) JavaInterop.asJavaObject}(ObjInterop.<b>class</b>, obj).x(10);
 * </pre>
 * 
 * the value of the <em>x</em> field is going to be <em>10</em> then.
 *
 * <h5>Checking for Null</h5>
 *
 * {@link JavaInteropSnippets#isNullValue}
 * 
 * @since 0.9
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodMessage {
    /**
     * Identification of the {@link Message message} to send. Well known messages include fields of
     * the {@link Message} class (e.g. <em>"READ"</em>, <em>"WRITE"</em>, <em>"UNBOX"</em>,
     * <em>IS_NULL</em>) or slightly mangled names of {@link Message} class factory methods (
     * <em>EXECUTE</em>, <em>INVOKE</em>). For more details on the string encoding of message names
     * see {@link Message#valueOf(java.lang.String)} method.
     * 
     * @return string identification of an inter-operability message
     * @see Message#valueOf(java.lang.String)
     * @since 0.9
     */
    String message();
}
