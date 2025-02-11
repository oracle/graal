/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.graphbuilderconf;

import static jdk.graal.compiler.core.common.NativeImageSupport.inBuildtimeCode;
import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInlineOnlyInvocationPlugin;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Abstract class for a plugin generated for a method annotated by {@link NodeIntrinsic} or
 * {@link Fold}.
 */
public abstract class GeneratedInvocationPlugin extends RequiredInlineOnlyInvocationPlugin {

    private ResolvedJavaMethod executeMethod;

    public GeneratedInvocationPlugin(String name, Type... argumentTypes) {
        super(name, argumentTypes);
    }

    /**
     * Gets the class of the annotation for which this plugin was generated.
     */
    public abstract Class<? extends Annotation> getSource();

    @Override
    public abstract boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args);

    @Override
    public String getSourceLocation() {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("execute")) {
                return String.format("%s.%s()", m.getDeclaringClass().getName(), m.getName());
            }
        }
        throw new GraalError("could not find method named \"execute\" in " + c.getName());
    }

    /**
     * Determines if the {@linkplain InjectedParameter} value in {@code arg} allows folding of a
     * call to {@code foldAnnotatedMethod} in the compilation context represented by {@code b}.
     *
     * @return true if the folding being attempted by the caller can proceed
     */
    protected boolean checkInjectedArgument(GraphBuilderContext b, ValueNode arg, ResolvedJavaMethod foldAnnotatedMethod) {
        if (inRuntimeCode()) {
            // In native image runtime compilation, there is no later stage where execution of the
            // plugin can be deferred.
            return true;
        }

        if (arg.isNullConstant()) {
            return true;
        }

        if (b.getMethod().equals(foldAnnotatedMethod)) {
            return false;
        }

        if (inBuildtimeCode()) {
            // Calls to the @Fold method from the generated fold plugin shouldn't be folded. This is
            // detected by comparing the class names of the current plugin and the method being
            // parsed. These might be in different class loaders so the classes can't be compared
            // directly. Class.getName() and ResolvedJavaType.getName() return different formats so
            // get the ResolvedJavaType for this plugin.
            ResolvedJavaType thisClass = b.getMetaAccess().lookupJavaType(getClass());
            if (thisClass.getName().equals(b.getMethod().getDeclaringClass().getName())) {
                return false;
            }
        }

        ResolvedJavaMethod thisExecuteMethod = getExecuteMethod(b);
        if (b.getMethod().equals(thisExecuteMethod)) {
            return true;
        }
        throw new AssertionError("must pass null to injected argument of " + foldAnnotatedMethod.format("%H.%n(%p)") + ", not " + arg + " in " + b.getMethod().format("%H.%n(%p)"));
    }

    private ResolvedJavaMethod getExecuteMethod(GraphBuilderContext b) {
        if (executeMethod == null) {
            try {
                MetaAccessProvider metaAccess = b.getMetaAccess();
                Method result = GeneratedInvocationPlugin.class.getMethod("execute", GraphBuilderContext.class, ResolvedJavaMethod.class, Receiver.class, ValueNode[].class);
                ResolvedJavaMethod baseMethod = metaAccess.lookupJavaMethod(result);
                ResolvedJavaType thisClass = metaAccess.lookupJavaType(getClass());
                executeMethod = thisClass.resolveConcreteMethod(baseMethod, thisClass);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalError(e);
            }
        }
        return executeMethod;
    }

    public final boolean isGeneratedFromFoldOrNodeIntrinsic() {
        return getSource().equals(Fold.class) || getSource().equals(NodeIntrinsic.class);
    }
}
