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
package com.oracle.truffle.api.instrumentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Generates a default wrapper subclass of an annotated {@link InstrumentableNode} subclass. The
 * generated subclass is has the same class name as the original class name plus the 'Wrapper'
 * suffix. The generated class has default package visibility. All non-final and non-private methods
 * starting with execute are overridden by the generated wrapper. The generated overrides notifies
 * execution events as required by {@link ProbeNode probes}. Other abstract methods are directly
 * delegated to the wrapped node. No other methods are overridden by the generated wrapper. At least
 * one method starting with execute must be non-private and non-final. Every execute method must
 * have {@link VirtualFrame} as the first declared parameter.
 * <p>
 * <b>Example Usage:</b>
 *
 * <pre>
 * &#64;GenerateWrapper
 * abstract class ExpressionNode extends Node implements InstrumentableNode {
 *
 *     abstract Object execute(VirtualFrame frame);
 *
 *     &#64;Override
 *     public WrapperNode createWrapper(ProbeNode probeNode) {
 *         return new ExpressionNodeWrapper(this, probeNode);
 *     }
 * }
 * </pre>
 *
 * @see InstrumentableNode
 * @see ProbeNode
 * @since 0.32
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GenerateWrapper {

}