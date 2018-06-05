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
package com.oracle.svm.hosted.phases;

import java.util.HashMap;

import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.hosted.code.RestrictHeapAccessCallees;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Exceptions that are thrown implicitly by bytecodes, such as {@link NullPointerException}, are
 * thrown frequently in nearly all methods. Having an explicit object allocation in every method
 * would lead to code explosion. Therefore, we have one cached instance of every such exception
 * class. To keep the generated code as small as possible, the cached exception is thrown by a
 * runtime method.
 */
public class ImplicitExceptionsPlugin implements NodePlugin {

    private final MetaAccessProvider metaAccess;
    private final ForeignCallsProvider foreignCalls;
    private final ResolvedJavaType assertionErrorType;
    private final HashMap<ResolvedJavaMethod, SubstrateForeignCallDescriptor> runtimeAssertionFatalReplacements;

    public ImplicitExceptionsPlugin(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls) {
        this.metaAccess = metaAccess;
        this.foreignCalls = foreignCalls;
        this.assertionErrorType = metaAccess.lookupJavaType(AssertionError.class);

        runtimeAssertionFatalReplacements = new HashMap<>();
        ResolvedJavaMethod[] assertionErrorCtors = assertionErrorType.getDeclaredConstructors();
        for (ResolvedJavaMethod ctor : assertionErrorCtors) {
            SubstrateForeignCallDescriptor descriptor;
            switch (ctor.getSignature().toMethodDescriptor()) {
                case "()V": // AssertionError()
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION;
                    break;

                case "(Z)V": // AssertionError(boolean)
                case "(C)V": // AssertionError(char)
                case "(I)V": // AssertionError(int)
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION_INT;
                    break;

                case "(J)V": // AssertionError(long)
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION_LONG;
                    break;

                case "(F)V": // AssertionError(float)
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION_FLOAT;
                    break;

                case "(D)V": // AssertionError(double)
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION_DOUBLE;
                    break;

                case "(Ljava/lang/String;)V": // AssertionError(String)
                case "(Ljava/lang/Object;)V": // AssertionError(Object)
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION_OBJ;
                    break;

                // AssertionError(String, Throwable)
                case "(Ljava/lang/String;Ljava/lang/Throwable;)V":
                    descriptor = SnippetRuntime.FATAL_RUNTIME_ASSERTION_OBJ_OBJ;
                    break;
                default:
                    continue;
            }
            runtimeAssertionFatalReplacements.put(ctor, descriptor);
        }
    }

    private static boolean methodMustNotAllocate(GraphBuilderContext b) {
        return ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(b.getMethod());
    }

    /**
     * The singleton {@link AssertionError} to throw when an assert fails in code that must not
     * allocate.
     */
    private static final AssertionError CACHED_ASSERTION_ERROR = new AssertionError();

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        /*
         * Only instantiations of java.lang.AssertionError in methods that must not allocate should
         * be modified.
         */
        if (!b.parsingIntrinsic() && type.equals(assertionErrorType) && methodMustNotAllocate(b)) {
            /*
             * The constructors of java.lang.AssertionError are substituted to be fatal error
             * messages. So if we want assertions to be fatal errors, we just leave the call to the
             * constructor in place. We need to remove the allocation, because assertions in GC code
             * must be allocation free. The object is never used, but we cannot use null because
             * then we get a NullPointerException when calling the constructor. So we just use the
             * cached AssertionError object that already exists.
             */
            b.push(JavaKind.Object, ConstantNode.forConstant(SubstrateObjectConstant.forObject(CACHED_ASSERTION_ERROR), metaAccess, b.getGraph()));
            return true;
        }
        return false;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        String parsedMethodName = b.getMethod().getDeclaringClass().getName();
        if (!parsedMethodName.startsWith("Lcom/oracle/svm")) {
            return false;
        }
        SubstrateForeignCallDescriptor descriptor = runtimeAssertionFatalReplacements.get(method);
        if (descriptor != null) {
            b.add(new ForeignCallNode(foreignCalls, descriptor, args));
            b.add(new DeadEndNode());
            return true;
        }

        return false;
    }
}
