/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.jvmci.external;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.impl.jvmci.JVMCIConstantPoolUtils.safeTagAt;

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
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
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.constantpool.ResolvedConstant;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.jvmci.JVMCIConstantPoolUtils;
import com.oracle.truffle.espresso.impl.jvmci.JVMCIIndyData;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@ExportLibrary(InteropLibrary.class)
public class InteropConstantPoolWrapper implements TruffleObject {
    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> INVOCABLE_MEMBERS;
    private static final Set<String> READABLE_MEMBERS;

    static {
        String[] readableMembers = {
                        ReadMember.LENGTH,
                        ReadMember.NUM_INDY_ENTRIES,
        };
        String[] invocableMembers = {
                        InvokeMember.LOAD_REFERENCED_TYPE,
                        InvokeMember.LOOKUP_RESOLVED_METHOD,
                        InvokeMember.LOOKUP_RESOLVED_FIELD,
                        InvokeMember.LOOKUP_DESCRIPTOR,
                        InvokeMember.LOOKUP_NAME,
                        InvokeMember.LOOKUP_APPENDIX,
                        InvokeMember.LOOKUP_CONSTANT,
                        InvokeMember.LOOKUP_DYNAMIC_KIND,
                        InvokeMember.GET_TAG_BYTE_AT,
                        InvokeMember.LOOKUP_REFERENCED_TYPE,
                        InvokeMember.LOOKUP_TYPE,
                        InvokeMember.LOOKUP_BOOTSTRAP_METHOD_INVOCATION,
                        InvokeMember.LOOKUP_INDY_BOOTSTRAP_METHOD_INVOCATION,
        };
        String[] allMembers = new String[readableMembers.length + invocableMembers.length];
        System.arraycopy(readableMembers, 0, allMembers, 0, readableMembers.length);
        System.arraycopy(invocableMembers, 0, allMembers, readableMembers.length, invocableMembers.length);
        ALL_MEMBERS = new KeysArray<>(allMembers);
        READABLE_MEMBERS = Set.of(readableMembers);
        INVOCABLE_MEMBERS = Set.of(invocableMembers);
    }

    private final RuntimeConstantPool constantPool;

    public InteropConstantPoolWrapper(RuntimeConstantPool constantPool) {
        this.constantPool = constantPool;
    }

    @ExportMessage
    abstract static class ReadMember {
        static final String LENGTH = "length";
        static final String NUM_INDY_ENTRIES = "numIndyEntries";

        @Specialization(guards = "LENGTH.equals(member)")
        static int length(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.constantPool.length();
        }

        @Specialization(guards = "NUM_INDY_ENTRIES.equals(member)")
        static int numIndyEntries(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member,
                        @Bind Node node) {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            Meta meta = EspressoContext.get(node).getMeta();
            JVMCIIndyData indyData = JVMCIIndyData.maybeGetExisting(receiver.constantPool.getHolder(), meta);
            if (indyData == null) {
                return 0;
            }
            return indyData.getLocationCount();
        }

