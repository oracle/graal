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
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VectorPrimitiveStamp;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

import static org.graalvm.compiler.phases.common.IsomorphicPackingPhase.Util.getStamp;

public final class Pack implements Iterable<ValueNode> {
   private final List<ValueNode> elements;
   private VectorPrimitiveStamp stamp = null;

   private Pack(List<ValueNode> elements) {
       this.elements = elements;
   }

   public List<ValueNode> getElements() {
       return elements;
   }

   public ValueNode getFirst() {
       return elements.get(0);
   }

   public ValueNode getLast() {
       return elements.get(elements.size() - 1);
   }

   public VectorPrimitiveStamp stamp(NodeView view) {
       if (stamp == null) {
           stamp = elements.stream().reduce(getStamp(elements.get(0), view).unrestricted(),
                   (stamp, valueNode) -> stamp.meet(getStamp(valueNode, view).unrestricted()),
                   Stamp::meet).asVector(elements.size());
       }

       return stamp;
   }

   @Override
   public boolean equals(Object o) {
       if (this == o) {
           return true;
       }
       if (o == null || getClass() != o.getClass()) {
           return false;
       }
       Pack pack = (Pack) o;
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
   public Iterator<ValueNode> iterator() {
       return elements.iterator();
   }

   @Override
   public Spliterator<ValueNode> spliterator() {
       return elements.spliterator();
   }

   @Override
   public void forEach(Consumer<? super ValueNode> action) {
       elements.forEach(action);
   }

   // Builders
   public static <T extends ValueNode> Pack create(Pair<T, T> pair) {
       return new Pack(Stream.of(pair.getLeft(), pair.getRight()).collect(Collectors.toList()));
   }

   public static Pack combine(Pack left, Pack right) {
       ValueNode rightLeft = right.getFirst();
       return new Pack(Stream.concat(
               left.elements.stream().filter(x -> !x.equals(rightLeft)),
               right.elements.stream()).collect(Collectors.toList()));
   }
}
