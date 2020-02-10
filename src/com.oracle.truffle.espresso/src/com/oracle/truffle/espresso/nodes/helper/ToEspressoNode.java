package com.oracle.truffle.espresso.nodes.helper;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@GenerateUncached
abstract class ToEspressoNode extends Node {
    static final int LIMIT = 4;

    public abstract Object execute(Object value, Klass targetType) throws UnsupportedMessageException, UnsupportedTypeException;

    @SuppressWarnings("unused")
    @Specialization(guards = {"targetType == cachedTargetType"}, limit = "LIMIT")
    Object doCached(Object operand,
                    Klass targetType,
                    @CachedLibrary("operand") InteropLibrary interop,
                    @Cached("targetType") Klass cachedTargetType) throws UnsupportedMessageException, UnsupportedTypeException {
        return convertImpl(operand, cachedTargetType, interop);
    }

    @Specialization(replaces = "doCached")
    Object doGeneric(Object operand,
                    Klass targetType,
                    @CachedLibrary(limit = "0") InteropLibrary interop) throws UnsupportedMessageException, UnsupportedTypeException {
        return convertImpl(operand, targetType, interop);
    }

    private static Object convertImpl(Object value, Klass targetType, InteropLibrary interop) throws UnsupportedMessageException, UnsupportedTypeException {
        Symbol<Type> type = targetType.getType();
        if (value instanceof StaticObject) {
            if (targetType.getJavaKind() == JavaKind.Object) {
                if (StaticObject.isNull((StaticObject) value) || InterpreterToVM.instanceOf((StaticObject) value, targetType)) {
                    return value;
                }
            }
        }
        if (interop.isNumber(value)) {
            if (type == Type._byte) {
                return interop.asByte(value);
            } else if (type == Type._short) {
                return interop.asShort(value);
            } else if (type == Type._int) {
                return interop.asInt(value);
            } else if (type == Type._long) {
                return interop.asLong(value);
            } else if (type == Type._float) {
                return interop.asFloat(value);
            } else if (type == Type._double) {
                return interop.asDouble(value);
            }
        } else if (type == Type._boolean) {
            return interop.asBoolean(value);
        } else if (interop.isString(value)) {
            if (type == Type._char) {
                String str = interop.asString(value);
                if (str.length() == 1) {
                    return str.charAt(0);
                }
            } else if (targetType == targetType.getMeta().java_lang_String) {
                return targetType.getMeta().toGuestString(interop.asString(value));
            }
        } else if (targetType == targetType.getMeta().java_lang_String.array()) {
            // TODO(peterssen): Remove, this is a temporary workaround for passing arguments to
            // main.
            int length = (int) interop.getArraySize(value);
            StaticObject array = targetType.getComponentType().allocateReferenceArray(length);
            for (int i = 0; i < length; ++i) {
                Object elem = null;
                try {
                    elem = interop.readArrayElement(value, i);
                } catch (InvalidArrayIndexException e) {
                    throw UnsupportedMessageException.create();
                }
                StaticObject guestString = (StaticObject) convertImpl(elem, targetType.getMeta().java_lang_String, InteropLibrary.getFactory().create(elem));
                targetType.getInterpreterToVM().setArrayObject(guestString, i, array);
            }
            return array;
        }

        throw UnsupportedTypeException.create(new Object[]{value}, targetType.getName().toString());
    }
}