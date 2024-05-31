package org.graalvm.igvutil.args;

import java.io.PrintWriter;
import java.util.Formatter;

public class HelpPrinter implements AutoCloseable {
    private final Formatter fmt;

    public HelpPrinter(PrintWriter writer) {
        this.fmt = new Formatter(writer);
    }

    public HelpPrinter print(String format, Object... args) {
        fmt.format(format, args);
        return this;
    }

    public HelpPrinter println(String format, Object... args) {
        print(format, args);
        return newline();
    }

    public HelpPrinter printArg(Argument argument) {
        fmt.format("  ");
        argument.printUsage(this);
        newline();
        fmt.format("    %s%n", argument.getDescription());
        return this;
    }

    public HelpPrinter newline() {
        fmt.format("%n");
        return this;
    }

    @Override
    public void close() {
        fmt.close();
    }
}
