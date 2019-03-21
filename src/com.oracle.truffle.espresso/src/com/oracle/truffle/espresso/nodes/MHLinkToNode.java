/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

public class MHLinkToNode extends EspressoBaseNode {
    final int argCount;
    final int id;

    @Child LinkToNode node;

    public MHLinkToNode(Method method, int id) {
        super(method);
        this.id = id;
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
        this.node = LinkToNodeGen.create();
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        assert (getMethod().isStatic());
        return linkTo(frame.getArguments());
    }

    private Object linkTo(Object[] args) {
        assert args.length >= 1;
        StaticObjectImpl memberName = (StaticObjectImpl) args[args.length - 1];
        assert (memberName.getKlass().getType() == Symbol.Type.MemberName);

        Method target = (Method) memberName.getHiddenField("vmtarget");
        int refKind = Target_java_lang_invoke_MethodHandleNatives.getRefKind((int) memberName.getField(memberName.getKlass().getMeta().MNflags));
        assert ((refKind == Target_java_lang_invoke_MethodHandleNatives.REF_invokeStatic && !target.hasReceiver()) ||
                        (refKind != Target_java_lang_invoke_MethodHandleNatives.REF_invokeStatic && target.hasReceiver()));
        if (target.hasReceiver()) {
            assert args.length >= 2;
            // args of the form {receiver, arg1, arg2... , memberName}
            StaticObjectImpl receiver = (StaticObjectImpl) args[0];
            if (refKind == Target_java_lang_invoke_MethodHandleNatives.REF_invokeVirtual || refKind == Target_java_lang_invoke_MethodHandleNatives.REF_invokeInterface) {
                target = node.executeLookup(target.getName(), target.getRawSignature(), receiver.getKlass());
                // target = receiver.getKlass().lookupMethod(target.getName(),
                // target.getRawSignature());
            }
            return rebasic(target.invokeDirect(receiver, unbasic(args, target.getParsedSignature(), 1, argCount - 2)), Signatures.returnType(target.getParsedSignature()));
        } else {
            // args of the form {arg1, arg2... , memberName}
            return rebasic(target.invokeDirect(null, unbasic(args, target.getParsedSignature(), 0, argCount - 1)), Signatures.returnType(target.getParsedSignature()));
        }
    }

    @ExplodeLoop
    private static Object[] unbasic(Object[] args, Symbol<Type>[] targetSig, int from, int length) {
        Object[] res = new Object[length];
        for (int i = 0; i < length; i++) {
            Symbol<Type> t = Signatures.parameterType(targetSig, i);
            res[i] = unbasic(args[i + from], t);
        }
        return res;
    }

    // Transforms ints to sub-words
    private static Object unbasic(Object arg, Symbol<Type> t) {
        if (t == Type._boolean) {
            return ((int) arg != 0);
        } else if (t == Type._short) { // Unbox to cast.
            int value = (int) arg;
            return (short) value;
        } else if (t == Type._byte) {
            int value = (int) arg;
            return (byte) value;
        } else if (t == Type._char) {
            int value = (int) arg;
            return (char) value;
        } else {
            return arg;
        }
    }

    // Tranform sub-words to int
    private static Object rebasic(Object result, Symbol<Type> rtype) {
        if (rtype == Type._boolean) {
            return ((boolean) result) ? 1 : 0;
        } else if (rtype == Type._short) { // Unbox to cast.
            return (int) ((short) result);
        } else if (rtype == Type._byte) {
            return (int) ((byte) result);
        } else if (rtype == Type._char) {
            return (int) ((char) result);
        } else {
            return result;
        }
    }
}

abstract class LinkToNode extends Node {
    /**
     * Cache gets full really fast. Unlike regular invocations, we do not know the descriptor of the
     * target method at the site, and we do not know the klass of the receiver.
     *
     * This 2-factor cache lookup blows it up fairly fast.
     */

    static final int INLINE_CACHE_SIZE_LIMIT = 15;

    public abstract Method executeLookup(Symbol<Name> name, Symbol<Signature> signature, Klass defKlass);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"name == cachedName", "signature == cachedSignature", "defKlass == cachedKlass"})
    Method directLookup(Symbol<Name> name, Symbol<Signature> signature, Klass defKlass,
                    @Cached("name") Symbol<Name> cachedName,
                    @Cached("signature") Symbol<Symbol.Signature> cachedSignature,
                    @Cached("defKlass") Klass cachedKlass,
                    @Cached("cachedKlass.lookupMethod(cachedName, cachedSignature)") Method cachedMethod) {
        return cachedMethod;
    }

    @Specialization(replaces = "directLookup")
    Method normalLookup(Symbol<Name> name, Symbol<Symbol.Signature> signature, Klass defKlass) {
        return defKlass.lookupMethod(name, signature);
    }

    LinkToNode() {
    }
}