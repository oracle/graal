/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.classfile;

import static jdk.graal.compiler.replacements.classfile.Classfile.skipFully;
import static jdk.graal.compiler.replacements.classfile.ClassfileConstant.CONSTANT_Class;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.LibGraalSupport;
import jdk.graal.compiler.replacements.classfile.ClassfileConstant.ClassRef;
import jdk.graal.compiler.replacements.classfile.ClassfileConstant.ExecutableRef;
import jdk.graal.compiler.replacements.classfile.ClassfileConstant.FieldRef;
import jdk.graal.compiler.replacements.classfile.ClassfileConstant.MemberRef;
import jdk.graal.compiler.replacements.classfile.ClassfileConstant.Primitive;
import jdk.graal.compiler.replacements.classfile.ClassfileConstant.Utf8;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

@LibGraalSupport.HostedOnly
class ClassfileConstantPool implements ConstantPool, ConstantPoolPatch {

    final ClassfileConstant[] entries;
    final ClassfileBytecodeProvider context;

    public static class Bytecodes {
        public static final int LDC = 18; // 0x12
        public static final int LDC_W = 19; // 0x13
        public static final int LDC2_W = 20; // 0x14
        public static final int GETSTATIC = 178; // 0xB2
        public static final int PUTSTATIC = 179; // 0xB3
        public static final int GETFIELD = 180; // 0xB4
        public static final int PUTFIELD = 181; // 0xB5
        public static final int INVOKEVIRTUAL = 182; // 0xB6
        public static final int INVOKESPECIAL = 183; // 0xB7
        public static final int INVOKESTATIC = 184; // 0xB8
        public static final int INVOKEINTERFACE = 185; // 0xB9
        public static final int INVOKEDYNAMIC = 186; // 0xBA
        public static final int NEW = 187; // 0xBB
        public static final int NEWARRAY = 188; // 0xBC
        public static final int ANEWARRAY = 189; // 0xBD
        public static final int CHECKCAST = 192; // 0xC0
        public static final int INSTANCEOF = 193; // 0xC1
        public static final int MULTIANEWARRAY = 197; // 0xC5
    }

    ClassfileConstantPool(DataInputStream stream, ClassfileBytecodeProvider context) throws IOException {
        this.context = context;
        byte tag;

        int count = stream.readUnsignedShort();
        entries = new ClassfileConstant[count];

        int i = 1;
        while (i < count) {
            entries[i] = readConstant(stream);
            tag = entries[i].tag;

            if ((tag == ClassfileConstant.CONSTANT_Double) || (tag == ClassfileConstant.CONSTANT_Long)) {
                i += 2;
            } else {
                i += 1;
            }
        }
    }

    static final ClassfileConstant readConstant(DataInputStream stream) throws IOException {
        byte tag = stream.readByte();

        switch (tag) {
            case ClassfileConstant.CONSTANT_Class:
                return new ClassfileConstant.ClassRef(stream);
            case ClassfileConstant.CONSTANT_Fieldref:
                return new ClassfileConstant.FieldRef(stream);
            case ClassfileConstant.CONSTANT_Methodref:
                return new ClassfileConstant.MethodRef(stream);
            case ClassfileConstant.CONSTANT_InterfaceMethodref:
                return new ClassfileConstant.InterfaceMethodRef(stream);
            case ClassfileConstant.CONSTANT_String:
                return new ClassfileConstant.StringRef(stream);
            case ClassfileConstant.CONSTANT_Integer:
                return new ClassfileConstant.Primitive(tag, JavaConstant.forInt(stream.readInt()));
            case ClassfileConstant.CONSTANT_Float:
                return new ClassfileConstant.Primitive(tag, JavaConstant.forFloat(stream.readFloat()));
            case ClassfileConstant.CONSTANT_Long:
                return new ClassfileConstant.Primitive(tag, JavaConstant.forLong(stream.readLong()));
            case ClassfileConstant.CONSTANT_Double:
                return new ClassfileConstant.Primitive(tag, JavaConstant.forDouble(stream.readDouble()));
            case ClassfileConstant.CONSTANT_NameAndType:
                return new ClassfileConstant.NameAndType(stream);
            case ClassfileConstant.CONSTANT_Utf8:
                return new ClassfileConstant.Utf8(stream.readUTF());
            case ClassfileConstant.CONSTANT_MethodHandle:
                skipFully(stream, 3); // reference_kind, reference_index
                return new ClassfileConstant.Unsupported(tag, "CONSTANT_MethodHandle_info");
            case ClassfileConstant.CONSTANT_MethodType:
                skipFully(stream, 2); // descriptor_index
                return new ClassfileConstant.Unsupported(tag, "CONSTANT_MethodType_info");
            case ClassfileConstant.CONSTANT_Dynamic:
                skipFully(stream, 4); // bootstrap_method_attr_index, name_and_type_index
                return new ClassfileConstant.Unsupported(tag, "CONSTANT_Dynamic_info");
            case ClassfileConstant.CONSTANT_InvokeDynamic:
                skipFully(stream, 4); // bootstrap_method_attr_index, name_and_type_index
                return new ClassfileConstant.Unsupported(tag, "CONSTANT_InvokeDynamic_info");
            default:
                throw new ClassFormatError("Invalid constant pool tag: " + tag);
        }
    }

