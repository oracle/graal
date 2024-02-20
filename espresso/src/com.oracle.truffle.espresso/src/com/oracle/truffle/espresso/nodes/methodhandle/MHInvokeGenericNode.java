/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.methodhandle;

import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Orchestrates the semantics of invoke and invoke exacts, and newer polymorphic signature methods
 * (eg: VarHandle.get(), ...).
 * 
 * Creating a call site for such a method goes to java code to create an invoker method that
 * implements type checking and the actual invocation of the payload. This node is basically a
 * bridge to the actual work.
 */
public class MHInvokeGenericNode extends MethodHandleIntrinsicNode {
    private final StaticObject appendix;
    private final boolean unbasic;
    @Child private DirectCallNode callNode;

    public MHInvokeGenericNode(Method method, StaticObject memberName, StaticObject appendix) {
        super(method);
        this.appendix = appendix;
        Method target = (Method) method.getMeta().HIDDEN_VMTARGET.getHiddenObject(memberName);
        // Call the invoker java code spun for us.
        if (method.getContext().getEspressoEnv().SplitMethodHandles) {
            this.callNode = DirectCallNode.create(target.forceSplit().getCallTarget());
        } else {
            this.callNode = DirectCallNode.create(target.getCallTarget());
        }
        this.unbasic = target.getReturnKind() != method.getReturnKind();
    }

    @Override
    public Object call(Object[] args) {
        // The quick node gave us the room to append the appendix.
        assert args[args.length - 1] == null;
        args[args.length - 1] = appendix;
        return callNode.call(args);
    }

    @SuppressWarnings("try")
    public static MHInvokeGenericNode create(EspressoLanguage language, Meta meta, Klass accessingKlass, Method method, Symbol<Symbol.Name> methodName, Symbol<Symbol.Signature> signature) {
        Klass callerKlass = accessingKlass == null ? meta.java_lang_Object : accessingKlass;
        StaticObject appendixBox = StaticObject.createArray(meta.java_lang_Object_array, new StaticObject[1], meta.getContext());
        // Ask java code to spin an invoker for us.
        try (EspressoLanguage.DisableSingleStepping ignored = meta.getLanguage().disableStepping()) {
            StaticObject memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkMethod.invokeDirect(
                    null,
                    callerKlass.mirror(), (int) REF_invokeVirtual,
                    method.getDeclaringKlass().mirror(), meta.toGuestString(methodName), meta.toGuestString(signature),
                    appendixBox);
            StaticObject appendix = appendixBox.get(language, 0);
            return new MHInvokeGenericNode(method, memberName, appendix);
        }
    }

    @Override
    public Object processReturnValue(Object obj, JavaKind kind) {
        if (unbasic) {
            return super.processReturnValue(obj, kind);
        }
        return obj;
    }
}
