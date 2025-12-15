/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.modelimpl;

import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.CompilationElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Christian Wimmer
 */
public class CompilationElementImpl implements CompilationElement {
    private Compilation compilation;
    private CompilationElement parent;
    private CompilationElement[] elements;
    private String shortName;
    private String name;

    public CompilationElementImpl(String shortName, String name) {
        this.shortName = shortName;
        this.name = name;
        this.elements = new CompilationElement[0];
    }

    public Compilation getCompilation() {
        return compilation;
    }

    public CompilationElement getParent() {
        return parent;
    }
    
    public List<CompilationElement> getElements() {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    public void setElements(CompilationElementImpl[] elements, Compilation compilation) {
        this.elements = elements;
        for (CompilationElementImpl ce : elements) {
            ce.parent = this;
            ce.compilation = compilation;
        }
    }
    
    public String getShortName() {
        return shortName;
    }

    public String getName() {
        return name;
    }
}
