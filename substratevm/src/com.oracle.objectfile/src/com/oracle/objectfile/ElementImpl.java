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

/**
 * Clients defining their own kinds of section typically want to do so in a way that is agnostic
 * w.r.t. ELF, Mach-O or other object file formats. Each object file class has a corresponding
 * "user-defined section" class, and these sections' implementations delegate to an ElementImpl. A
 * single ElementImpl interface suffices for all classes of object file. Therefore, by implementing
 * this interface, clients can breaks out of the ELF/Mach-O/... class hierarchy and implement common
 * functionality (e.g. DWARF debugging sections) in a format-agnostic way.
 *
 */
public interface ElementImpl {

    /**
     * Implementing this method allows to declare dependencies to other Sections (i.e. information
     * about other sections needed to construct this section). The information will be used to
     * construct the buildOrder in ObjectFile.writeInternal(). A good example can be found at:
     * ELFObjectFile.ProgramHeaderTable.getDependencies
     */
    Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions);

    /**
     * Implement this method to provide the offset this section should be placed at.
     */
    int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint);

    /**
     * Implement this method to provide the size of this section.
     */
    int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint);

    /**
     * Implement this method to deliver the content of the section.
     */
    byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint);

    /**
     * Implement this method to provide the virtual address of this section. For loadable sections,
     * this is where the section is expected to be mapped to at runtime (see {@link #isLoadable()}).
     */
    int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint);

    /**
     * Implement this method to provide the section size in memory (at runtime).
     */
    int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided);

    LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn);

    ObjectFile.Element getElement();

    void setElement(ObjectFile.Element e);

    int getAlignment();

    /**
     * Whether this section is loaded into memory at runtime.
     */
    boolean isLoadable();

    /**
     * Locations in this section are referenceable by symbol names, section names, or by relocation,
     * so the section needs a virtual address. Generally applies to loadable sections, but can also
     * be used to enable references into sections that are not loadable, such as debug sections.
     */
    boolean isReferenceable();
}
