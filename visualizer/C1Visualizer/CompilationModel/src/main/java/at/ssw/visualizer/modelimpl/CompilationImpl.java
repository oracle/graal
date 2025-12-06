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
import at.ssw.visualizer.model.CompilationModel;
import java.util.Date;

/**
 *
 * @author Christian Wimmer
 */
public class CompilationImpl extends CompilationElementImpl implements Compilation {
    private CompilationModel compilationModel;
    private String method;
    private Date date;

    public CompilationImpl(String shortName, String name, String method, Date date) {
        super(shortName, name);
        this.method = method;
        this.date = date;
    }

    public CompilationModel getCompilationModel() {
        return compilationModel;
    }

    public void setCompilationModel(CompilationModelImpl compilationModel) {
        this.compilationModel = compilationModel;
    }

    public String getMethod() {
        return method;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("  Name: ").append(getName());
        result.append("\n  Method: ").append(method);
        result.append("\n  Date: ").append(date);
        result.append("\n  Elements: ").append(getElements().size());
        result.append("\n");
        for (CompilationElement element : getElements()) {
            result.append(element);
        }
        return result.toString();
    }
}
