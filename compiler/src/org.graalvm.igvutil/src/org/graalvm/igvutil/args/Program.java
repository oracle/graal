package org.graalvm.igvutil.args;

import java.io.PrintWriter;

public class Program extends Command {
    public Program(String name, String description) {
        super(name, description);
    }

    public final void parseAndValidate(String[] args, boolean errorIsFatal) {
        try {
            int parsed = parse(args, 0);
            if (parsed < args.length) {
                throw new UnknownArgumentException(args[parsed]);
            }
        } catch (InvalidArgumentException | MissingArgumentException | UnknownArgumentException e) {
            System.err.printf("Argument parsing error: %s%n", e.getMessage());
            if (errorIsFatal) {
                printHelpAndExit(1);
            }
        } catch (HelpRequestedException e) {
            printHelpAndExit(0);
        }
    }

    public void printHelpAndExit(int exitCode) {
        try (HelpPrinter printer = new HelpPrinter(new PrintWriter(System.out))) {
            printHelp(printer);
        }
        System.exit(exitCode);
    }

    @Override
    public void printHelp(HelpPrinter help) {
        help.println("USAGE:");
        help.printArg(this);
        help.newline();
        super.printHelp(help);
    }
}
