/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.configwithorigins;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.agent.tracing.ConfigurationResultWriter;
import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.configure.config.conditional.MethodCallNode;
import com.oracle.svm.configure.config.conditional.MethodInfo;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.jni.headers.JNIMethodId;

/**
 * Processes trace events that carry origin information.
 *
 * All trace entries (events) passed to this tracer are accompanied by a full call stack. Using that
 * information we construct a call tree where each call node maintains its own configuration set
 * resulting from the trace events from that method. When writing configuration files, the call tree
 * is written node by node, once per configuration file.
 */
public final class ConfigurationWithOriginsTracer extends Tracer {

    protected final TraceProcessor processor;
    protected final MethodCallNode rootNode;
    protected final MethodInfoRecordKeeper methodInfoRecordKeeper;

    public ConfigurationWithOriginsTracer(TraceProcessor processor, MethodInfoRecordKeeper methodInfoRecordKeeper) {
        this.processor = processor;
        this.rootNode = MethodCallNode.createRoot();
        this.methodInfoRecordKeeper = methodInfoRecordKeeper;
    }

    @Override
    public void traceEntry(EconomicMap<String, Object> entry) {
        String tracer = (String) entry.get("tracer");
        if (tracer.equals("meta")) {
            processor.processEntry(entry, null);
        } else {
            assert entry.containsKey("stack_trace");
            JNIMethodId[] rawStackTrace = (JNIMethodId[]) entry.removeKey("stack_trace");
            MethodInfo[] stackTrace = methodInfoRecordKeeper.getStackTraceInfo(rawStackTrace);
            EconomicMap<String, Object> transformedEntry = ConfigurationResultWriter.arraysToLists(entry);

            if (stackTrace == null) {
                traceEntry(rootNode, transformedEntry);
            } else {
                dispatchTraceEntry(stackTrace, transformedEntry);
            }
        }
    }

    public MethodCallNode getRootNode() {
        return rootNode;
    }

    private void dispatchTraceEntry(MethodInfo[] stackTrace, EconomicMap<String, Object> entry) {
        MethodCallNode currentNode = rootNode;
        for (int i = stackTrace.length - 1; i >= 0; i--) {
            MethodInfo nextMethodInfo = stackTrace[i];
            currentNode = currentNode.getOrCreateChild(nextMethodInfo);
        }
        traceEntry(currentNode, entry);
    }

    private void traceEntry(MethodCallNode node, EconomicMap<String, Object> entry) {
        processor.processEntry(entry, node.getConfiguration());
    }
}
