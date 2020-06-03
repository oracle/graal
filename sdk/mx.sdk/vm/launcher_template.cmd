::
:: ----------------------------------------------------------------------------------------------------
::
:: Copyright (c) 2019, <year>, Oracle and/or its affiliates. All rights reserved.
:: DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
::
:: This code is free software; you can redistribute it and/or modify it
:: under the terms of the GNU General Public License version 2 only, as
:: published by the Free Software Foundation.  Oracle designates this
:: particular file as subject to the "Classpath" exception as provided
:: by Oracle in the LICENSE file that accompanied this code.
::
:: This code is distributed in the hope that it will be useful, but WITHOUT
:: ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
:: FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
:: version 2 for more details (a copy is included in the LICENSE file that
:: accompanied this code).
::
:: You should have received a copy of the GNU General Public License version
:: 2 along with this work; if not, write to the Free Software Foundation,
:: Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
::
:: Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
:: or visit www.oracle.com if you need additional information or have any
:: questions.
::
:: ----------------------------------------------------------------------------------------------------
@echo off

setlocal enabledelayedexpansion

call :getScriptLocation location
call :getExecutableName executablename
for /f "delims=" %%i in ("%executablename%") do set "basename=%%~ni"

set "relcp=<classpath>"
set "absolute_cp="
set newline=^


:: The two white lines above this comment are significant.

:: Split on semicolon by replacing it by newlines.
for /f "delims=" %%i in ("%relcp:;=!newline!%") do (
    if "!absolute_cp!"=="" (
        set "absolute_cp=%location%%%i"
    ) else (
        set "absolute_cp=!absolute_cp!;%location%%%i"
    )
)

set "jvm_args=-Dorg.graalvm.launcher.shell=true "-Dorg.graalvm.launcher.executablename=%executablename%""
set "launcher_args="

:: Check option-holding variables.
:: Those can be specified as the `option_vars` argument of the LauncherConfig constructor.
for %%v in (<option_vars>) do (
    if "!%%v:~0,5!"=="--vm.*" (
        call :escape_args !%%v!
        for %%o in (!args!) do (
            call :unescape_arg %%o
            call :process_arg !arg!
            if errorlevel 1 exit /b 1
        )
    )
)

call :escape_args %*
for %%a in (%args%) do (
  call :unescape_arg %%a
  call :process_arg !arg!
  if errorlevel 1 exit /b 1
)

if "%VERBOSE_GRAALVM_LAUNCHERS%"=="true" echo on

"%location%<jre_bin>\java" <extra_jvm_args> %jvm_args% -cp "%absolute_cp%" <main_class> %launcher_args%

:: Function are defined via labels, so have to be defined at the end of the file and skipped
:: in order not to be executed. :eof is implicitly defined.
goto :eof

