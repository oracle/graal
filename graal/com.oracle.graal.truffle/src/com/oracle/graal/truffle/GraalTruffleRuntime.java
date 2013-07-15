/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.nodes.spi.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class GraalTruffleRuntime implements TruffleRuntime {

    public static GraalTruffleRuntime makeInstance() {
        return new GraalTruffleRuntime();
    }

    private TruffleCompiler truffleCompiler;
    private Replacements truffleReplacements;
    private ArrayList<String> includes;
    private ArrayList<String> excludes;

    private GraalTruffleRuntime() {
    }

    public String getName() {
        return "Graal Truffle Runtime";
    }

    public CallTarget createCallTarget(RootNode rootNode) {
        return createCallTarget(rootNode, new FrameDescriptor());
    }

    @Override
    public CallTarget createCallTarget(RootNode rootNode, FrameDescriptor frameDescriptor) {
        if (!acceptForCompilation(rootNode)) {
            return new DefaultCallTarget(rootNode, frameDescriptor);
        }
        if (truffleCompiler == null) {
            truffleCompiler = new TruffleCompilerImpl();
        }
        return new OptimizedCallTarget(rootNode, frameDescriptor, truffleCompiler, TruffleCompilationThreshold.getValue());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Arguments arguments) {
        return createMaterializedFrame(arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Arguments arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, null, arguments);
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    public Replacements getReplacements() {
        if (truffleReplacements == null) {
            truffleReplacements = TruffleReplacements.makeInstance();
        }
        return truffleReplacements;
    }

    private boolean acceptForCompilation(RootNode rootNode) {
        if (TruffleCompileOnly.getValue() != null) {
            if (includes == null) {
                parseCompileOnly();
            }

            String name = rootNode.toString();
            boolean included = includes.isEmpty();
            for (int i = 0; !included && i < includes.size(); i++) {
                if (name.contains(includes.get(i))) {
                    included = true;
                }
            }
            if (!included) {
                return false;
            }
            for (String exclude : excludes) {
                if (name.contains(exclude)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void parseCompileOnly() {
        includes = new ArrayList<>();
        excludes = new ArrayList<>();

        String[] items = TruffleCompileOnly.getValue().split(",");
        for (String item : items) {
            if (item.startsWith("~")) {
                excludes.add(item.substring(1));
            } else {
                includes.add(item);
            }
        }
    }
}
