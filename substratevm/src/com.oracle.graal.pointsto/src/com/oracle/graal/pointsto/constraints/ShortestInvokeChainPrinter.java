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
package com.oracle.graal.pointsto.constraints;

import java.io.PrintStream;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.code.BytecodePosition;

public final class ShortestInvokeChainPrinter {

    static class Element {

        protected final Element parent;
        protected final AnalysisMethod method;
        protected final InvokeTypeFlow invoke;

        protected Element(AnalysisMethod method, Element parent, InvokeTypeFlow invoke) {
            this.parent = parent;
            this.method = method;
            this.invoke = invoke;
        }
    }

    public static void print(BigBang bb, AnalysisMethod target) {
        print(bb, target, System.out);
    }

    public static void print(BigBang bb, AnalysisMethod target, PrintStream out) {
        Deque<AnalysisMethod> workList = new LinkedList<>();
        Map<AnalysisMethod, Element> visited = new HashMap<>();

        for (AnalysisMethod m : bb.getUniverse().getMethods()) {
            if (m.isEntryPoint()) {
                workList.addLast(m);
                visited.put(m, new Element(m, null, null));
            }
        }

        while (workList.size() > 0) {
            AnalysisMethod method = workList.removeFirst();
            Element methodElement = visited.get(method);
            assert methodElement != null;

            for (InvokeTypeFlow invoke : method.getTypeFlow().getInvokes()) {
                for (AnalysisMethod callee : invoke.getCallees()) {

                    if (visited.containsKey(callee)) {
                        // We already had a shorter path to this method.
                        continue;
                    }
                    Element calleeElement = new Element(callee, methodElement, invoke);
                    visited.put(callee, calleeElement);
                    if (callee.equals(target)) {
                        // We found a path from an entry point to our target method.
                        printPath(calleeElement, out);
                        return;
                    } else {
                        workList.addLast(callee);
                    }
                }
            }
        }
        printNoPath(out);
    }

    private static void printPath(Element start, PrintStream out) {
        Element cur = start;
        out.print("\tat " + cur.method.asStackTraceElement(0));
        while (cur.parent != null) {
            BytecodePosition source = cur.invoke.getSource();
            out.print("\n\tat " + source.getMethod().asStackTraceElement(source.getBCI()));
            cur = cur.parent;
        }
    }

    private static void printNoPath(PrintStream out) {
        out.println("\tno path found from entry point to target method");
    }
}
