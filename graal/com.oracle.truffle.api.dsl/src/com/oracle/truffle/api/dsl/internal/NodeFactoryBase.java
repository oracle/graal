/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.internal;

import java.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * This is NOT public API. Do not use directly. This code may change without notice.
 */
public abstract class NodeFactoryBase<T> implements NodeFactory<T> {

    private final Class<T> nodeClass;
    private final Class<?>[][] nodeSignatures;
    private final Class<? extends Node>[] executionSignatures;

    @SuppressWarnings("unchecked")
    public NodeFactoryBase(Class<T> nodeClass, Class<?>[] executionSignatures, Class<?>[][] nodeSignatures) {
        this.nodeClass = nodeClass;
        this.nodeSignatures = nodeSignatures;
        this.executionSignatures = (Class<? extends Node>[]) executionSignatures;
    }

    public abstract T createNode(Object... arguments);

    public final Class<T> getNodeClass() {
        return nodeClass;
    }

    public final List<List<Class<?>>> getNodeSignatures() {
        List<List<Class<?>>> signatures = new ArrayList<>();
        for (int i = 0; i < nodeSignatures.length; i++) {
            signatures.add(Arrays.asList(nodeSignatures[i]));
        }
        return signatures;
    }

    public final List<Class<? extends Node>> getExecutionSignature() {
        return Arrays.asList(executionSignatures);
    }

}
