/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.lang.reflect.Method;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The Eclipse Java compiler generates methods that lazily initializes tables for Enum switches,
 * using a known {@link #METHOD_NAME_PREFIX method name prefix}. The first invocation fills the
 * table, subsequent invocations reuse the table. We call the method during image generation, so
 * that the table gets built, and intrinsify the result of the invocation. This ensures that Enum
 * switches are allocation-free at run time. All classes involved must be safe for initialization at
 * image build time.
 */
final class EnumSwitchPlugin implements NodePlugin {

    private static final String METHOD_NAME_PREFIX = "$SWITCH_TABLE$";

    private final ParsingReason reason;

    EnumSwitchPlugin(ParsingReason reason) {
        this.reason = reason;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod m, ValueNode[] args) {
        VMError.guarantee(reason.duringAnalysis(), "plugin can only be used during parsing for analysis");
        AnalysisMethod method = (AnalysisMethod) m;

        if (!method.getName().startsWith(METHOD_NAME_PREFIX) || !method.isStatic() || method.getSignature().getParameterCount(false) != 0) {
            return false;
        }
        if (!method.getDeclaringClass().isInitialized()) {
            /*
             * Declaring class is initialized at run time. Even if the enum itself is initialized at
             * image build time, we cannot invoke the switch-table method because it is declared in
             * the class that contains the switch.
             */
            return false;
        }
        /*
         * There is no easy link from the method to the enum that it is actually used for. But we
         * need to ensure that invoking the method does not trigger any class initialization.
         * Parsing the method is the easiest way to find out what the method is going to do. If any
         * class initialization happens inside method, we must not invoke it. Note that we do not
         * check for transitive callees, because we trust that the Eclipse compiler only emits calls
         * that end up in the same class or in the JDK.
         */
        EnumSwitchSupport support = EnumSwitchSupport.singleton();
        AnalysisMetaAccess metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
        method.ensureGraphParsed(metaAccess.getUniverse().getBigbang());
        Boolean methodSafeForExecution = support.isMethodsSafeForExecution(method);
        assert methodSafeForExecution != null : "after-parsing hook not executed for method " + method.format("%H.%n(%p)");
        if (!methodSafeForExecution.booleanValue()) {
            return false;
        }

        Object switchTable;
        try {
            Method switchTableMethod = ReflectionUtil.lookupMethod(method.getDeclaringClass().getJavaClass(), method.getName());
            switchTable = switchTableMethod.invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw GraalError.shouldNotReachHere(ex); // ExcludeFromJacocoGeneratedReport
        }

        if (switchTable instanceof int[]) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(b.getSnippetReflection().forObject(switchTable), 1, true, b.getMetaAccess()));
            return true;
        }
        return false;
    }
}

@AutomaticallyRegisteredFeature
final class EnumSwitchFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        EnumSwitchSupport support = new EnumSwitchSupport();
        ImageSingletons.add(EnumSwitchSupport.class, support);
        access.getHostVM().addMethodAfterParsingListener(support::onMethodParsed);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        EnumSwitchSupport.singleton().afterAnalysis();
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        plugins.appendNodePlugin(new EnumSwitchPlugin(reason));
    }
}
