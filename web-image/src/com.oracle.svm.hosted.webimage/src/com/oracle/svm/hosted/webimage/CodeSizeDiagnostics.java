/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage;

import java.io.File;
import java.io.PrintWriter;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.options.Option;

/**
 * Dump code size diagnostics to a CSV file.
 *
 * The CSV file has the following format:
 *
 * <pre>
 *     # name,                  id, size, callees
 *     java.lang.System.exit(), 10,  50,  50 30
 * </pre>
 *
 * The columns are explained below:
 * <ul>
 * <li><b>name</b>: It consists of the fully-qualified name of the class enclosing the method,
 * method name and signature.</li>
 * <li><b>id</b>: Unique identifier of the method.</li>
 * <li><b>size</b>: The generated code size for the method in bytes.</li>
 * <li><b>callees</b>: The methods that are directly called from this method.</li>
 * </ul>
 */
public class CodeSizeDiagnostics {
    static class Options {
        @Option(help = "Dump code size diagnostics.")//
        public static final HostedOptionKey<Boolean> CodeSizeDiagnostics = new HostedOptionKey<>(false);
    }

    final EconomicMap<AnalysisMethod, EconomicSet<AnalysisMethod>> calleesMap = EconomicMap.create(1024);

    /**
     * A function that returns the size of the given method.
     */
    private Function<HostedMethod, Integer> methodSizeProvider;

    public static void installMethodSizeProvider(Function<HostedMethod, Integer> methodSizeProvider) {
        if (!Options.CodeSizeDiagnostics.getValue()) {
            return;
        }

        CodeSizeDiagnostics self = ImageSingletons.lookup(CodeSizeDiagnostics.class);
        self.methodSizeProvider = methodSizeProvider;
    }

    public static void write(Feature.AfterImageWriteAccess access) {
        HostedUniverse universe = ((FeatureImpl.AfterImageWriteAccessImpl) access).getUniverse();
        CodeSizeDiagnostics self = ImageSingletons.lookup(CodeSizeDiagnostics.class);

        ReportUtils.report("Dependency and code size", getFile().toPath(), false, os -> {
            try (PrintWriter pw = new PrintWriter(os)) {
                self.dumpMethods(pw, universe);
            }
        });
    }

    private static File getFile() {
        return ReportUtils.reportFile(SubstrateOptions.reportsPath(), "dependencies", "csv");
    }

    private void dumpMethods(PrintWriter out, HostedUniverse universe) {
        for (HostedMethod method : universe.getMethods()) {
            if (!method.wrapped.isReachable()) {
                continue;
            }

            // full name
            out.print(method.format("%H.%n(%p)").replaceAll(",", ";"));
            out.print(", ");
            // id
            out.print(method.wrapped.getId());
            out.print(", ");
            // size
            Integer size = methodSizeProvider.apply(method);
            out.print(size == null ? 0 : size);
            out.print(",");
            // callees
            for (AnalysisMethod callee : calleesMap.get(method.wrapped)) {
                out.print(' ');
                out.print(callee.getId());
            }
            out.println();
        }
    }
}

@AutomaticallyRegisteredFeature
class CodeSizeDiagnosticsFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return CodeSizeDiagnostics.Options.CodeSizeDiagnostics.getValue();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        var data = new CodeSizeDiagnostics();
        ImageSingletons.add(CodeSizeDiagnostics.class, data);
        FeatureImpl.AfterAnalysisAccessImpl config = (FeatureImpl.AfterAnalysisAccessImpl) access;
        for (AnalysisMethod method : config.getUniverse().getMethods()) {
            if (method.isReachable()) {
                var callees = data.calleesMap.get(method);
                if (callees == null) {
                    callees = EconomicSet.create(8);
                    data.calleesMap.put(method, callees);
                }
                for (InvokeInfo invokeInfo : method.getInvokes()) {
                    callees.addAll(invokeInfo.getAllCallees());
                }
            }
        }
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        CodeSizeDiagnostics.write(access);
    }
}
