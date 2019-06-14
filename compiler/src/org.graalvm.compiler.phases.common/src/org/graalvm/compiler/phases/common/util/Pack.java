package org.graalvm.compiler.phases.common.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.graph.Node;

public class Pack<T extends Node> implements Iterable<T> {
   private List<T> elements;

   private Pack(T left, T right) {
       this.elements = Arrays.asList(left, right);
   }

   private Pack(List<T> elements) {
       this.elements = elements;
   }

   // Accessors
   public T getLeft() {
       return elements.get(0);
   }

   public T getRight() {
       return elements.get(elements.size() - 1);
   }

   public List<T> getElements() {
       return elements;
   }

   /**
    * Returns true if left matches the left side of the pair or right matches the right side of the pair.
    *
    * @param left Node on the left side of the candidate pair
    * @param right Node on the right side of the candidate pair
    */
   public boolean match(T left, T right) {
       return getLeft().equals(left) || getRight().equals(right);
   }

   public boolean isPair() {
       return elements.size() == 2;
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
       final StringBuilder sb = new StringBuilder();
       if (isPair()) {
           sb.append('P');
       }

       sb.append('<');
       sb.append(elements.toString());
       sb.append('>');
       return sb.toString();
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
   public static <T extends Node> Pack<T> pair(T left, T right) {
       return new Pack<>(left, right);
   }

   public static <T extends Node> Pack<T> list(List<T> elements) {
       if (elements.size() < 2)
           throw new IllegalArgumentException("cannot construct pack consisting of single element");

       return new Pack<>(elements);
   }

   /**
    * Appends the right pack to the left pack, omitting the first element of the right pack.
    * Pre: last element of left = first element of right
    */
   public static <T extends Node> Pack<T> combine(Pack<T> left, Pack<T> right) {
       T rightLeft = right.getLeft();
       return Pack.list(Stream.concat(
               left.elements.stream().filter(x -> !x.equals(rightLeft)),
               right.elements.stream()).collect(Collectors.toList()));
   }

}
