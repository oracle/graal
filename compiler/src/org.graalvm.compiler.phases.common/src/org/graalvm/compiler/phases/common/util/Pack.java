/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.util;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;

public final class Pack<T extends Node> implements Iterable<T> {
   private final List<T> elements;

   private Pack(List<T> elements) {
       this.elements = elements;
   }

   public List<T> getElements() {
       return elements;
   }

   public T getFirst() {
       return elements.get(0);
   }

   public T getLast() {
       return elements.get(elements.size() - 1);
   }

   @Override
   public boolean equals(Object o) {
       if (this == o) {
           return true;
       }
       if (o == null || getClass() != o.getClass()) {
           return false;
       }
       Pack<?> pack = (Pack<?>) o;
       return elements.equals(pack.elements);
   }

   @Override
   public int hashCode() {
       return Objects.hash(elements);
   }

   @Override
   public String toString() {
       return '<' + elements.toString() + '>';
   }

   // Iterable<T>

   @Override
   public Iterator<T> iterator() {
       return elements.iterator();
   }

   @Override
   public Spliterator<T> spliterator() {
       return elements.spliterator();
   }

   @Override
   public void forEach(Consumer<? super T> action) {
       elements.forEach(action);
   }

   // Builders
   public static <T extends Node> Pack<T> create(Pair<T, T> pair) {
       return new Pack<T>(Stream.of(pair.getLeft(), pair.getRight()).collect(Collectors.toList()));
   }

   public static <T extends Node> Pack<T> combine(Pack<T> left, Pack<T> right) {
       T rightLeft = right.getFirst();
       return new Pack<>(Stream.concat(
               left.elements.stream().filter(x -> !x.equals(rightLeft)),
               right.elements.stream()).collect(Collectors.toList()));
   }
}
