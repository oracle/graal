package org.graalvm.compiler.phases.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;

public class Pack<T extends Node> implements Iterable<T> {
   private List<T> elements;

   private Pack(List<T> elements) {
       this.elements = elements;
   }

   public List<T> getElements() {
       return elements;
   }

   @Override
   public boolean equals(Object o) {
       if (this == o) return true;
       if (o == null || getClass() != o.getClass()) return false;
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

   public static <T extends Node> Pack<T> create(List<T> elements) {
       if (elements.size() < 2)
           throw new IllegalArgumentException("cannot construct pack consisting of single element");

       return new Pack<>(elements);
   }

}
