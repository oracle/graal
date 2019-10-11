package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public class NullKlass extends Klass {

    private static NullKlass theKlass;

    private NullKlass(EspressoContext context) {
        super(context, Symbol.Name.Null, Symbol.Type.Null, null, ObjectKlass.EMPTY_ARRAY);
    }

    public static Klass getKlass(EspressoContext context) {
        if (theKlass == null) {
            theKlass = new NullKlass(context);
        }
        return theKlass;
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return null;
    }

    @Override
    public ConstantPool getConstantPool() {
        return null;
    }

    @Override
    public StaticObject getStatics() {
        return null;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void initialize() {

    }

    @Override
    public Klass getElementalType() {
        return null;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public Method[] getDeclaredConstructors() {
        return new Method[0];
    }

    @Override
    public Method[] getDeclaredMethods() {
        return new Method[0];
    }

    @Override
    public Field[] getDeclaredFields() {
        return new Field[0];
    }

    @Override
    public Klass getComponentType() {
        return null;
    }

    @Override
    public Field lookupFieldTable(int slot) {
        return null;
    }

    @Override
    public Field lookupStaticFieldTable(int slot) {
        return null;
    }

    @Override
    public Method lookupMethod(Symbol<Symbol.Name> methodName, Symbol<Symbol.Signature> signature, Klass accessingKlass) {
        return null;
    }

    @Override
    public Method vtableLookup(int vtableIndex) {
        return null;
    }

    @Override
    protected int getFlags() {
        return 0;
    }
}
