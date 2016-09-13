package uk.ac.man.cs.llvm.ir.module;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;

import uk.ac.man.cs.llvm.ir.types.BigIntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.Type;

/**
 * Metadata Nodes from the type OLD_NODE are structured in a way like:
 *
 * [type, value, type, value, ...]
 */
public class MetadataArgumentParser implements Iterator<Type> {

    protected final Types types;

    protected final List<Type> symbols;

    private long[] args;

    private int index = 0;

    public MetadataArgumentParser(Types types, List<Type> symbols, long[] args) {
        super();
        this.types = types;
        this.symbols = symbols;
        this.args = args;
    }

    @Override
    public boolean hasNext() {
        return remaining() > 0;
    }

    @Override
    public Type next() {
        assert (hasNext());

        return get(index++);
    }

    public Type peek() {
        return get(index);
    }

    protected Type get(int i) {
        assert (args.length >= i * 2 + 1);

        Type typeOfArgument = types.get(args[i * 2]);

        long argVal = args[(i * 2) + 1];
        if (typeOfArgument instanceof IntegerConstantType) {
            return symbols.get((int) argVal); // TODO: check
        } else if (typeOfArgument instanceof BigIntegerConstantType) {
            return symbols.get((int) argVal); // TODO: check
        } else if (typeOfArgument instanceof IntegerType) {
            return symbols.get((int) argVal); // should work
        } else if (typeOfArgument instanceof MetaType) {
            // TODO: return more suited type
            return new IntegerConstantType(IntegerType.INTEGER, argVal); // TODO: check
        } else if (typeOfArgument instanceof PointerType) {
            // TODO: return more suited type
            return new IntegerConstantType(IntegerType.INTEGER, argVal); // TODO: check
        } else {

            System.out.println(typeOfArgument.getClass().getName()); // TODO: get correct type
            return new IntegerConstantType(IntegerType.SHORT, argVal);
        }
    }

    public int remaining() {
        assert (args.length >= index * 2 + 1);

        return (args.length / 2) - index;
    }

    public void rewind() {
        index = 0;
    }

    @Override
    public String toString() {
        String s = "MetadataArgumentParser [";
        for (int i = index; i < (args.length / 2); i++) {
            s += get(i);
            if (i < ((args.length / 2) - 1)) {
                s += ", ";
            }
        }
        return s + "]";
    }
}
