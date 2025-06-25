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
package com.oracle.svm.hosted.webimage.codegen.type;

import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.hightiercodegen.Emitter;

import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.common.JVMCIError;

public class InvokeLoweringUtil {

    public enum WebImageCallType {
        /**
         * Early bind without receiver.
         */
        STATIC,
        /**
         * Early bind with receiver with direct call syntax (<init>,super,private -> invokespecial).
         */
        DYNAMIC_EARLY,
        /**
         * Early bind with receiver.
         */
        DYNAMIC_NOVTAB,
        /**
         * Late bind with receiver (-> vtab).
         */
        DYNAMIC_VTAB,
        /**
         * Indirect function invocation (calls the function at the specified numeric address).
         */
        INDIRECT,

    }

    private static void performCallOperation(WebImageCallType calltype, HostedMethod targetMethod, Invoke n, JSCodeGenTool jsLTools) {
        if (calltype == WebImageCallType.STATIC) {
            jsLTools.genStaticCall(targetMethod, Emitter.of(n.callTarget().arguments()));
        } else {
            switch (calltype) {
                case DYNAMIC_VTAB:
                    if (WebImageOptions.UseVtable.getValue(HostedOptionValues.singleton())) {
                        jsLTools.genFunctionCall(Emitter.of(n.getReceiver()), Emitter.of("constructor." + TypeVtableLowerer.VTAB_PROP + "[" + targetMethod.getVTableIndex() + "]"),
                                        Emitter.of(n.callTarget().arguments()));
                    } else {
                        lowerDynamicJSCall(targetMethod, n, jsLTools);
                    }
                    break;
                case DYNAMIC_NOVTAB:
                    // lower the receiver
                    lowerDynamicJSCall(targetMethod, n, jsLTools);
                    break;
                case DYNAMIC_EARLY:
                    WebImageHostedConfiguration.get().getInvokeLoweringUtil().emitPrototypeCall(n, jsLTools);
                    break;
                case INDIRECT:
                    ValueNode address = ((IndirectCallTargetNode) n.callTarget()).computedAddress();
                    jsLTools.genIndirectCall(Emitter.of(address), Emitter.of(n.callTarget().arguments()));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    private static void lowerDynamicJSCall(HostedMethod m, Invoke invoke, JSCodeGenTool jsLTools) {
        jsLTools.genFunctionCall(Emitter.of(invoke.getReceiver()), Emitter.of(m), Emitter.of(invoke.callTarget().arguments()));
    }

    /**
     * Emits a call to the given method through the prototype object.
     *
     * This can be used when one want to circumvent JS's dynamic binding, for example when doing
     * super calls or when the target method is known.
     *
     * @param n invoke to be lowered
     * @param jsLTools lowering tools
     */
    protected void emitPrototypeCall(Invoke n, JSCodeGenTool jsLTools) {
        jsLTools.genPrototypeCall(n.getTargetMethod(), Emitter.of(n.callTarget().arguments()));
    }

    public static void lowerOrPatchStatic(HostedMethod targetMethod, Invoke n, JSCodeGenTool jsLTools) {
        WebImageCallType calltype = getCallType(targetMethod, null, n);
        assert calltype == WebImageCallType.STATIC : calltype;
        performCallOperation(calltype, targetMethod, n, jsLTools);
    }

    public static void lowerOrPatchDynamic(HostedMethod targetMethod, HostedType receiverType, Invoke n, JSCodeGenTool jsLTools) {
        JVMCIError.guarantee(n.getInvokeKind() != InvokeKind.Static, "Only Dynamic calls are allowed %s", n.toString());
        WebImageCallType calltype = getCallType(targetMethod, receiverType, n);
        assert calltype != WebImageCallType.STATIC : calltype;
        performCallOperation(calltype, targetMethod, n, jsLTools);
    }

    public static void lowerIndirect(Invoke node, JSCodeGenTool jsLTools) {
        performCallOperation(WebImageCallType.INDIRECT, null, node, jsLTools);
    }

    public static WebImageCallType getCallType(HostedMethod targetMethod, HostedType receiverType, Invoke n) {
        if (receiverType == null) {
            // early bind static call
            assert n.getInvokeKind() == InvokeKind.Static : n.getInvokeKind();
            return WebImageCallType.STATIC;
        }
        assert n.getInvokeKind() != InvokeKind.Static : n.getInvokeKind();

        if (n.getInvokeKind() == InvokeKind.Special) {
            return WebImageCallType.DYNAMIC_EARLY;
        } else {
            // dynamic call
            if (targetMethod.hasVTableIndex()) {
                // vtab call
                return WebImageCallType.DYNAMIC_VTAB;
            } else {
                // dynamic call early bind
                return WebImageCallType.DYNAMIC_NOVTAB;
            }
        }
    }
}
