/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile;

import java.util.Map;

import com.oracle.objectfile.ObjectFile.Element;

public abstract class BasicElementImpl implements ElementImpl {

    private Element element;

    public BasicElementImpl(Element element) {
        this.element = element;
    }

    /**
     * Sometimes the element cannot be constructed until the Impl is available (e.g. when using
     * ObjectFile.newUserDefinedSection()). If you use this constructor, you must called setElement
     * before using any other methods.
     */
    public BasicElementImpl() {
    }

    @Override
    public void setElement(Element element) {
        /*
         * Callers are allowed to redundantly set the same element, but they're not allowed to
         * change it
         */
        assert element != null;
        assert this.element == null || this.element == element;
        this.element = element;
    }

    public ObjectFile getOwner() {
        return element.getOwner();
    }

    @Override
    public Element getElement() {
        assert element != null;
        return element;
    }

    @Override
    public int getAlignment() {
        return element.getAlignment();
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return ObjectFile.defaultDependencies(decisions, element);
    }

    @Override
    public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
        return ObjectFile.defaultDecisions(element, copyingIn);
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, element, offsetHint);
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return ObjectFile.defaultGetOrDecideSize(alreadyDecided, element, sizeHint);
    }

    @Override
    public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
        return ObjectFile.defaultGetOrDecideVaddr(alreadyDecided, element, vaddrHint);
    }

    @Override
    public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
        return (int) alreadyDecided.get(element).getDecidedValue(LayoutDecision.Kind.SIZE);
    }

    @Override
    public boolean isReferenceable() {
        return isLoadable();
    }
}
