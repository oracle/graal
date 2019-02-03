package com.oracle.truffle.espresso.impl;

import java.lang.reflect.Modifier;

import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Represents a resolved Espresso field.
 */
public final class Field implements ModifiersProvider {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    private final LinkedField linkedField;
    private final ObjectKlass holder;
    private final ByteString<Type> type;
    private final ByteString<Name> name;
    private volatile Klass typeKlassCache;

    public ByteString<Type> getType() {
        return type;
    }

    public Field(LinkedField linkedField, ObjectKlass holder) {
        this.linkedField = linkedField;
        this.holder = holder;
        this.type = linkedField.getType();
        this.name = linkedField.getName();
    }

    // private Attribute runtimeVisibleAnnotations;

    public JavaKind getKind() {
        return Types.getJavaKind(getType());
    }

    public int getModifiers() {
        return linkedField.getFlags() & Modifier.fieldModifiers();
    }

    public ObjectKlass getHolder() {
        return holder;
    }

    public int getSlot() {
        return linkedField.getSlot();
    }

    public boolean isInternal() {
        // No internal fields in Espresso (yet).
        return false;
    }

    @Override
    public String toString() {
        return "EspressoField<" + getHolder() + "." + getName() + " -> " + getType() + ">";
    }

// public Attribute getRuntimeVisibleAnnotations() {
// return runtimeVisibleAnnotations;
// }

    public Object get(StaticObject self) {
        InterpreterToVM vm = getHolder().getContext().getInterpreterToVM();
        // @formatter:off
        // Checkstyle: stop
        switch (getKind()) {
            case Boolean : return vm.getFieldBoolean(self, this);
            case Byte    : return vm.getFieldByte(self, this);
            case Short   : return vm.getFieldShort(self, this);
            case Char    : return vm.getFieldChar(self, this);
            case Int     : return vm.getFieldInt(self, this);
            case Float   : return vm.getFieldFloat(self, this);
            case Long    : return vm.getFieldLong(self, this);
            case Double  : return vm.getFieldDouble(self, this);
            case Object  : return vm.getFieldObject(self, this);
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public void set(StaticObject self, Object value) {
        assert value != null;
        InterpreterToVM vm = getHolder().getContext().getInterpreterToVM();
        // @formatter:off
        // Checkstyle: stop
        switch (getKind()) {
            case Boolean : vm.setFieldBoolean((boolean) value, self, this); break;
            case Byte    : vm.setFieldByte((byte) value, self, this);       break;
            case Short   : vm.setFieldShort((short) value, self, this);     break;
            case Char    : vm.setFieldChar((char) value, self, this);       break;
            case Int     : vm.setFieldInt((int) value, self, this);         break;
            case Float   : vm.setFieldFloat((float) value, self, this);     break;
            case Long    : vm.setFieldLong((long) value, self, this);       break;
            case Double  : vm.setFieldDouble((double) value, self, this);   break;
            case Object  : vm.setFieldObject((StaticObject) value, self, this); break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public ByteString<Name> getName() {
        return name;
    }

//    public Field fieldRefConstant(RuntimeConstantPool pool, int index) {
//        CompilerDirectives.transferToInterpreterAndInvalidate();
//
//        FieldRefConstant fieldRefConstant = pool.fieldAt(index);
//
//        ByteString<Type> declaringKlass = fieldRefConstant.getDeclaringClass(pool);
//        Klass klass = getContext().getRegistries().loadKlass(declaringKlass, pool.getClassLoader());
//
//        // assert declaringInterface.isInterface();
//        ByteString<Name> name = fieldRefConstant.getName(pool);
//        ByteString<Type> type = fieldRefConstant.getType(pool);
//
//        Klass declaringClass = getContext().getRegistries().loadKlass(declaringKlass, pool.getClassLoader());
//
//        while (declaringClass != null) {
//            for (Field fi : declaringClass.getDeclaredFields()) {
//                if (fi.getName().equals(name) && type.equals(fi.getType())) {
//                    return fi;
//                }
//            }
//            declaringClass = declaringClass.getSuperclass();
//        }
//        throw EspressoError.shouldNotReachHere();
//    }

    public final Klass resolveTypeKlass() {
        Klass tk = typeKlassCache;
        if (tk == null) {
            synchronized (this) {
                tk = typeKlassCache;
                if (tk == null) {
                    tk = holder.getConstantPool().resolveType(getType(), linkedField.getParserField().getTypeIndex());
                    typeKlassCache = tk;
                    // linkedField.getParserField().getTypeIndex()
                    // throw EspressoError.unimplemented("Resolve field type in the holder CP");
                }
            }
        }
        return typeKlassCache;
    }
}