    @Override
    public int length() {
        return entries.length;
    }

    <T extends ClassfileConstant> T get(Class<T> c, int index) {
        return c.cast(entries[index]);
    }

    @Override
    public void loadReferencedType(int index, int opcode) {
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            throw new GraalError("INVOKEDYNAMIC not supported by " + ClassfileBytecodeProvider.class.getSimpleName());
        }
        entries[index].loadReferencedType(this, index, opcode);
    }

    @Override
    public JavaField lookupField(int index, ResolvedJavaMethod method, int opcode) {
        if (opcode == -1 || opcode == Bytecodes.LDC || opcode == Bytecodes.LDC_W) {
            if (getTag(index) != Tag.METHODHANDLE) {
                throw new IllegalArgumentException("constant pool tag at index " + index + " is " + getTag(index) + " but expected METHODHANDLE for an ldc opcode");
            }
            throw new GraalError("METHODHANDLE entries not supported in " + ClassfileBytecodeProvider.class.getSimpleName());
        }
        return get(FieldRef.class, index).resolve(this, opcode);
    }

    @Override
    public JavaMethod lookupMethod(int index, int opcode) {
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            throw new GraalError("INVOKEDYNAMIC not supported by" + ClassfileBytecodeProvider.class.getSimpleName());
        }
        if (opcode == -1 || opcode == Bytecodes.LDC || opcode == Bytecodes.LDC_W) {
            if (getTag(index) != Tag.METHODHANDLE) {
                throw new IllegalArgumentException("constant pool tag at index " + index + " is " + getTag(index) + " but expected METHODHANDLE for an ldc opcode");
            }
            throw new GraalError("METHODHANDLE entries not supported in " + ClassfileBytecodeProvider.class.getSimpleName());
        }
        return get(ExecutableRef.class, index).resolve(this, opcode);
    }

    @Override
    public JavaMethod lookupMethod(int index, int opcode, ResolvedJavaMethod caller) {
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            throw new GraalError("INVOKEDYNAMIC not supported by" + ClassfileBytecodeProvider.class.getSimpleName());
        }
        if (opcode == -1 || opcode == Bytecodes.LDC || opcode == Bytecodes.LDC_W) {
            if (getTag(index) != Tag.METHODHANDLE) {
                throw new IllegalArgumentException("constant pool tag at index " + index + " is " + getTag(index) + " but expected METHODHANDLE for an ldc opcode");
            }
            throw new GraalError("METHODHANDLE entries not supported in " + ClassfileBytecodeProvider.class.getSimpleName());
        }
        ResolvedJavaMethod result = get(ExecutableRef.class, index).resolve(this, opcode);
        if (result != null && result.getName().equals("getEventWriter") && result.getDeclaringClass().getName().equals("Ljdk/jfr/internal/event/EventWriterFactory;")) {
            // Only JFR transformed methods can call
            // jdk.jfr.internal.event.EventWriterFactory.getEventWriter(long)
            // and this ClassfileBytecode will never be used to load such a class.
            throw new IllegalAccessError("illegal access linking method 'jdk.jfr.internal.event.EventWriterFactory.getEventWriter(long)'");
        }
        return result;
    }

    @Override
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public JavaType lookupType(int index, int opcode) {
        return get(ClassRef.class, index).resolve(this);
    }

    @Override
    public JavaType lookupReferencedType(int index, int opcode) {
        int classIndex;
        switch (opcode) {
            case -1:
                Tag tag = getTag(index);
                classIndex = switch (tag) {
                    case DYNAMIC -> throw new GraalError("DYNAMIC entries not supported in " + ClassfileBytecodeProvider.class.getSimpleName());
                    case CLASS -> index;
                    case FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF -> get(MemberRef.class, index).classIndex;
                    case METHODHANDLE -> throw new GraalError("METHODHANDLE entries not supported in " + ClassfileBytecodeProvider.class.getSimpleName());
                    default -> throw JVMCIError.shouldNotReachHere("Unexpected constant pool tag: " + tag);
                };
                break;
            case Bytecodes.CHECKCAST:
            case Bytecodes.INSTANCEOF:
            case Bytecodes.NEW:
            case Bytecodes.ANEWARRAY:
            case Bytecodes.MULTIANEWARRAY:
                classIndex = index;
                break;
            case Bytecodes.LDC:
            case Bytecodes.LDC_W:
            case Bytecodes.LDC2_W:
                if (getTag(index) == Tag.DYNAMIC) {
                    throw new GraalError("DYNAMIC entries not supported in " + ClassfileBytecodeProvider.class.getSimpleName());
                }
                classIndex = index;
                break;
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTSTATIC:
            case Bytecodes.GETFIELD:
            case Bytecodes.PUTFIELD:
                FieldRef f = get(FieldRef.class, index);
                classIndex = f.classIndex;
                break;
            case Bytecodes.INVOKEVIRTUAL:
            case Bytecodes.INVOKESPECIAL:
            case Bytecodes.INVOKESTATIC:
            case Bytecodes.INVOKEINTERFACE:
                ExecutableRef e = get(ExecutableRef.class, index);
                classIndex = e.classIndex;
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected opcode: " + opcode); // ExcludeFromJacocoGeneratedReport
        }
        return get(ClassRef.class, classIndex).resolve(this);
    }

    @Override
    public String lookupUtf8(int index) {
        return ((Utf8) entries[index]).value;
    }

    @Override
    public Signature lookupSignature(int index, int opcode) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Object lookupConstant(int index) {
        return lookupConstant(index, true);
    }

    @Override
    public Object lookupConstant(int index, boolean resolve) {
        ClassfileConstant c = entries[index];
        if (c instanceof Primitive) {
            Primitive p = (Primitive) c;
            return p.value;
        }
        switch (c.tag) {
            case CONSTANT_Class:
                final int opcode = -1;
                return lookupType(index, opcode);
            case ClassfileConstant.CONSTANT_String:
                return ((ClassfileConstant.StringRef) c).getValue(this);
            default:
                throw new GraalError("Unexpected constant pool tag %s", c.tag);
        }
    }

    @Override
    public JavaConstant lookupAppendix(int index, int opcode) {
        if (opcode == Bytecodes.INVOKEVIRTUAL) {
            return null;
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(opcode); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int decodeIndex(int rawIndex, int opcode) {
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            return rawIndex >>> 16;
        }
        return rawIndex;
    }

    @Override
    public Tag getTag(int cpi) {
        if (cpi < 1 || cpi >= entries.length) {
            throw new IndexOutOfBoundsException(cpi);
        }
        ClassfileConstant c = entries[cpi];
        return c == null ? Tag.INVALID : Tag.fromValue(c.tag);
    }

    @Override
    public String lookupName(int cpi, int opcode) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int lookupMethodHandleReferenceKind(int cpi) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int lookupMethodHandleReferenceIndex(int cpi) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }
}
