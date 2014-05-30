/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.graph.util;

import java.util.*;

import com.oracle.graal.api.collections.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.graph.*;

/**
 * Static methods for accessing the methods in the installed {@link GraalRuntime}'s
 * {@link CollectionsProvider} and {@link NodeCollectionsProvider}.
 */
public class CollectionsAccess {

    private static final NodeCollectionsProvider provider = Graal.getRequiredCapability(NodeCollectionsProvider.class);

    /**
     * @see CollectionsProvider#newIdentityMap()
     */
    public static <K, V> Map<K, V> newIdentityMap() {
        return provider.newIdentityMap();
    }

    /**
     * @see CollectionsProvider#newIdentityMap()
     */
    public static <K, V> Map<K, V> newIdentityMap(int expectedMaxSize) {
        return provider.newIdentityMap(expectedMaxSize);
    }

    /**
     * @see CollectionsProvider#newIdentityMap(Map)
     */
    public static <K, V> Map<K, V> newIdentityMap(Map<K, V> initFrom) {
        return provider.newIdentityMap(initFrom);
    }

    /**
     * @see NodeCollectionsProvider#newNodeIdentitySet()
     */
    public static <E extends Node> Set<E> newNodeIdentitySet() {
        return provider.newNodeIdentitySet();
    }

    /**
     * @see NodeCollectionsProvider#newNodeIdentityMap()
     */
    public static <K extends Node, V> Map<K, V> newNodeIdentityMap() {
        return provider.newNodeIdentityMap();
    }

    /**
     * @see NodeCollectionsProvider#newNodeIdentityMap(int)
     */
    public static <K extends Node, V> Map<K, V> newNodeIdentityMap(int expectedMaxSize) {
        return provider.newNodeIdentityMap(expectedMaxSize);
    }

    /**
     * @see NodeCollectionsProvider#newNodeIdentityMap(Map)
     */
    public static <K extends Node, V> Map<K, V> newNodeIdentityMap(Map<K, V> initFrom) {
        return provider.newNodeIdentityMap(initFrom);
    }

    /**
     * Creates an identity set.
     */
    public static <E> Set<E> newIdentitySet() {
        return provider.newIdentitySet();
    }
}
