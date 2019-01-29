package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.descriptors.TypeDescriptor;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import java.util.function.Consumer;

/**
 * Represents a resolved Espresso field.
 */
public final class Field implements ModifiersProvider {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    private final LinkedField linkedField;
    private final Klass holder;
    private final ByteString<Type> type;

    public ByteString<Type> getType() {
        return type;
    }


    public Klass getDeclaringKlass() {
        return holder;
    }

    public Field(LinkedField linkedField, Klass declaringKlass) {
        this.linkedField = linkedField;
        this.holder = getHolder();
        this.type = linkedField.getType(); // cache
    }

    // private final Klass holder;

    private final TypeDescriptor typeDescriptor;
    private final String name;
    private final int offset;
    private final short slot;
    private Attribute runtimeVisibleAnnotations;

    @CompilerDirectives.CompilationFinal private Klass type;

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int modifiers;

    Field(Klass holder, String name, int modifiers, int slot, Attribute runtimeVisibleAnnotations) {
        this.holder = holder;
        this.name = name;
        this.slot = (short) slot;
        this.modifiers = modifiers;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
        assert this.slot == slot;
    }

    public JavaKind getKind() {
        return TypeDescriptor.getJavaKind(getType());
    }

    public int getModifiers() {
        return modifiers & ModifiersProvider.jvmFieldModifiers();
    }

    public Klass getHolder() {
        return holder;
    }

    public String getName() {
        return name;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isInternal() {
        // No internal fields in Espresso (yet).
        return false;
    }

    @Override
    public String toString() {
        return "EspressoField<" + getHolder() + "." + getName() + ">";
    }

//    public int getFlags() {
//        return modifiers;
//    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }


    // region Meta.Field

    public Object get(StaticObject self) {
        InterpreterToVM vm = getHolder().getContext().getInterpreterToVM();
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
    }

    public void setNull(StaticObject self) {
        set(self, StaticObject.NULL);
    }

    public void set(StaticObject self, Object value) {
        InterpreterToVM vm = getHolder().getContext().getInterpreterToVM();
        switch (getKind()) {
            case Boolean:
                vm.setFieldBoolean((boolean) value, self, this);
                break;
            case Byte:
                vm.setFieldByte((byte) value, self, this);
                break;
            case Short:
                vm.setFieldShort((short) value, self, this);
                break;
            case Char:
                vm.setFieldChar((char) value, self, this);
                break;
            case Int:
                vm.setFieldInt((int) value, self, this);
                break;
            case Float:
                vm.setFieldFloat((float) value, self, this);
                break;
            case Long:
                vm.setFieldLong((long) value, self, this);
                break;
            case Double:
                vm.setFieldDouble((double) value, self, this);
                break;
            case Object:
                vm.setFieldObject((StaticObject) value, self, this);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }
}
