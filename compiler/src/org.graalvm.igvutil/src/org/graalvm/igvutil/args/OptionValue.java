package org.graalvm.igvutil.args;

import java.util.List;

/**
 * An argument that represents a value.
 *
 * @param <T> the type of the parsed value.
 */
public abstract class OptionValue<T> {
    /**
     * The parsed value of the argument.
     */
    protected T value = null;

    /**
     * Default value to return if no value was parsed.
     * Null if there is no default.
     */
    private final T defaultValue;

    private boolean set = false;

    private final String name;
    private final boolean required;
    private final String description;

    /**
     * Constructs a required value argument with no default.
     *
     * @param name the name of the argument
     * @param help the help message
     */
    public OptionValue(String name, String help) {
        this.name = name;
        this.description = help;
        this.required = true;
        this.defaultValue = null;
    }

    /**
     * Constructs an optional argument with a default value (which can be null).
     *
     * @param name the name of the argument
     * @param help the help message
     */
    public OptionValue(String name, T defaultValue, String help) {
        this.name = name;
        this.description = help;
        this.required = false;
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the value of the argument.
     *
     * @return the parsed argument value, or a default if the argument wasn't parsed (yet).
     */
    public T getValue() {
        return value == null ? defaultValue : value;
    }

    public boolean isSet() {
        return set;
    }

    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses the value from one or more arguments
     * and updates {@link #value} if parsing succeeded.
     *
     * @return the index of the next argument to consume.
     */
    abstract int parseValue(String[] args, int offset);

    public int parse(String[] args, int offsetBase) throws InvalidArgumentException {
        int index = parseValue(args, offsetBase);
        if (value == null) {
            throw new InvalidArgumentException(name, "couldn't parse value");
        }
        set = true;
        return index;
    }

    public void printUsage(HelpPrinter help) {
        help.print("<%s>", name);
        if (defaultValue != null) {
            help.print(" (default: %s)", defaultValue);
        }
    }

    public OptionValue<List<T>> repeated() {
        if (defaultValue != null) {
            return new ListValue<>(name, List.of(defaultValue), description, this);
        }
        return new ListValue<>(name, description, this);
    }
}
