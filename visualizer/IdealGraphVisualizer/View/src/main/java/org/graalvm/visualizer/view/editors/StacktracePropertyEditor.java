/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.view.editors;

import java.awt.*;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.beans.PropertyEditorSupport;
import java.util.*;
import java.util.List;

import org.openide.explorer.propertysheet.ExPropertyEditor;
import org.openide.explorer.propertysheet.PropertyEnv;
import org.openide.nodes.PropertyEditorRegistration;

import jdk.graal.compiler.graphio.parsing.LocationStackFrame;
import jdk.graal.compiler.graphio.parsing.LocationStratum;

/**
 * @author sdedic
 */
@PropertyEditorRegistration(targetType = LocationStackFrame.class)
public class StacktracePropertyEditor extends PropertyEditorSupport implements ExPropertyEditor {
    private PropertyEnv env;
    private PropertyEditor delegate;

    @Override
    public void attachEnv(PropertyEnv pe) {
        this.env = pe;
        this.delegate = PropertyEditorManager.findEditor(String.class);
        if (this.delegate instanceof ExPropertyEditor) {
            ((ExPropertyEditor) delegate).attachEnv(pe);
        }
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAsText() {
        Map<String, List<String>> langLines = new HashMap<>();
        LocationStackFrame elem = (LocationStackFrame) getValue();
        while (elem != null) {
            Set<String> langSeen = new HashSet<>();
            for (LocationStratum st : elem.getStrata()) {
                if (!langSeen.add(st.language)) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                boolean j = LANG_JAVA.equals(st.language);
                if (j) {
                    if (elem.getFullMethodName() != null) {
                        sb.append(elem.getFullMethodName());
                        sb.append(" ");
                    }
                }

                if (st.file != null) {
                    sb.append("(").append(st.file).append(":").append(st.line).append(")");
                } else if (st.uri != null) {
                    sb.append("(").append(st.uri).append(":").append(st.line).append(")");
                }
                if (j) {
                    sb.append(" [bci:").append(elem.getBci()).append(']');
                }
                List<String> trace = langLines.get(st.language);
                if (trace == null) {
                    trace = new ArrayList<>();
                    langLines.put(st.language, trace);
                    trace.add(sb.toString());
                } else {
                    String ll = sb.toString();
                    if (!trace.get(trace.size() - 1).equals(ll)) {
                        trace.add(ll);
                    }
                }

            }
            elem = elem.getParent();
        }


        // finally build the string:
        StringBuilder sb = new StringBuilder();
        List<String> lines = langLines.get(LANG_JAVA);
        if (lines != null) {
            sb.append("Java:");
            printLanguageTrace(sb, lines);
        }
        for (String l : langLines.keySet()) {
            if (!LANG_JAVA.equals(l)) {
                sb.append("\n\n");
                sb.append(l).append(':');
                printLanguageTrace(sb, langLines.get(l));
            }
        }
        return sb.toString();
    }

    private static final String LANG_JAVA = "Java";

    private void printLanguageTrace(StringBuilder sb, List<String> lines) {
        for (String s : lines) {
            sb.append("\n\t");
            sb.append(s);
        }
    }

    @Override
    public boolean supportsCustomEditor() {
        return delegate.supportsCustomEditor();
    }

    @Override
    public Component getCustomEditor() {
        delegate.setValue(getAsText());
        return delegate.getCustomEditor();
    }

}
