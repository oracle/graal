# Notes on Windows Scripting

## Comments

`cmd.exe` will blow up with weird errors (e.g. `can't find label`, `unexpected
else`) when you put some characters in *some* `::` comments. Test every time you
change a comment. It is particularly finicky around labels.

## Quoting and Escapes

By default, batch splits arguments (for scripts, subroutine calls, and
optionless `for`) on delimiter characters: spaces, commas, semicolon and equals
(excepted when they appear within double quotes).

Graal expects `=` in its arguments, and we want to preserve language arguments
as much as possible. To achieve this, we escape all such delimiter characters,
except spaces, by replacing them by markers. This lets us "correctly" split the
arguments in the top-level loop.

Quoting is also peculiar in batch. It has the splitting-prevention effect said
above, but double quotes are regular characters that are passed along with
arguments. This means we can't blindly requote arguments, as we risk getting the
quotes mismatched (e.g. `""a""`). We could *perhaps* get it to work, if not for
the fact that we want to allow quoting only part of an argument (e.g.
`--vm.cp="my path"`).

We still need to treat unquoted and quoted arguments (e.g. `--flag` and
`"--flag"`) similarly. Since we can't requote everything, the solution is
therefore to unquote all arguments - at least, at the outer level, they may
still contain inner quotes! This however exposes us to the possibility of
undesired splitting on spaces that are no longer protected by quotes. We can
mitigate this.

(1) In comparisons, we always surround both sides by quotes (or the expanded
    space would cause a syntactic error). We also always use delayed expansion
    variables (e.g. `!myvar!`), which prevents a variable's inner quote from
    messing up the syntax.

(2) When passing a user-supplied argument to a subroutine, we let splitting
    occur and recoalesce the argument in the subroutine using the %* variable.
    We can pass extra (non-user-supplied) arguments to the subroutine before the
    user-supplied argument. Those need to be removed from the argument list with
    shift before using %* to coalesce. This script does not need to pass
    multiple user-supplied arguments to a subroutine and does not currently
    contain a solution for that particular issue.

In general, unless it's not possible, you should use delayed expansion variables
(`!myvar!`) whenever possible. These variables are only expanded whenever a
script line is actually executed, as opposed to when it is read. Without delayed
expansion, you can get expansions in non-taken if branches to generate syntax
errors, for instance.

We use `%arg_quoted%` to track whether non-classpath jvm arguments appear nested
inside quotes in order to know if we need to requote the translated argument.
This enables both `--vm.obscure="with spaces"` and `"--vm.obscure=with spaces"`
to work.

## Testing

A primitive way to test the windows script is to copy the template to a file
named `test.cmd`, and make a couple temporary adjustements:

- Prefix the final command invocation with `echo`.
- Escape all substitution tags that live within strings with `^`. e.g.
  `<extra_jvm_args>` becomes `^<extra_jvm_args^>`.
- Replace `(<option_vars>)` by the option-holding variables for your language
  (e.g. `(RUBYOPT TRUFFLERUBYOPT)`), or leave the parens empty.
- Replace `set"relcp=<classpath>"` with `set "relcp=a;b;c"`.

Then you can use the example test cases below to test your script.

Please add your own failing test cases.

TODO: automate the process :)

## Test Cases

Where `BASEDIR` is the current directory.

- `test.cmd`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>
    ```

- `test.cmd foox --vm.fooy --vm.cp=bar --vm.classpath=baz`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -fooy -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c;bar;baz" <main_class>  foox
    ```

- `test.cmd --vm.cp=bar foox foov --vm.fooy --vm.fooz --vm.cp=baz --vm.cp=kuz`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -fooy -fooz -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c;bar;baz;kuz" <main_class> foox foov
    ```

- `test.cmd --vm.cp=foo "--vm.cp=tchou tchou"`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c;foo;tchou tchou" <main_class>
    ```

- `test.cmd "--vm.cp=tchou tchou" --vm.cp=foo`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
    "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
    "BASEDIR\a;BASEDIR\b;BASEDIR\c;tchou tchou;foo" <main_class>
    ```

- `test.cmd "--vm.cp=tchou tchou" --vm.cp=foo "--vm.cp=oui oui"`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c;tchou tchou;foo;oui oui" <main_class>
    ```

- `test.cmd --vm.cp=foo;bar --vm.cp=baz;kuz`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c;foo;bar;baz;kuz" <main_class>
    ```

- `test.cmd --arg=x,y --arg2=z,v`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>  --arg=x,y --arg2=z,v
    ```

- `test.cmd --arg=foo=bar`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>  --arg=foo=bar
    ```

- `test.cmd --vm.cp="foo bar" --vm.cp="baz" --vm."zorglub" --foo"xor"`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" "-zorglub" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c;foo bar;baz" <main_class>  --foo"xor"
    ```

- `test.cmd "a b c"`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>  "a b c"
    ```

- `test.cmd --vm.obscure="one two"`

     expected:
     ```
     "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -obscure="one two"
        -cp "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>
     ```

- `test.cmd --vm."obscure=one two"`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" "-obscure=one two"
        -cp "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>
    ```

- `test.cmd "--vm.obscure=one two"`

     expected:
     ```
     "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" "-obscure=one two"
        -cp "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>
     ```

- `test.cmd foo -vm.bar --native`

    expected:

    ```
    The native version of test does not exist: cannot use '--native'.
    If native-image is installed, you may build it with 'native-image
        --macro:<macro_name>'.
    ```

- `test.cmd -e "print(123)"`

    expected:
    ```
    "BASEDIR\<jre_bin>\java" <extra_jvm_args> -Dorg.graalvm.launcher.shell=true
        "-Dorg.graalvm.launcher.executablename=BASEDIR\test.cmd" -cp
        "BASEDIR\a;BASEDIR\b;BASEDIR\c" <main_class>  -e "print(123)"
    ```
