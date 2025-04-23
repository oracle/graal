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

package org.graalvm.visualizer.difference.impl;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.openide.util.Exceptions;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;

/**
 * @author odouda
 */
public class DiffGraph extends InputGraph implements Group.LazyContent<Collection<InputNode>> {
    private CompletableFuture<Collection<InputNode>> f;
    private final InputGraph a, b;
    private final DiffGraphCompletion completer;

    public interface DiffGraphCompletion {
        DiffGraph complete(InputGraph a, InputGraph b, DiffGraph c);
    }

    public DiffGraph(Object id, String format, Object[] args, InputGraph a, InputGraph b, DiffGraphCompletion completer) {
        super(id, INVALID_INDEX, format, args);
        assert completer != null;
        assert a.getGraphType().equals(b.getGraphType());
        setGraphType(a.getGraphType());
        this.a = a;
        this.b = b;
        this.completer = completer;
    }

    private InputGraph completeDiffGraph() {
        Object ta = completeLazy(a);
        Object tb = completeLazy(b);
        completer.complete(a, b, this);
        return this;
    }

    private static Object completeLazy(InputGraph g) {
        if (g instanceof Group.LazyContent) {
            Group.LazyContent lg = (Group.LazyContent) g;
            if (!lg.isComplete()) {
                try {
                    return lg.completeContents(null).get();
                } catch (InterruptedException | ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        return null;
    }

    @Override
    public boolean isComplete() {
        if (f == null) {
            return false;
        }
        return f.isDone();
    }

    @Override
    public Future<Collection<InputNode>> completeContents(Group.Feedback feedback) {
        if (f == null) {
            f = CompletableFuture.supplyAsync(() -> completeDiffGraph().getNodes());
        }
        return f;
    }

    @Override
    public Collection<InputNode> partialData() {
        return null;
    }
}
