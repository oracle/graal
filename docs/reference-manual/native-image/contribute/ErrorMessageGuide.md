# Error Reporting in Native Image

The rules for errors reported during image generation are:

  1) An error message that does not contain a stack trace is aimed for *Native Image users*. This message must be: a) clearly written, and b) actionable for someone without deep VM knowledge.

  2) An error message that contains a stack trace is aimed for *Native Image developers*. This message represents a Native Image builder bug and should be reported to [GitHub](https://github.com/oracle/graal/issues) specifying `Native Image` in the `Component` field. The bug report needs to explain the used environment, the sequence of commands that led to the error, as well as the whole text of the error.

## How to Report Errors to Native Image Users

Report errors to the Native Image users with `UserError.abort`. A good example of reporting a clearly written and actionable error is:

    throw UserError.abort("No output file name specified. Use '%s'.",
            SubstrateOptionsParser.commandArgument(SubstrateOptions.Name, "<output-file>"));

An example of a clearly written but non-actionable error message is:

    throw UserError.abort("Correct image building task must be provided.");

This message relies on the internal slang (correct image building task) and does not directly and concisely explain to the user how to fix the error.

The `UserError.abort` method will interrupt compilation with a `UserException` causing the `native image` generator to exit. To assure consistent error reporting throughout the project, this exception must be handled only once and must not be ignored anywhere in the project.

## How to Report Errors to Native Image Developers

To report VM errors use `throw VMError.shouldNotReachHere(<message>)`. The error message should be clearly written and actionable for a VM developer. A good example of reporting VM errors is:

    throw VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");

The following example shows improper error reporting:

    for (ResolvedJavaField field : declaringClass.getStaticFields()) {
        if (field.getName().equals(name)) {
            return field;
        }
    }
    VMError.shouldNotReachHere();

Error reporting with no error message requires the user to unnecessarily read the context in order to understand the error. Furthermore, the `throw` in preceding `VMError.shouldNotReachHere()` is omitted making users and static analysis tools unaware of control flow interruption.
