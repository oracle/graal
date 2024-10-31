/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.constantpool;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.util.Arrays;

public final class ResolvedInvokeDynamicConstant implements LinkableInvokeDynamicConstant {
    private final BootstrapMethodsAttribute.Entry bootstrapMethod;
    private final Symbol<Type>[] parsedInvokeSignature;
    private final Symbol<Name> nameSymbol;
    private volatile CallSiteLink[] callSiteLinks;

    public ResolvedInvokeDynamicConstant(BootstrapMethodsAttribute.Entry bootstrapMethod, Symbol<Type>[] parsedInvokeSignature, Symbol<Name> name) {
        this.bootstrapMethod = bootstrapMethod;
        this.parsedInvokeSignature = parsedInvokeSignature;
        this.nameSymbol = name;
    }

    @Override
    public Object value() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Use indy.link() rather than Resolved.value()");
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    /**
     * The call site linking operation must be executed once per {@code invokedynamic} instruction,
     * rather than once per {@code invokedynamic_constant}.
     * <p>
     * Furthermore, a previously failed call site linking from the constant pool must immediately
     * fail again on subsequent linking operations, independently of the invokedynamic instruction
     * involved.
     * <p>
     * For example:
     *
     * <pre>
     * method1:
     *     0: invokedynamic #0 // calls MHN.linkCallSite successfully
     *     4: invokedynamic #0 // Also calls MHM.linkCallSite, resulting in a *different* CallSite
     *
     * method2:
     *     0: invokedynamic #1 // Fails during MHN.linkCallSite upcall
     *     4: invokedynamic #1 // Immediately fails without calling MHN.linkCallSite
     * </pre>
     *
     * @see RuntimeConstantPool#linkInvokeDynamic(ObjectKlass, int, int,
     *      com.oracle.truffle.espresso.impl.Method)
     */
    @Override
    public CallSiteLink link(RuntimeConstantPool pool, ObjectKlass accessingKlass, int thisIndex, Method method, int bci) {
        int existingIndex = findCallSiteLinkIndex(method, bci);
        if (existingIndex >= 0) {
            return getCallSiteLink(existingIndex);
        }
        // The call site linking must not happen under the lock, it is racy
        // However insertion should be done under a lock
        // see JVMS sect. 5.4.3.6 & ConstantPoolCache::set_dynamic_call
        CallSiteLink newLink = createCallSiteLink(pool, accessingKlass, thisIndex, method, bci);
        synchronized (this) {
            CallSiteLink[] existingLinks = callSiteLinks;
            if (existingLinks == null) {
                CallSiteLink[] newLinks = new CallSiteLink[1];
                newLinks[0] = newLink;
                callSiteLinks = newLinks;
                return newLink;
            } else {
                int i = 0;
                for (; i < existingLinks.length; i++) {
                    CallSiteLink link = existingLinks[i];
                    if (link == null) {
                        existingLinks[i] = newLink;
                        return existingLinks[i];
                    }
                    if (link.matchesCallSite(method, bci)) {
                        return link;
                    }
                }
                // we didn't find an existing link nor an available slot
                // the array growth is limited by the max number of possible bcis for a method
                // (which is a power of 2).
                CallSiteLink[] newLinks = Arrays.copyOf(existingLinks, existingLinks.length * 2);
                newLinks[i] = newLink;
                callSiteLinks = newLinks;
                return newLink;
            }
        }
    }

    private CallSiteLink createCallSiteLink(RuntimeConstantPool pool, ObjectKlass accessingKlass, int thisIndex, Method method, int bci) {
        // Per-callsite linking
        CompilerAsserts.neverPartOfCompilation();
        Meta meta = accessingKlass.getMeta();
        try {
            StaticObject bootstrapmethodMethodHandle = pool.getMethodHandle(bootstrapMethod, accessingKlass);
            StaticObject[] args = pool.getStaticArguments(bootstrapMethod, accessingKlass);

            StaticObject name = meta.toGuestString(nameSymbol);
            StaticObject methodType = Resolution.signatureToMethodType(parsedInvokeSignature, accessingKlass, meta.getContext().getJavaVersion().java8OrEarlier(), meta);
            /*
             * the 4 objects resolved above are not actually call-site specific. We don't cache them
             * to minimize footprint since most indy constant are only used by one call-site
             */

            StaticObject appendix = StaticObject.createArray(meta.java_lang_Object_array, new StaticObject[1], meta.getContext());
            StaticObject memberName;
            if (meta.getJavaVersion().varHandlesEnabled() && !meta.getJavaVersion().java19OrLater()) {
                memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkCallSite.invokeDirectStatic(
                                accessingKlass.mirror(),
                                thisIndex,
                                bootstrapmethodMethodHandle,
                                name, methodType,
                                StaticObject.createArray(meta.java_lang_Object_array, args.clone(), meta.getContext()),
                                appendix);
            } else {
                memberName = (StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkCallSite.invokeDirectStatic(
                                accessingKlass.mirror(),
                                bootstrapmethodMethodHandle,
                                name, methodType,
                                StaticObject.createArray(meta.java_lang_Object_array, args.clone(), meta.getContext()),
                                appendix);
            }
            StaticObject unboxedAppendix = appendix.get(meta.getLanguage(), 0);

            return new CallSiteLink(method, bci, memberName, unboxedAppendix, parsedInvokeSignature);
        } catch (EspressoException e) {
            throw new CallSiteLinkingFailure(e);
        }
    }

    public int findCallSiteLinkIndex(Method method, int bci) {
        CallSiteLink[] existingLinks = callSiteLinks;
        if (existingLinks == null) {
            return -1;
        }
        for (int i = 0; i < existingLinks.length; i++) {
            CallSiteLink link = existingLinks[i];
            if (link == null) {
                break;
            }
            if (link.matchesCallSite(method, bci)) {
                return i;
            }
        }
        return -1;
    }

    public CallSiteLink getCallSiteLink(int index) {
        return callSiteLinks[index];
    }

    @Override
    public int getBootstrapMethodAttrIndex() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("String already resolved");
    }

    @Override
    public Symbol<Name> getName(ConstantPool pool) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("String already resolved");
    }

    @Override
    public Symbol<Signature> getSignature(ConstantPool pool) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("String already resolved");
    }

    @Override
    public NameAndTypeConstant getNameAndType(ConstantPool pool) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("String already resolved");
    }

    @Override
    public Symbol<Type>[] getParsedSignature() {
        return parsedInvokeSignature;
    }
}
