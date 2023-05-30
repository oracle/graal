/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * Denotes port of a HotSpot stub. This information will be parsed by
 * {@code org.graalvm.compiler.lir.processor.StubPortProcessor}.
 */
@Target(ElementType.TYPE)
@Repeatable(StubPorts.class)
public @interface StubPort {
    /**
     * Relevant path of source code file containing the ported stub.
     */
    String path();

    /**
     * Starting line of the ported stub.
     */
    int lineStart();

    /**
     * Ending line of the ported stub.
     */
    int lineEnd();

    /**
     * Version of the original source code when the port was created or last updated.
     */
    String commit();

    /**
     * Digest of the source code that was ported.
     */
    String sha1();

}
