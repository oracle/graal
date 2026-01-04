/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.jvmci.external.JVMCIInteropHelper;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.commands.AddPathToBindingsNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Wrapper to expose guest class loading as an non-enumerable interop map.
 *
 * <p>
 * Classes cannot be enumerated; and in this implementation, not even the ones already loaded. e.g.
 * {@link InteropLibrary#getMembers(Object) Peeking all memebers} will return an empty interop
 * collection. <br>
 * {@link InteropLibrary#readMember(Object, String) Reading a member} will trigger class loading; it
 * is equivalent to calling {@link Class#forName(String, boolean, ClassLoader)} with the provided
 * guest class loader. <br>
 * {@link ClassNotFoundException} is translated into interop's {@link UnknownIdentifierException
 * member not found}, all other guest exceptions thrown during class loading will be propagated.
 *
 * <p>
 * If properly set up, these bindings will expose the {@code addPath} invocable member, which allows
 * to add a new path for the underlying class loader to load from. This invocation takes a single
 * {@link InteropLibrary#isString(Object) interop string} as argument, the path to add.
 */
@ExportLibrary(InteropLibrary.class)
public final class EspressoBindings implements TruffleObject {
    public static final String JAVA_VM = "<JavaVM>";
    public static final String JVMCI_HELPER = "<JVMCI_HELPER>";
    public static final String ADD_PATH = "addPath";

    final boolean useBindingsLoader;

    boolean withNativeJavaVM;
    boolean withJVMCIHelper;

    public EspressoBindings(boolean withNativeJavaVM, boolean useBindingsLoader, boolean withJVMCIHelper) {
        this.withNativeJavaVM = withNativeJavaVM;
        this.useBindingsLoader = useBindingsLoader;
        this.withJVMCIHelper = withJVMCIHelper;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        List<String> members = new ArrayList<>(3);
        if (withNativeJavaVM) {
            members.add(JAVA_VM);
        }
        if (withJVMCIHelper) {
            members.add(JVMCI_HELPER);
        }
        if (useBindingsLoader) {
            members.add(ADD_PATH);
        }
        return new KeysArray<>(members.toArray(new String[0]));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @ExportMessage(name = "hasMemberReadSideEffects")
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String member) {
        if (JAVA_VM.equals(member)) {
            return withNativeJavaVM;
        }
        if (JVMCI_HELPER.equals(member)) {
            return withJVMCIHelper;
        }
        if (ADD_PATH.equals(member)) {
            return useBindingsLoader;
        }
        // TODO(peterssen): Validate proper class name.
        return true;
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        if (ADD_PATH.equals(member)) {
            return useBindingsLoader;
        }
        return false;
    }

    @ExportMessage
    static Object readMember(EspressoBindings receiver, String member,
                    @Bind Node node,
                    @CachedLibrary("this") InteropLibrary self,
                    @Exclusive @Cached InlinedBranchProfile error) throws UnknownIdentifierException {
        if (!receiver.isMemberReadable(member)) {
            error.enter(node);
            throw UnknownIdentifierException.create(member);
        }
        EspressoContext context = EspressoContext.get(self);
        if (receiver.withNativeJavaVM && JAVA_VM.equals(member)) {
            return context.getVM().getJavaVM();
        }
        if (receiver.withJVMCIHelper && JVMCI_HELPER.equals(member)) {
            assert context.getLanguage().isExternalJVMCIEnabled();
            return new JVMCIInteropHelper(context);
        }
        if (receiver.useBindingsLoader && ADD_PATH.equals(member)) {
            return new AddPathToBindingsNode.InvocableAddToBindings();
        }
        Meta meta = context.getMeta();
        try {
            StaticObject clazz = (StaticObject) meta.java_lang_Class_forName_String_boolean_ClassLoader.invokeDirectStatic(meta.toGuestString(member), false, context.getBindingsLoader());
            return clazz.getMirrorKlass(meta);
        } catch (EspressoException e) {
            error.enter(node);
            if (InterpreterToVM.instanceOf(e.getGuestException(), meta.java_lang_ClassNotFoundException)) {
                throw UnknownIdentifierException.create(member, e);
            }
            throw e; // exception during class loading
        }
    }

    @ExportMessage
    static Object invokeMember(EspressoBindings receiver, String member, Object[] arguments,
                    @Bind Node node,
                    @Cached AddPathToBindingsNode addPathToBindingsNode,
                    @Exclusive @Cached InlinedBranchProfile error) throws UnknownIdentifierException, ArityException, UnsupportedTypeException {
        if (!receiver.isMemberInvocable(member)) {
            error.enter(node);
            throw UnknownIdentifierException.create(member);
        }
        if (receiver.useBindingsLoader && ADD_PATH.equals(member)) {
            addPathToBindingsNode.execute(arguments);
            return StaticObject.NULL;
        }
        error.enter(node);
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberRemovable(String member) {
        if (withNativeJavaVM && JAVA_VM.equals(member)) {
            return true;
        }
        if (withJVMCIHelper && JVMCI_HELPER.equals(member)) {
            return true;
        }
        return false;
    }

    @ExportMessage
    static void removeMember(EspressoBindings receiver, String member,
                    @Bind Node node,
                    @Exclusive @Cached InlinedBranchProfile error) throws UnknownIdentifierException {
        if (!receiver.isMemberRemovable(member)) {
            error.enter(node);
            throw UnknownIdentifierException.create(member);
        }
        if (receiver.withNativeJavaVM && JAVA_VM.equals(member)) {
            receiver.withNativeJavaVM = false;
        }
        if (receiver.withJVMCIHelper && JVMCI_HELPER.equals(member)) {
            receiver.withJVMCIHelper = false;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    String getLanguageId() {
        return EspressoLanguage.ID;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        if (useBindingsLoader) {
            return "espresso-bindings-classloader";
        }
        return "espresso-system-classloader";
    }
}
