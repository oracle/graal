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
package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.fromMemberName;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives;
import com.oracle.svm.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;

/**
 * The runtime constant pool entry for
 * {@link com.oracle.svm.espresso.classfile.ConstantPool.Tag#INVOKEDYNAMIC} entries.
 * <p>
 * Since {@code invokedynamic} are linked per call site, this contains a mapping from call sites to
 * {@link CallSiteLink} objects that represent the linkage result.
 */
public final class ResolvedInvokeDynamicConstant {
    private final BootstrapMethodsAttribute.Entry bootstrapMethod;
    private final Symbol<Type>[] parsedInvokeSignature;
    private final Symbol<Name> nameSymbol;
    private volatile CallSiteLink[] callSiteLinks;

    ResolvedInvokeDynamicConstant(BootstrapMethodsAttribute.Entry bootstrapMethod, Symbol<Type>[] parsedInvokeSignature, Symbol<Name> name) {
        this.bootstrapMethod = bootstrapMethod;
        this.parsedInvokeSignature = parsedInvokeSignature;
        this.nameSymbol = name;
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
     * @return an index that can be stored in the available space in an {@code invokedynamic}'s
     *         constant pool index. It can be used to {@linkplain #getCallSiteLink(int) retrieve}
     *         the {@link CallSiteLink}.
     */
    public int link(RuntimeInterpreterConstantPool pool, Class<?> accessingKlass, InterpreterResolvedJavaMethod method, int bci) {
        int extraCPI = getCallSiteLinkExtraCPI(method, bci);
        if (extraCPI > 0) {
            return extraCPI;
        }
        // The call site linking must not happen under the lock, it is racy
        // However insertion should be done under a lock
        // see JVMS sect. 5.4.3.6 & ConstantPoolCache::set_dynamic_call
        CallSiteLink newLink = createCallSiteLink(pool, accessingKlass, method, bci);
        synchronized (this) {
            CallSiteLink[] existingLinks = callSiteLinks;
            if (existingLinks == null) {
                CallSiteLink[] newLinks = new CallSiteLink[1];
                newLinks[0] = newLink;
                callSiteLinks = newLinks;
                return 1;
            } else {
                int i = 0;
                for (; i < existingLinks.length; i++) {
                    CallSiteLink link = existingLinks[i];
                    if (link == null) {
                        existingLinks[i] = newLink;
                        return i + 1;
                    }
                    if (link.matchesCallSite(method, bci)) {
                        return i + 1;
                    }
                }
                // we didn't find an existing link nor an available slot
                // the array growth is limited by the max number of possible bcis for a method
                // (which is a power of 2).
                CallSiteLink[] newLinks = Arrays.copyOf(existingLinks, existingLinks.length * 2);
                newLinks[i] = newLink;
                callSiteLinks = newLinks;
                return i + 1;
            }
        }
    }

    private int getCallSiteLinkExtraCPI(InterpreterResolvedJavaMethod method, int bci) {
        CallSiteLink[] existingLinks = callSiteLinks;
        if (existingLinks != null) {
            for (int i = 0; i < existingLinks.length; i++) {
                CallSiteLink link = existingLinks[i];
                if (link == null) {
                    break;
                }
                if (link.matchesCallSite(method, bci)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    public CallSiteLink getCallSiteLink(int extraCPI) {
        return callSiteLinks[extraCPI - 1];
    }

    private CallSiteLink createCallSiteLink(RuntimeInterpreterConstantPool pool, Class<?> accessingKlass, InterpreterResolvedJavaMethod method, int bci) {
        // Per-callsite linking
        try {
            InterpreterResolvedObjectType accessingType = (InterpreterResolvedObjectType) DynamicHub.fromClass(accessingKlass).getInterpreterType();
            MethodHandle bootstrapmethodMethodHandle = pool.resolvedMethodHandleAt(bootstrapMethod.getBootstrapMethodRef(), accessingType);
            Object[] args = pool.getStaticArguments(bootstrapMethod, accessingType);

            String name = nameSymbol.toString();
            MethodType methodType = RuntimeInterpreterConstantPool.signatureToMethodType(parsedInvokeSignature, accessingType);
            /*
             * the 4 objects resolved above are not actually call-site specific. We don't cache them
             * to minimize footprint since most indy constant are only used by one call-site
             */

            Object[] appendix = new Object[1];
            Target_java_lang_invoke_MemberName memberName = Target_java_lang_invoke_MethodHandleNatives.linkCallSite(
                            accessingKlass,
                            bootstrapmethodMethodHandle,
                            name, methodType,
                            args,
                            appendix);
            Object unboxedAppendix = appendix[0];
            InterpreterResolvedJavaMethod invoker = fromMemberName(memberName);
            return new SuccessfulCallSiteLink(method, bci, invoker, unboxedAppendix);
        } catch (LinkageError e) {
            return new FailedCallSiteLink(method, bci, e);
        }
    }
}
