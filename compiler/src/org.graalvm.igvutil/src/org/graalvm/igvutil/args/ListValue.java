package org.graalvm.igvutil.args;

import java.util.ArrayList;
import java.util.List;

/**
 * A positional argument that can be parsed multiple times, and collected into a list.
 * When parsing, this will try to consume all subsequent positional arguments.
 * To avoid ambiguities when using a list argument followed by another positional argument of the same type,
 * you can use an argument separator (`--`) as a terminator, marking the end of a list.
 */
public class ListValue<T> extends OptionValue<List<T>> {
    private final OptionValue<T> inner;

    public ListValue(String name, String help, OptionValue<T> inner) {
        super(name, help);
        this.inner = inner;
    }

    public ListValue(String name, List<T> defaultValue, String help, OptionValue<T> inner) {
        super(name, defaultValue, help);
        this.inner = inner;
    }

    @Override
    public int parseValue(String[] args, int offset) {
        int index;
        for (index = offset; index < args.length; ) {
            if (args[index].contentEquals(Command.SEPARATOR)) {
                index++;
                break;
            }
            index = inner.parseValue(args, index);
            if (inner.value == null) {
                break;
            }
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