:: A digression on quotes and escapes.
:: ----
:: By default, batch splits arguments (for scripts, subroutine calls, and optionless `for`) on
:: delimiter characters: spaces, commas, semicolon and equals (excepted when they appear within
:: double quotes).
::
:: Graal expects `=` in its arguments, and we want to preserve language arguments as much as
:: possible. To achieve this, we escape all such delimiter characters, except spaces, by replacing
:: them by markers. This lets us "correctly" split the arguments.
::
:: Quoting is also peculiar in batch. It has the splitting-prevention effect said above, but double
:: quotes are regular characters that are passed along with arguments. This means we can't blindly
:: requote arguments, as we risk getting the quotes mismatched (e.g. ""a""). In practice such things
:: can be made to work, but it's much worse when only part of the argument is quoted (e.g.
:: --vm.cp="my path").
::
:: We can avoid issues in comparisons by always using quotes + delayed expansions variables (e.g.
:: !myvar!). When matching the start of arguments, we need to strip the outer quotes beforehand.
::
:: Things are harder when passing arguments to subroutines. By stripping away quotes and taking
:: substring of arguments, we risk unforeseen splitting. If we allow only part of an argument to be
:: quoted (--vm.cp="my path";path2), there is no easy way to ensure quotation in all cases. Instead,
:: we let splitting occur and simply coalesce all arguments in one (set "arg=%*") in most function.
:: Because we escaped our arguments beforehand, splitting occur only on spaces and we avoid losing
:: meaningful symbols.
::
:: We use %arg_quoted% to track whether non-classpath jvm arguments appear nested inside quotes in
:: order to know if we need to requote the translated argument. This enables both
:: [--vm.obscure="with spaces"] and ["--vm.obscure=with spaces"] to work.
::
:: Also, cmd.exe will blow up with weird errors (can't find label, unexpected else) when you put
:: some characters in *some* :: comments. Test every time you change a comment It is particularly
:: finicky around labels.

:escape_args
    set "args=%*"
    :: Without early exit on empty contents, substitutions fail.
    if "!args!"=="" exit /b 0
    set "args=%args:,=##GRAAL_ESCAPE_COMMA##%"
    set "args=%args:;=##GRAAL_ESCAPE_SEMI##%"
    :: Temporarily, so that args are split on '=' only.
    set "args=%args: =##GRAAL_ESCAPE_SPACE##%"
    :: Temporarily, otherwise we won't split on '=' inside quotes.
    set "args=%args:"=##GRAAL_ESCAPE_QUOTE##%"
    :: We can't replace equal using the variable substitution syntax.
    call :replace_equals %args%
    set "args=%args:##GRAAL_ESCAPE_SPACE##= %"
    set "args=%args:##GRAAL_ESCAPE_QUOTE##="%"
    exit /b 0

:replace_equals
    setlocal
    set "arg=%1"
    if "!arg!"=="" goto :end_replace_equals
    set "args=%1"
    shift
    :: Items in %* are all separated by =, because we replaced all other delimiters in :escape_args.
    :loop_replace_equals
        set "arg=%1"
        if "!arg!"=="" goto :end_replace_equals
        set "args=%args%##GRAAL_ESCAPE_EQUAL##%arg%"
        shift
        goto :loop_replace_equals
    :end_replace_equals
        endlocal & ( set "args=%args%" )
        exit /b 0

:unescape_arg
    set "arg=%*"
    set "arg=%arg:##GRAAL_ESCAPE_COMMA##=,%"
    set "arg=%arg:##GRAAL_ESCAPE_SEMI##=;%"
    set "arg=%arg:##GRAAL_ESCAPE_EQUAL##==%"
    exit /b 0

:is_quoted
    setlocal
    set /a argslen=0
    for %%a in (%*) do set /a argslen+=1
    if %argslen% gtr 1 (
        set "quoted=false"
    ) else (
        set "arg=%1"
        if "!arg:~0,1!!arg:~-1!"=="""" ( set "quoted=true" ) else ( set "quoted=false" )
    )
    endlocal & ( set "quoted=%quoted%" )
    exit /b 0

:unquote_arg
    :: Sets %arg% to a version of the argument with outer quotes stripped, if present.
    call :is_quoted %*
    if %quoted%==true ( set "arg=%~1" ) else ( set "arg=%*" )
    exit /b 0

:: Unfortunately, parsing of `--jvm.*` and `--vm.*` arguments has to be done blind:
:: Maybe some of those arguments where not really intended for the launcher but were application
:: arguments.

:process_vm_arg
    if %arg_quoted%==false (
        call :is_quoted %*
        set "arg_quoted=%quoted%"
    )
    call :unquote_arg %*
    set "vm_arg=%arg%"
    set "custom_cp="

    if "!vm_arg!"=="cp" (
        set "part1='--%prefix%.cp' argument must be of the form"
        set "part2='--%prefix%.cp=<classpath>', not two separate arguments."
        >&2 echo !part1! !part2!
        exit /b 1
    ) else if "!vm_arg!"=="classpath" (
        set "part1='--%prefix%.classpath' argument must be of the form"
        set "part2='--%prefix%.classpath=<classpath>', not two separate arguments."
        >&2 echo !part1! !part2!
        exit /b 1
    ) else if "!vm_arg:~0,3!"=="cp=" (
        call :unquote_arg %vm_arg:~3%
        set "absolute_cp=%absolute_cp%;!arg!"
    ) else if "!vm_arg:~0,10!"=="classpath=" (
        call :unquote_arg %vm_arg:~10%
        set "absolute_cp=%absolute_cp%;!arg!"
    ) else (
        if %arg_quoted%==true ( set "arg="-%vm_arg%"" ) else ( set "arg=-%vm_arg%" )
        set "jvm_args=%jvm_args% !arg!"
    )
    exit /b 0

:process_arg
    call :is_quoted %*
    set "arg_quoted=%quoted%"
    call :unquote_arg %*

    if "!arg:~0,6!"=="--jvm." (
        >&2 echo '--jvm.*' options are deprecated, use '--vm.*' instead.
        set prefix=jvm
        call :unquote_arg %arg:~6%
        call :process_vm_arg !arg!
        if errorlevel 1 exit /b 1
    ) else if "!arg:~0,5!"=="--vm." (
        set prefix=vm
        call :unquote_arg %arg:~5%
        call :process_vm_arg !arg!
        if errorlevel 1 exit /b 1
    ) else (
        set cond=false
        if "!arg!"=="--native" set cond=true
        if "!arg:~0,9!"=="--native." set cond=true

        if !cond!==true (
            >&2 echo The native version of %basename% does not exist: cannot use '%arg%'.

            set "extra="
            if "!basename!"=="polyglot" set "extra= --language:all"
            set "part1=If native-image is installed, you may build it with"
            set "part2='native-image --macro:<macro_name>!extra!'."
            >&2 echo !part1! !part2!
            exit /b 1
        ) else (
            :: Use %* instead of %arg% to preserve surrounding quotes if present.
            set "launcher_args=%launcher_args% %*"
        )
    )
    exit /b 0

:: If this script is in `%PATH%` and called quoted without a full path (e.g., `"js"`), `%~dp0` is expanded to `cwd`
:: rather than the path to the script.
:: This does not happen if `%~dp0` is accessed in a subroutine.
:getScriptLocation variableName
    set "%~1=%~dp0"
    exit /b 0

:getExecutableName variableName
    set "%~1=%~f0"
    exit /b 0

endlocal
