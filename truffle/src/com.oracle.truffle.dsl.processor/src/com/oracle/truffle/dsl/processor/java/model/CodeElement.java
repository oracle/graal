/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.java.model;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.java.transform.AbstractCodeWriter;

public abstract class CodeElement<E extends Element> implements Element, GeneratedElement {

    private final Set<Modifier> modifiers;
    private List<AnnotationMirror> annotations;
    private List<E> enclosedElements;
    private Element enclosingElement;

    private Element generatorElement;
    private AnnotationMirror generatorAnnotationMirror;

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
    public boolean equals(Object obj) {
        if (obj != null && this.getClass() == obj.getClass()) {
            CodeElement<?> other = (CodeElement<?>) obj;
            return Objects.equals(modifiers, other.modifiers) && //
                            Objects.equals(annotations, other.annotations) && //
                            Objects.equals(enclosedElements, other.enclosedElements);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifiers, annotations, enclosedElements);
    }

    @Override
    public Element getGeneratorElement() {
        return generatorElement;
    }

    public <T extends E> void addAll(Collection<? extends T> elements) {
        for (T t : elements) {
            add(t);
        }
    }

    public <T extends E> T add(T element) {
        if (element == null) {
            throw new NullPointerException();
        }
        getEnclosedElements().add(element);
        return element;
    }

    public <T extends E> T addOptional(T element) {
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

    public void setEnclosingElement(Element parent) {
        this.enclosingElement = parent;
    }

    public Element getEnclosingElement() {
        return enclosingElement;
    }

    public TypeElement getEnclosingClass() {
        Element p = enclosingElement;
        while (p != null && !p.getKind().isClass() && !p.getKind().isInterface()) {
            p = p.getEnclosingElement();
        }
        return (TypeElement) p;
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

        StringBuilderCodeWriter() {
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

        ParentableList(Element parent, List<T> delegate) {
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
