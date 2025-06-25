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
package com.oracle.svm.hosted.webimage.codegen.oop;

import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.codegen.heap.ConstantMap;
import com.oracle.svm.hosted.webimage.codegen.wrappers.JSEmitter;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 *
 * Static field are lowered with their name not their HIR index, for accessing static fields the
 * name of the property and the prototype is used (again) (resolved via var map).
 *
 * NOTE: the name of this static variable is assumed to be the name of the field, as the
 * {@link ConstantMap} assumes the static variables keep this name.
 */
public class StaticFieldLowerer {
    private final HostedType type;
    private final HashMap<HostedField, Constant> recursiveConstants;

    public StaticFieldLowerer(HostedType type, HashMap<HostedField, Constant> recursiveConstants) {
        this.type = type;
        this.recursiveConstants = recursiveConstants;
    }

    public static HashMap<HostedField, Constant> registerLowering(HostedType type, WebImageJSProviders providers) {
        HashMap<HostedField, Constant> recursiveConstants = new HashMap<>();

        for (ResolvedJavaField f : type.getStaticFields()) {
            HostedField hf = (HostedField) f;

            if (!hf.isRead()) {
                continue;
            }

            JavaConstant c = providers.getConstantReflection().readFieldValue(hf, null);
            /*
             * For non-primitive fields we need to resolve the object constant. The value is already
             * replaced on hosted field read.
             */
            if (!hf.getType().isPrimitive() && c.isNonNull() && !(c instanceof PrimitiveConstant)) {
                // must force resolve -> var name used in lowering
                providers.typeControl().getConstantMap().resolveConstant(c);
            }
            recursiveConstants.put(hf, c);
        }

        return recursiveConstants;

    }

    public void lower(JSCodeGenTool jsLTools) {
        for (Map.Entry<HostedField, Constant> entry : recursiveConstants.entrySet()) {
            HostedField f = entry.getKey();
            JavaConstant c = (JavaConstant) entry.getValue();

            IEmitter value;

            if (c.getJavaKind().isPrimitive()) {
                value = JSEmitter.of((PrimitiveConstant) c);
            } else {
                if (c.equals(JavaConstant.NULL_POINTER)) {
                    value = Emitter.ofNull();
                } else {
                    ConstantMap constantMap = jsLTools.getJSProviders().typeControl().getConstantMap();
                    value = Emitter.of(constantMap.resolveConstant(c));
                }
            }

            jsLTools.getCodeBuffer().emitNewLine();
            jsLTools.genComment(f.format("static %T %H.%n"), WebImageOptions.CommentVerbosity.MINIMAL);
            jsLTools.genPropertyAccess(Emitter.of(type), Emitter.of(f));
            jsLTools.genAssignment();
            value.lower(jsLTools);
            jsLTools.genResolvedVarDeclPostfix(null);
        }
    }
}
