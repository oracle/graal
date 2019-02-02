package com.oracle.truffle.espresso.impl;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

// Structural shareable klass (superklass in superinterfaces resolved and linked)
// contains shape, field locations.
// Klass shape, vtable and field locations can be computed at the structural level.
public final class LinkedKlass {

    public static final LinkedKlass[] EMPTY_ARRAY = new LinkedKlass[0];

//    public static final LinkedKlass BOOLEAN = new LinkedKlass(ParserKlass.BOOLEAN, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass BYTE = new LinkedKlass(ParserKlass.BYTE, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass CHAR = new LinkedKlass(ParserKlass.CHAR, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass SHORT = new LinkedKlass(ParserKlass.SHORT, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass INT = new LinkedKlass(ParserKlass.INT, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass FLOAT = new LinkedKlass(ParserKlass.FLOAT, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass DOUBLE = new LinkedKlass(ParserKlass.DOUBLE, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass LONG = new LinkedKlass(ParserKlass.LONG, null, LinkedKlass.EMPTY_ARRAY);
//    public static final LinkedKlass VOID = new LinkedKlass(ParserKlass.VOID, null, LinkedKlass.EMPTY_ARRAY);
//
//    private static LinkedKlass arrayOf(LinkedKlass base) {
//        return new LinkedKlass(null, )
//    }
//
//
//    public static LinkedKlass primitive(JavaKind kind) {
//        assert kind.isPrimitive() : "not a primitive kind";
//        // @formatter:off
//        // Checkstyle: stop
//        switch (kind) {
//            case Boolean : return BOOLEAN;
//            case Byte    : return BYTE;
//            case Short   : return SHORT;
//            case Char    : return CHAR;
//            case Int     : return INT;
//            case Float   : return FLOAT;
//            case Long    : return LONG;
//            case Double  : return DOUBLE;
//            case Void    : return VOID;
//            default      : throw EspressoError.shouldNotReachHere("not a primitive kind");
//        }
//        // @formatter:on
//        // Checkstyle: resume
//    }

    private final ParserKlass parserKlass;

    // Linked structural references.
    private final LinkedKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final LinkedKlass[] interfaces;

    @CompilationFinal(dimensions = 1) //
    private final LinkedMethod[] methods;

    @CompilationFinal(dimensions = 1) //
    private final LinkedField[] fields; // Field slots already computed.

    protected LinkedMethod[] getLinkedMethods() {
        return methods;
    }

    protected LinkedField[] getLinkedFields() {
        return fields;
    }

    protected final int instanceFieldCount;
    protected final int staticFieldCount;

    public LinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {

        assert Arrays.stream(interfaces).allMatch(i -> Modifier.isInterface(i.getFlags()));
        assert superKlass == null || !Modifier.isInterface(superKlass.getFlags());

        int instanceFieldSlot = superKlass != null ? superKlass.instanceFieldCount : 0;
        int staticFieldSlot = 0;

        final int methodCount = parserKlass.getMethods().length;
        final int fieldCount = parserKlass.getFields().length;

        LinkedField[] linkedFields = new LinkedField[fieldCount];
        LinkedMethod[] linkedMethods = new LinkedMethod[methodCount];

        for (int i = 0; i < fieldCount; ++i) {
            ParserField parserField = parserKlass.getFields()[i];

            int slot = Modifier.isStatic(parserField.getFlags())
                            ? staticFieldSlot++
                            : instanceFieldSlot++;

            linkedFields[i] = new LinkedField(parserField, this, slot);
        }

        for (int i = 0; i < methodCount; ++i) {
            ParserMethod parserMethod = parserKlass.getMethods()[i];
            // TODO(peterssen): Methods with custom constant pool should spawned here, but not
            // supported.
            linkedMethods[i] = new LinkedMethod(parserMethod, this);
        }

        this.parserKlass = parserKlass;
        this.superKlass = superKlass;
        this.interfaces = interfaces;
        this.staticFieldCount = staticFieldSlot;
        this.instanceFieldCount = instanceFieldSlot;
        this.fields = linkedFields;
        this.methods = linkedMethods;
    }

    public boolean equals(LinkedKlass other) {
        return parserKlass == other.parserKlass &&
                        superKlass == other.superKlass &&
                        /* reference equals */ Arrays.equals(interfaces, other.interfaces);
    }

    int getFlags() {
        return parserKlass.getFlags();
    }

    ConstantPool getConstantPool() {
        return parserKlass.getConstantPool();
    }

    ByteString<Type> getType() {
        return parserKlass.getType();
    }
}
