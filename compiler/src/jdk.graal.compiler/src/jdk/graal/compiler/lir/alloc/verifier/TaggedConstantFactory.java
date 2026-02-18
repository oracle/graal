package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Factory to create a tagged constant from an existing constant.
 */
public class TaggedConstantFactory {
    protected int lastTag;

    public TaggedConstantFactory(int initialTag) {
        this.lastTag = initialTag;
    }

    public TaggedConstantFactory() {
        this(0);
    }

    public Constant createNew(Constant constant) {
        return switch (constant) {
            case JavaConstant javaConst -> this.createNew(javaConst);
            case DataPointerConstant ptrConst -> this.createNew(ptrConst);
            default -> throw new IllegalStateException();
        };
    }

    public Constant createNew(JavaConstant constant) {
        return new TaggedJavaConstant(constant, this.lastTag++);
    }

    public Constant createNew(DataPointerConstant constant) {
        return new TaggedDataPointerConstant(constant, this.lastTag++);
    }
}
