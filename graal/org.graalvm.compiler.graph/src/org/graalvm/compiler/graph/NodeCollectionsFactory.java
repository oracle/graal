/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.CollectionsFactory;

public class NodeCollectionsFactory {

    /**
     * @see CollectionsFactory#newSet()
     */
    public static <E extends Node> Set<E> newSet() {
        return CollectionsFactory.newSet();
    }

    /**
     * @see #newSet()
     */
    public static <E extends Node> Set<E> newSet(Collection<? extends E> c) {
        return CollectionsFactory.newSet(c);
    }

    public static <K extends Node, V> Map<K, V> newMap() {
        // Node.equals() and Node.hashCode() are final and are implemented
        // purely in terms of identity so HashMap and IdentityHashMap with
        // Node's as keys will behave the same. We choose to use the latter
        // due to its lighter memory footprint.
        return newIdentityMap();
    }

    public static <K extends Node, V> Map<K, V> newMap(Map<K, V> m) {
        // Node.equals() and Node.hashCode() are final and are implemented
        // purely in terms of identity so HashMap and IdentityHashMap with
        // Node's as keys will behave the same. We choose to use the latter
        // due to its lighter memory footprint.
        return newIdentityMap(m);
    }

    public static <K extends Node, V> Map<K, V> newMap(int expectedMaxSize) {
        // Node.equals() and Node.hashCode() are final and are implemented
        // purely in terms of identity so HashMap and IdentityHashMap with
        // Node's as keys will behave the same. We choose to use the latter
        // due to its lighter memory footprint.
        return newIdentityMap(expectedMaxSize);
    }

    public static <K extends Node, V> Map<K, V> newIdentityMap() {
        return CollectionsFactory.newIdentityMap();
    }

    public static <K extends Node, V> Map<K, V> newIdentityMap(Map<K, V> m) {
        return CollectionsFactory.newIdentityMap(m);
    }

    public static <K extends Node, V> Map<K, V> newIdentityMap(int expectedMaxSize) {
        return CollectionsFactory.newIdentityMap(expectedMaxSize);
    }
}
