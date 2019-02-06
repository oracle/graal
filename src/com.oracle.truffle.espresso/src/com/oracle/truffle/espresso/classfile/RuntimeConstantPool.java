package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class RuntimeConstantPool extends ConstantPool {

    private final EspressoContext context;
    private final ConstantPool pool;
    private final StaticObject classLoader;

    @CompilationFinal(dimensions = 1) //
    private final Resolvable.ResolvedConstant[] constants;

    public RuntimeConstantPool(EspressoContext context, ConstantPool pool, StaticObject classLoader) {
        this.context = context;
        this.pool = pool;
        constants = copyResolvedConstant(pool); // utf8, int, floats..., others->null
        this.classLoader = classLoader;
    }

    private static Resolvable.ResolvedConstant[] copyResolvedConstant(ConstantPool pool) {
        return new Resolvable.ResolvedConstant[pool.length()];
    }

    @Override
    public int length() {
        return pool.length();
    }

    @Override
    public PoolConstant at(int index, String description) {
        return pool.at(index, description);
    }

    private Resolvable.ResolvedConstant resolvedAt(Klass accessingKlass, int index, String description) {
        Resolvable.ResolvedConstant c = constants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                // fence += 1;
                // FIXME(peterssen): Add memory fence for array read.
                c = constants[index];
                if (c == null) {
                    constants[index] = c = ((Resolvable) pool.at(index, description)).resolve(this, index, accessingKlass);
                }
            }
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    public StaticObject resolvedStringAt(int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(null, index, "string");
        return (StaticObject) resolved.value();
    }

    @SuppressWarnings("unchecked")
    public Klass resolvedKlassAt(Klass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "klass");
        return (Klass) resolved.value();
    }

    @SuppressWarnings("unchecked")
    public Field resolvedFieldAt(Klass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "field");
        return (Field) resolved.value();
    }

    @SuppressWarnings("unchecked")
    public Method resolvedMethodAt(Klass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method");
        return (Method) resolved.value();
    }

    public StaticObject getClassLoader() {
        return classLoader;
    }

    public EspressoContext getContext() {
        return context;
    }
}
