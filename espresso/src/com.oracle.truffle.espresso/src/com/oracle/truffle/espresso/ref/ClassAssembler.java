package com.oracle.truffle.espresso.ref;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_ABSTRACT;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_BRIDGE;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PRIVATE;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PROTECTED;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SUPER;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SYNTHETIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ALOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.ARETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.CHECKCAST;
import static com.oracle.truffle.api.impl.asm.Opcodes.GETFIELD;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKESPECIAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.PUTFIELD;
import static com.oracle.truffle.api.impl.asm.Opcodes.RETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.V1_8;

import com.oracle.truffle.api.impl.asm.AnnotationVisitor;
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.FieldVisitor;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Type;

final class ClassAssembler {

    /**
     * <pre>
     * package java.lang.ref;
     *
     * public abstract class PublicFinalReference&lt;T&gt; extends FinalReference&lt;T&gt; {
     *     protected PublicFinalReference(T referent, ReferenceQueue&lt;? super T&gt; q) {
     *         super(referent, q);
     *     }
     * }
     * </pre>
     */
    static byte[] assemblePublicFinalReference() {
        ClassWriter classWriter = new ClassWriter(0);
        MethodVisitor methodVisitor;

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_ABSTRACT, "java/lang/ref/PublicFinalReference", "<T:Ljava/lang/Object;>Ljava/lang/ref/FinalReference<TT;>;",
                        "java/lang/ref/FinalReference", null);

        {
            methodVisitor = classWriter.visitMethod(ACC_PROTECTED, "<init>", "(Ljava/lang/Object;Ljava/lang/ref/ReferenceQueue;)V", "(TT;Ljava/lang/ref/ReferenceQueue<-TT;>;)V", null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/ref/FinalReference", "<init>", "(Ljava/lang/Object;Ljava/lang/ref/ReferenceQueue;)V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(3, 3);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    /**
     * <pre>
     * package com.oracle.truffle.espresso.ref;
     *
     * import java.lang.ref.PublicFinalReference;
     * import java.lang.ref.ReferenceQueue;
     *
     * import com.oracle.truffle.espresso.runtime.StaticObject;
     * import com.oracle.truffle.espresso.substitutions.JavaType;
     *
     * final class EspressoFinalReference extends PublicFinalReference&lt;StaticObject&gt; implements EspressoReference {
     *
     *     private final StaticObject guestReference;
     *
     *     EspressoFinalReference(&#64;JavaType(internalName = &quot;Ljava/lang/ref/FinalReference;&quot;) StaticObject guestReference,
     *                     &#64;JavaType(Object.class) StaticObject referent, ReferenceQueue&lt;StaticObject&gt; queue) {
     *         super(referent, queue);
     *         this.guestReference = guestReference;
     *     }
     *
     *     &#64;Override
     *     public StaticObject getGuestReference() {
     *         return guestReference;
     *     }
     * }
     * </pre>
     */
    static byte[] assembleEspressoFinalReference() {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        classWriter.visit(V1_8, ACC_FINAL | ACC_SUPER, "com/oracle/truffle/espresso/ref/EspressoFinalReference",
                        "Ljava/lang/ref/PublicFinalReference<Lcom/oracle/truffle/espresso/runtime/StaticObject;>;Lcom/oracle/truffle/espresso/ref/EspressoReference;",
                        "java/lang/ref/PublicFinalReference", new String[]{"com/oracle/truffle/espresso/ref/EspressoReference"});

        {
            fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "guestReference", "Lcom/oracle/truffle/espresso/runtime/StaticObject;", null, null);
            fieldVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(0, "<init>",
                            "(Lcom/oracle/truffle/espresso/runtime/StaticObject;Lcom/oracle/truffle/espresso/runtime/StaticObject;Ljava/lang/ref/ReferenceQueue;)V",
                            "(Lcom/oracle/truffle/espresso/runtime/StaticObject;Lcom/oracle/truffle/espresso/runtime/StaticObject;Ljava/lang/ref/ReferenceQueue<Lcom/oracle/truffle/espresso/runtime/StaticObject;>;)V",
                            null);
            {
                annotationVisitor0 = methodVisitor.visitTypeAnnotation(369098752, null, "Lcom/oracle/truffle/espresso/substitutions/JavaType;", false);
                annotationVisitor0.visit("internalName", "Ljava/lang/ref/FinalReference;");
                annotationVisitor0.visitEnd();
            }
            {
                annotationVisitor0 = methodVisitor.visitTypeAnnotation(369164288, null, "Lcom/oracle/truffle/espresso/substitutions/JavaType;", false);
                annotationVisitor0.visit("value", Type.getType("Ljava/lang/Object;"));
                annotationVisitor0.visitEnd();
            }
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/ref/PublicFinalReference", "<init>", "(Ljava/lang/Object;Ljava/lang/ref/ReferenceQueue;)V", false);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitFieldInsn(PUTFIELD, "com/oracle/truffle/espresso/ref/EspressoFinalReference", "guestReference", "Lcom/oracle/truffle/espresso/runtime/StaticObject;");
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(3, 4);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getGuestReference", "()Lcom/oracle/truffle/espresso/runtime/StaticObject;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, "com/oracle/truffle/espresso/ref/EspressoFinalReference", "guestReference", "Lcom/oracle/truffle/espresso/runtime/StaticObject;");
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC, "get", "()Lcom/oracle/truffle/espresso/runtime/StaticObject;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/ref/PublicFinalReference", "get", "()Ljava/lang/Object;", false);
            methodVisitor.visitTypeInsn(CHECKCAST, "com/oracle/truffle/espresso/runtime/StaticObject");
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
