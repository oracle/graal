package org.graalvm.igvutil.args;

/**
 * An argument that represents a value.
 *
 * @param <T> the type of the parsed value.
 */
public abstract class Value<T> extends Argument {
    /**
     * The parsed value of the argument.
     */
    protected T value = null;

    /**
     * Default value to return if no value was parsed.
     * Null if there is no default.
     */
    private T defaultValue = null;


    /**
     * Constructs a positional value argument.
     *
     * @param name the name of the argument
     * @param help the help message
     */
    public Value(String name, boolean required, String help) {
        super(name, required, help);
    }

    /**
     * Constructs a required value argument with no help message.
     * This constructor is meant to be used along with an {@link Option},
     * whose required-ness and help message will override its value's.
     */
    public Value(String name) {
        this(name, true, "");
    }

    public Value<T> withDefault(T value) {
        defaultValue = value;
        set = true;
        return this;
    }

    /**
     * Gets the value of the argument.
     *
     * @return the parsed argument value, or a default if the argument wasn't parsed (yet).
     */
    public T getValue() {
        return value == null ? defaultValue : value;
    }

    /**
     * Parses the value from one or more arguments
     * and updates {@link #value} if parsing succeeded.
     *
     * @return the index of the next argument to consume.
     */
    abstract int parseValue(String[] args, int offset);

    @Override
    public int parse(String[] args, int offsetBase) throws InvalidArgumentException {
        int index = parseValue(args, offsetBase);
        if (value == null) {
            throw new InvalidArgumentException(this, "couldn't parse value");
        }
        set = true;
        return index;
    }

    @Override
    public void printUsage(HelpPrinter help) {
        help.print("<%s>", getName());
        if (defaultValue != null) {
            help.print(" (default: %s)", defaultValue);
        }
    }

    public final ListValue<T> repeated() {
        return new ListValue<>(getName(), isRequired(), getDescription(), this);
    }
}
