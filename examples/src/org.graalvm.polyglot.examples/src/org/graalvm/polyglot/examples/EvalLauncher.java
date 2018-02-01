package org.graalvm.polyglot.examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

/**
 * Example that shows how a fully language and tool agnostic launcher can be implemented using the
 * polyglot API.
 */
public class EvalLauncher {

    static final String BASIC_HELP = "" +
                    "Usage: eval [OPTIONS] SCRIPT...\n" +
                    "    SCRIPT:  <language-id>:<source-code> eg. js:42 \n" +
                    "    Example: eval \"js:42\" \"r:c(1,2,3)\"\n" +
                    "\n" +
                    "Options: \n" +
                    "    --version       print version information\n" +
                    "    --help          print this help message \n" +
                    "    --experthelp    print a help message for expert users \n" +
                    "    --debughelp     print a help message for debugging internal errors \n";

    public static void main(String[] args) {
        Map<String, String> options = new HashMap<>();

        List<String> scripts = new ArrayList<>();
        for (String option : args) {
            if (option.equals("--version")) {
                printVersions();
                return;
            } else if (option.equals("--help")) {
                printHelp(OptionCategory.USER);
                return;
            } else if (option.equals("--experthelp")) {
                printHelp(OptionCategory.EXPERT);
                return;
            } else if (option.equals("--debughelp")) {
                printHelp(OptionCategory.DEBUG);
                return;
            } else if (option.startsWith("--")) {
                int equalIndex = option.indexOf("=");
                int keyEndIndex;
                String value = "";
                if (equalIndex == -1) {
                    keyEndIndex = option.length();
                } else {
                    keyEndIndex = equalIndex;
                    value = option.substring(equalIndex + 1, option.length());
                }
                String key = option.substring(2, keyEndIndex);
                options.put(key, value);
            } else {
                scripts.add(option);
            }
        }
        if (scripts.isEmpty()) {
            System.err.println("Error: No files to execute specified.\nUse --help for usage information.");
            return;
        }

        Context context = Context.newBuilder().options(options).build();

        for (String script : scripts) {
            int index = script.indexOf(':');
            if (index == -1) {
                System.err.println(String.format("Error: Invalid script %s provided.\nUse --help for usage information.", script));
                return;
            }
            String languageId = script.substring(0, index);
            if (context.getEngine().getLanguages().containsKey(languageId)) {
                System.err.println(String.format("Error: Invalid language %s provided.\nUse --help for usage information.", languageId));
                return;
            }
            String code = script.substring(index + 1, script.length());

            System.out.println("Script:       " + script + ": ");
            try {
                Value evalValue = context.eval(languageId, code);
                System.out.println("Result type:  " + evalValue.getMetaObject());
                System.out.println("Result value: " + evalValue);
            } catch (PolyglotException e) {
                System.err.println("Error:        " + e.getMessage());
            }
        }
    }

    private static void printVersions() {
        Engine engine = Engine.create();
        System.out.println("GraalVM Polyglot Engine Version " + engine.getVersion());
        System.out.println("Installed Languages: ");
        for (Language language : engine.getLanguages().values()) {
            System.out.printf("    %-10s: %-10s Version %s%n", language.getId(), language.getName(), language.getVersion());
        }
        System.out.println("Installed Instruments: ");
        for (Instrument instrument : engine.getInstruments().values()) {
            System.out.printf("    %-10s: %-10s Version %s%n", instrument.getId(), instrument.getName(), instrument.getVersion());
        }
    }

    private static void printHelp(OptionCategory maxCategory) {
        Engine engine = Engine.create();
        System.out.println(BASIC_HELP);
        System.out.println("Engine options: ");
        for (OptionDescriptor descriptor : engine.getOptions()) {
            printOption(maxCategory, descriptor);
        }

        System.out.println("Language options: ");
        for (String languageId : engine.getLanguages().keySet()) {
            Language language = engine.getLanguage(languageId);
            for (OptionDescriptor descriptor : language.getOptions()) {
                printOption(maxCategory, descriptor);
            }
        }

        System.out.println("Tool options: ");
        for (String instrumentId : engine.getInstruments().keySet()) {
            Instrument instrument = engine.getInstrument(instrumentId);
            for (OptionDescriptor descriptor : instrument.getOptions()) {
                printOption(maxCategory, descriptor);
            }
        }
    }

    private static void printOption(OptionCategory maxCategory, OptionDescriptor descriptor) {
        if (maxCategory.ordinal() <= descriptor.getCategory().ordinal()) {
            System.out.printf("    --%-30s = %-10s %s %n", descriptor.getKey(), descriptor.getKey().getDefaultValue(), descriptor.getHelp());
        }
    }

    @MyTest
    public void testPrintHelp() {
        main(new String[]{"--help"});
    }

    @MyTest
    public void testPrintVersion() {
        main(new String[]{"--version"});
    }

    @MyTest
    public void testEval() {
        main(new String[]{"js:42"});
    }

}
