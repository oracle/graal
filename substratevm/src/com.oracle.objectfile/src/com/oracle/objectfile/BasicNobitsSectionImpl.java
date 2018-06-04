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
import com.oracle.objectfile.ObjectFile.NobitsSectionImpl;
import com.oracle.objectfile.ObjectFile.Section;

/**
 * This represents the core of a nobits/zerofill section, in any object file format. By default it
 * delegates to an underlying Section object, of some format-specific class.
 */
public class BasicNobitsSectionImpl extends BasicElementImpl implements NobitsSectionImpl {

    private long size;

    public BasicNobitsSectionImpl(long size) {
        this(size, null);
    }

    public BasicNobitsSectionImpl(Section s) {
        this(0L, s);
    }

    public BasicNobitsSectionImpl(long size, Section s) {
        super(s);
        this.size = size;
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        // we don't need a SIZE -> CONTENT dependency
        return ObjectFile.minimalDependencies(decisions, getElement());
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return 0; // size on disk!
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        return new byte[0];
    }

    @Override
    public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
        return (int) size;
    }

    @Override
    public long getSizeInMemory() {
        return size;
    }

    @Override
    public void setSizeInMemory(long size) {
        this.size = size;
    }

    @Override
    public int getAlignment() {
        return getElement().getAlignment();
    }

    @Override
    public boolean isLoadable() {
        return true;
    }
}
