/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import java.io.*;
import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.java.transform.*;

public abstract class CodeElement<E extends Element> implements Element, GeneratedElement {

    private final Set<Modifier> modifiers;
    private List<AnnotationMirror> annotations;
    private List<E> enclosedElements;

    private Element enclosingElement;

    private Element generatorElement;
    private AnnotationMirror generatorAnnotationMirror;

    public CodeElement() {
        this.modifiers = new LinkedHashSet<>();
    }

    public CodeElement(Set<Modifier> modifiers) {
        this.modifiers = new LinkedHashSet<>(modifiers);
    }

    @Override
    public void setGeneratorAnnotationMirror(AnnotationMirror mirror) {
        this.generatorAnnotationMirror = mirror;
    }

    @Override
    public void setGeneratorElement(Element element) {
        this.generatorElement = element;
    }

    @Override
    public AnnotationMirror getGeneratorAnnotationMirror() {
        return generatorAnnotationMirror;
    }

    @Override
    public Element getGeneratorElement() {
        return generatorElement;
    }

    public E add(E element) {
        if (element == null) {
            throw new NullPointerException();
        }
        getEnclosedElements().add(element);
        return element;
    }

    public E addOptional(E element) {
        if (element != null) {
            add(element);
        }
        return element;
    }

    public void remove(E element) {
        getEnclosedElements().remove(element);
    }

    @Override
    public Set<Modifier> getModifiers() {
        return modifiers;
    }

    @Override
    public List<E> getEnclosedElements() {
        if (enclosedElements == null) {
            enclosedElements = parentableList(this, new ArrayList<E>());
        }
        return enclosedElements;
    }

    @Override
    public List<AnnotationMirror> getAnnotationMirrors() {
        if (annotations == null) {
            annotations = parentableList(this, new ArrayList<AnnotationMirror>());
        }
        return annotations;
    }

    /**
     * Support JDK8 langtools.
     *
     * @param annotationType
     */
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        throw new UnsupportedOperationException();
    }

    /**
     * Support for some JDK8 builds. (remove after jdk8 is released)
     *
     * @param annotationType
     */
    public <A extends Annotation> A[] getAnnotations(Class<A> annotationType) {
        throw new UnsupportedOperationException();
    }

    /**
     * Support for some JDK8 builds. (remove after jdk8 is released)
     *
     * @param annotationType
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        throw new UnsupportedOperationException();
    }

    public void addAnnotationMirror(AnnotationMirror annotationMirror) {
        getAnnotationMirrors().add(annotationMirror);
    }

    protected void setEnclosingElement(Element parent) {
        this.enclosingElement = parent;
    }

    public Element getEnclosingElement() {
        return enclosingElement;
    }

    public CodeTypeElement getEnclosingClass() {
        Element p = enclosingElement;
        while (p != null && p.getKind() != ElementKind.CLASS && p.getKind() != ElementKind.ENUM) {
            p = p.getEnclosingElement();
        }
        return (CodeTypeElement) p;
    }

    <T> List<T> parentableList(Element parent, List<T> list) {
        return new ParentableList<>(parent, list);
    }

    @Override
    public String toString() {
        StringBuilderCodeWriter codeWriter = new StringBuilderCodeWriter();
        accept(codeWriter, null);
        return codeWriter.getString();
    }

    private static class StringBuilderCodeWriter extends AbstractCodeWriter {

        public StringBuilderCodeWriter() {
            this.writer = new CharArrayWriter();
        }

        @Override
        protected Writer createWriter(CodeTypeElement clazz) throws IOException {
            return writer;
        }

        public String getString() {
            return new String(((CharArrayWriter) writer).toCharArray()).trim();
        }

    }

    private static class ParentableList<T> implements List<T> {

        private final Element parent;
        private final List<T> delegate;

        public ParentableList(Element parent, List<T> delegate) {
            this.parent = parent;
            this.delegate = delegate;
        }

        private void addImpl(T element) {
            if (element != null) {
                if (element instanceof CodeElement<?>) {
                    ((CodeElement<?>) element).setEnclosingElement(parent);
                }
            }
        }

        private static void removeImpl(Object element) {
            if (element instanceof CodeElement<?>) {
                ((CodeElement<?>) element).setEnclosingElement(null);
            }
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public Iterator<T> iterator() {
            return delegate.iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <E> E[] toArray(E[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(T e) {
            addImpl(e);
            return delegate.add(e);
        }

        @Override
        public boolean remove(Object o) {
            boolean removed = delegate.remove(o);
            if (removed) {
                removeImpl(o);
            }
            return removed;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            if (c != null) {
                for (T t : c) {
                    addImpl(t);
                }
            }
            return delegate.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends T> c) {
            if (c != null) {
                for (T t : c) {
                    addImpl(t);
                }
            }
            return delegate.addAll(index, c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (c != null) {
                for (Object t : c) {
                    removeImpl(t);
                }
            }
            return delegate.removeAll(c);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("Not supported by parentable list");
        }

        @Override
        public void clear() {
            for (Object e : this) {
                removeImpl(e);
            }
            delegate.clear();
        }

        @Override
        public T get(int index) {
            return delegate.get(index);
        }

        @Override
        public T set(int index, T element) {
            removeImpl(delegate.get(index));
            addImpl(element);
            return delegate.set(index, element);
        }

        @Override
        public void add(int index, T element) {
            addImpl(element);
            delegate.add(index, element);
        }

        @Override
        public T remove(int index) {
            T element = delegate.remove(index);
            removeImpl(element);
            return element;
        }

        @Override
        public int indexOf(Object o) {
            return delegate.indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return delegate.lastIndexOf(o);
        }

        @Override
        public ListIterator<T> listIterator() {
            return delegate.listIterator();
        }

        @Override
        public ListIterator<T> listIterator(int index) {
            return delegate.listIterator(index);
        }

        @Override
        public List<T> subList(int fromIndex, int toIndex) {
            return new ParentableList<>(parent, delegate.subList(fromIndex, toIndex));
        }

    }

}
