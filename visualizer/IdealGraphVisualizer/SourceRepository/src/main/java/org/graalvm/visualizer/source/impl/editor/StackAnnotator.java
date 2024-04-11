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

package org.graalvm.visualizer.source.impl.editor;

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeLocationEvent;
import org.graalvm.visualizer.source.NodeLocationListener;
import org.graalvm.visualizer.source.NodeStack;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.modules.OnStart;
import org.openide.text.Annotation;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 *
 */
public class StackAnnotator implements Runnable, NodeLocationListener {
    private static final RequestProcessor RP = new RequestProcessor(StackAnnotator.class);
    private final NodeLocationContext locContext;
    private final RequestProcessor.Task refreshTask = RP.create(this, true);

    private Collection<Annotation> attachedAnnotations = Collections.emptyList();
    private Collection<Annotation> newAnnotations;
    private NodeStack.Frame current;

    public StackAnnotator(NodeLocationContext locContext) {
        this.locContext = locContext;
    }

    @Override
    public void run() {
        newAnnotations = new ArrayList<>();
        current = locContext.getSelectedFrame();
        for (InputNode n : locContext.getGraphNodes()) {
            NodeStack locs = locContext.getStack(n);
            if (locs == null) {
                continue;
            }
            boolean isCurrentNode = locs.contains(current);

            // XXX: change for multi-node scenario, must annotate lines from all
            // nodes.
            if (!isCurrentNode) {
                continue;
            }
            for (NodeStack.Frame l : locs) {
                annotateLocation(l);
            }
        }
        for (Annotation a : attachedAnnotations) {
            a.detach();
        }
        attachedAnnotations = newAnnotations;
        newAnnotations = null;
        current = null;
    }

    void annotateLocation(NodeStack.Frame l) {
        FileObject f = l.getOriginFile();
        if (f == null) {
            return;
        }
        LineCookie cake = f.getLookup().lookup(LineCookie.class);
        if (cake == null) {
            return;
        }
        Line line = null;
        try {
            line = cake.getLineSet().getOriginal(l.getLine() - 1);
        } catch (IndexOutOfBoundsException ex) {
            // expected, source has changed.
            return;
        }
        if (line == null) {
            return;
        }
        Annotation a;
        if (current == null) {
            return;
        }
        String suffix = l == current ? "Current" : ""; // NOI18N
        if (l.getNested() == null) {
            a = new StackAnnotation(line, l.getLocation(), StackAnnotation.NODE + suffix);
        } else if (current.isNestedIn(l)) {
            a = new StackAnnotation(line, l.getLocation(), StackAnnotation.OUTER + suffix);
        } else if (current == l || l.isNestedIn(current)) {
            a = new StackAnnotation(line, l.getLocation(), StackAnnotation.NESTED + suffix);
        } else {
            return;
        }
        a.attach(line);
        newAnnotations.add(a);
    }

    @Override
    public void nodesChanged(NodeLocationEvent evt) {
//        refreshTask.schedule(0);
    }

    @Override
    public void locationsResolved(NodeLocationEvent evt) {
    }

    @Override
    public void selectedNodeChanged(NodeLocationEvent evt) {
    }

    @Override
    public void selectedLocationChanged(NodeLocationEvent evt) {
        refreshTask.schedule(0);
    }

    @OnStart
    public static class Init implements Runnable {
        @Override
        public void run() {
            NodeLocationContext locContext = Lookup.getDefault().lookup(NodeLocationContext.class);
            if (locContext != null) {
                locContext.addNodeLocationListener(new StackAnnotator(locContext));
            }
        }
    }
}
