package org.graalvm.igvutil.args;

import java.util.ArrayList;
import java.util.List;

/**
 * A positional argument that can be parsed multiple times, and collected into a list.
 * When parsing, this will try to consume all subsequent positional arguments.
 * To avoid ambiguities when using a list argument followed by another positional argument of the same type,
 * you can use an argument separator (`--`) as a terminator, marking the end of a list.
 */
public class ListValue<T> extends Value<List<T>> {
    private final Value<T> inner;

    public ListValue(String name, boolean required, String description,
                     Value<T> inner) {
        super(name, required, description);
        this.inner = inner;
    }

    @Override
    public int parseValue(String[] args, int offset) {
        int index;
        for (index = offset; index < args.length; ) {
            if (args[index].startsWith(Argument.OPTION_PREFIX)) {
                if (args[index].contentEquals(Argument.OPTION_PREFIX)) {
                    index++;
                }
                break;
            }
            index = inner.parseValue(args, index);
            if (inner.value == null) {
                break;
            }
            set = true;
            if (value == null) {
                value = new ArrayList<>();
            }
            value.add(inner.value);
        }
        return index;
    }

    @Override
    public void printUsage(HelpPrinter help) {
        help.print("[%s ...]", getName());
    }
}
