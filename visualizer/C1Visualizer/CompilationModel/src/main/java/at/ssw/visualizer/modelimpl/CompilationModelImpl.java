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
import at.ssw.visualizer.model.CompilationModel;
import at.ssw.visualizer.parser.CompilationParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Christian Wimmer
 */
public class CompilationModelImpl implements CompilationModel {
    private List<Compilation> compilations = new ArrayList<Compilation>();
    private List<ChangeListener> listeners = new ArrayList<ChangeListener>();

    private boolean parsing;

    public List<Compilation> getCompilations() {
        return Collections.unmodifiableList(compilations);
    }

    public String parseInputFile(String fileName) {
        parsing = true;
        String result;
        try {
            result = CompilationParser.parseInputFile(fileName, this);
        } finally {
            parsing = false;
            notifyListeners();
        }
        return result;
    }

    public void addCompilation(CompilationImpl compilation) {
        this.compilations.add(compilation);
        compilation.setCompilationModel(this);
        
        if (!parsing || this.compilations.size() % 40 == 0) {
            notifyListeners();
        }
    }

    public void removeCompilation(Compilation compilation) {
        compilations.remove(compilation);
        notifyListeners();
    }

    public void clear() {
        compilations.clear();
        notifyListeners();
    }


    public void addChangedListener(ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangedListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners) {
            listener.stateChanged(event);
        }
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Compilations: ");
        result.append(compilations.size());
        result.append("\n");
        for (Compilation compilation : compilations) {
            result.append(compilation);
        }
        return result.toString();
    }
}