        @Fallback
        public static Object doUnknown(@SuppressWarnings("unused") InteropConstantPoolWrapper receiver, String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    abstract static class InvokeMember {
        static final String LOAD_REFERENCED_TYPE = "loadReferencedType";
        static final String LOOKUP_RESOLVED_METHOD = "lookupResolvedMethod";
        static final String LOOKUP_RESOLVED_FIELD = "lookupResolvedField";
        static final String LOOKUP_DESCRIPTOR = "lookupDescriptor";
        static final String LOOKUP_NAME = "lookupName";
        static final String LOOKUP_APPENDIX = "lookupAppendix";
        static final String LOOKUP_CONSTANT = "lookupConstant";
        static final String LOOKUP_DYNAMIC_KIND = "lookupDynamicKind";
        static final String GET_TAG_BYTE_AT = "getTagByteAt";
        static final String LOOKUP_REFERENCED_TYPE = "lookupReferencedType";
        static final String LOOKUP_TYPE = "lookupType";
        static final String LOOKUP_BOOTSTRAP_METHOD_INVOCATION = "lookupBootstrapMethodInvocation";
        static final String LOOKUP_INDY_BOOTSTRAP_METHOD_INVOCATION = "lookupIndyBootstrapMethodInvocation";

        @Specialization(guards = "LOAD_REFERENCED_TYPE.equals(member)")
        static boolean loadReferencedType(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            if (!(arguments[1] instanceof Integer opcode)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            Meta meta = EspressoContext.get(node).getMeta();
            RuntimeConstantPool cp = receiver.constantPool;
            return JVMCIConstantPoolUtils.loadReferencedType0(cpi, opcode, cp, cp.getHolder(), meta);
        }

        @Specialization(guards = "LOOKUP_RESOLVED_METHOD.equals(member)")
        static Object lookupResolvedMethod(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Exclusive InteropLibrary interopLibrary,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 3) {
                arityError.enter(node);
                throw ArityException.create(3, 3, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            if (!(arguments[1] instanceof Integer opcode)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            Method caller;
            if ((arguments[2] instanceof Method callerMethod)) {
                caller = callerMethod;
            } else if (interopLibrary.isNull(arguments[2])) {
                caller = null;
            } else {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            Method result = JVMCIConstantPoolUtils.lookupResolvedMethod(receiver.constantPool, cpi, opcode, caller, context);
            if (result == null) {
                return StaticObject.NULL;
            }
            return result;
        }

        @Specialization(guards = "LOOKUP_RESOLVED_FIELD.equals(member)")
        static Object lookupResolvedField(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary(limit = "1") @Exclusive InteropLibrary interopLibrary,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 3) {
                arityError.enter(node);
                throw ArityException.create(3, 3, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            if (!(arguments[1] instanceof Integer opcode)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            Method method;
            if ((arguments[2] instanceof Method m)) {
                method = m;
            } else if (interopLibrary.isNull(arguments[2])) {
                method = null;
            } else {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            Field result = JVMCIConstantPoolUtils.lookupResolvedField(receiver.constantPool, cpi, method, opcode, context);
            if (result == null) {
                return StaticObject.NULL;
            }
            return result;
        }

        @Specialization(guards = "LOOKUP_DESCRIPTOR.equals(member)")
        static String lookupDescriptor(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            return JVMCIConstantPoolUtils.lookupDescriptor(receiver.constantPool, cpi, context).toString();
        }

        @Specialization(guards = "LOOKUP_NAME.equals(member)")
        static String lookupName(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            return JVMCIConstantPoolUtils.lookupName(receiver.constantPool, cpi, context).toString();
        }

        @Specialization(guards = "LOOKUP_APPENDIX.equals(member)")
        static StaticObject lookupAppendix(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer index)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            if (!(arguments[1] instanceof Integer opcode)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            return JVMCIConstantPoolUtils.lookupAppendix(receiver.constantPool, index, opcode, context);
        }

        @Specialization(guards = "LOOKUP_DYNAMIC_KIND.equals(member)")
        static int lookupDynamicKind(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            return TypeSymbols.getJavaKind(receiver.constantPool.dynamicType(cpi)).getTypeChar();
        }

        @Specialization(guards = "LOOKUP_CONSTANT.equals(member)")
        static Object lookupConstant(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError,
                        @Cached @Exclusive InlinedBranchProfile indexError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1 && arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(1, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            boolean resolve = false;
            if (arguments.length > 1) {
                if (!(arguments[1] instanceof Boolean shouldDesolve)) {
                    typeError.enter(node);
                    throw UnsupportedTypeException.create(arguments);
                }
                resolve = shouldDesolve;
            }
            if (cpi < 0 || cpi >= receiver.constantPool.length()) {
                indexError.enter(node);
                Meta meta = EspressoContext.get(node).getMeta();
                throw meta.throwIndexOutOfBoundsExceptionBoundary("invalid cpi", cpi, receiver.constantPool.length());
            }
            return switch (receiver.constantPool.tagAt(cpi)) {
                case INTEGER -> receiver.constantPool.intAt(cpi);
                case LONG -> receiver.constantPool.longAt(cpi);
                case FLOAT -> receiver.constantPool.floatAt(cpi);
                case DOUBLE -> receiver.constantPool.doubleAt(cpi);
                case STRING -> receiver.constantPool.resolvedStringAt(cpi);
                case METHODHANDLE, METHODTYPE -> {
                    ResolvedConstant resolvedConstant;
                    if (resolve) {
                        resolvedConstant = receiver.constantPool.resolvedAt(receiver.constantPool.getHolder(), cpi);
                    } else {
                        Meta meta = EspressoContext.get(node).getMeta();
                        resolvedConstant = receiver.constantPool.peekResolvedOrNull(cpi, meta);
                    }
                    if (resolvedConstant == null) {
                        yield StaticObject.NULL;
                    }
                    yield resolvedConstant.value();
                }
                case DYNAMIC -> {
                    Meta meta = EspressoContext.get(node).getMeta();
                    ResolvedConstant resolvedConstant = receiver.constantPool.peekResolvedOrNull(cpi, meta);
                    if (resolvedConstant == null) {
                        yield StaticObject.NULL;
                    }
                    yield switch (TypeSymbols.getJavaKind(receiver.constantPool.dynamicType(cpi))) {
                        case Boolean -> ((Integer) resolvedConstant.value() != 0);
                        case Byte -> (byte) (int) resolvedConstant.value();
                        case Short -> (short) (int) resolvedConstant.value();
                        case Char -> (char) (int) resolvedConstant.value();
                        case Int -> (int) resolvedConstant.value();
                        case Float -> (float) resolvedConstant.value();
                        case Double -> (double) resolvedConstant.value();
                        case Long -> (long) resolvedConstant.value();
                        case Object -> resolvedConstant.value();
                        default -> throw meta.throwIllegalArgumentExceptionBoundary();
                    };
                }
                default -> false;
            };
        }

        @Specialization(guards = "GET_TAG_BYTE_AT.equals(member)")
        static byte getTagByteAt(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof Integer cpi)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            Meta meta = EspressoContext.get(node).getMeta();
            return safeTagAt(receiver.constantPool, cpi, meta).getValue();
        }

        @Specialization(guards = "LOOKUP_REFERENCED_TYPE.equals(member)")
        static Object lookupReferencedType(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer index)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            if (!(arguments[1] instanceof Integer opcode)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            Object result = JVMCIConstantPoolUtils.lookupReferencedType(receiver.constantPool, index, opcode, context);
            if (result instanceof Klass klass) {
                return new TypeWrapper(klass);
            } else {
                ByteSequence type = (ByteSequence) result;
                return type.toString();
            }
        }

        @Specialization(guards = "LOOKUP_TYPE.equals(member)")
        static Object lookupType(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer index)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            if (safeTagAt(receiver.constantPool, index, context.getMeta()) == ConstantPool.Tag.CLASS) {
                ResolvedConstant resolvedConstant = receiver.constantPool.peekResolvedOrNull(index, context.getMeta());
                if (resolvedConstant == null || !resolvedConstant.isSuccess()) {
                    return TypeSymbols.nameToType(receiver.constantPool.className(index)).toString();
                }
                Klass klass = (Klass) resolvedConstant.value();
                return new TypeWrapper(klass);
            }
            if (safeTagAt(receiver.constantPool, index, context.getMeta()) == ConstantPool.Tag.UTF8) {
                return TypeSymbols.nameToType(receiver.constantPool.utf8At(index)).toString();
            }
            throw context.getMeta().throwIllegalArgumentExceptionBoundary();
        }

        @Specialization(guards = "LOOKUP_BOOTSTRAP_METHOD_INVOCATION.equals(member)")
        static Object lookupBootstrapMethodInvocation(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 2) {
                arityError.enter(node);
                throw ArityException.create(2, 2, arguments.length);
            }
            if (!(arguments[0] instanceof Integer index)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            if (!(arguments[1] instanceof Integer opcode)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            InteropBootstrapMethodInvocation builder = new InteropBootstrapMethodInvocation();
            JVMCIConstantPoolUtils.lookupBootstrapMethodInvocation(receiver.constantPool, index, opcode, context, builder);
            if (builder.isInitialised()) {
                return builder;
            }
            return StaticObject.NULL;
        }

        @Specialization(guards = "LOOKUP_INDY_BOOTSTRAP_METHOD_INVOCATION.equals(member)")
        static Object lookupIndyBootstrapMethodInvocation(InteropConstantPoolWrapper receiver, @SuppressWarnings("unused") String member, Object[] arguments,
                        @Bind Node node,
                        @Cached @Exclusive InlinedBranchProfile typeError,
                        @Cached @Exclusive InlinedBranchProfile arityError) throws ArityException, UnsupportedTypeException {
            assert EspressoLanguage.get(node).isExternalJVMCIEnabled();
            if (arguments.length != 1) {
                arityError.enter(node);
                throw ArityException.create(1, 1, arguments.length);
            }
            if (!(arguments[0] instanceof Integer index)) {
                typeError.enter(node);
                throw UnsupportedTypeException.create(arguments);
            }
            EspressoContext context = EspressoContext.get(node);
            JVMCIIndyData indyData = JVMCIIndyData.getExisting(receiver.constantPool.getHolder(), context.getMeta());
            if (index < 0 || index >= indyData.getLocationCount()) {
                context.getMeta().throwIndexOutOfBoundsExceptionBoundary("Invalid site index", index, indyData.getLocationCount());
            }
            int indyCpi = indyData.recoverFullCpi(index);
            InteropBootstrapMethodInvocation builder = new InteropBootstrapMethodInvocation();
            JVMCIConstantPoolUtils.lookupBootstrapMethodInvocation(receiver.constantPool, indyCpi, INVOKEDYNAMIC, context, builder);
            assert builder.isInitialised();
            return builder;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doUnknown(InteropConstantPoolWrapper receiver, String member, Object[] arguments) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    public boolean isMemberReadable(String member) {
        return READABLE_MEMBERS.contains(member);
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public boolean isMemberInvocable(String member) {
        return INVOCABLE_MEMBERS.contains(member);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return ALL_MEMBERS;
    }
}
