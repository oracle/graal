@echo off

setlocal enabledelayedexpansion

set "rebuild_images=%~dpnx0"
call :dirname "%rebuild_images%" bin_dir
rem We assume we are in `jre\lib\svm\bin`
set "graalvm_home=%bin_dir%\..\..\..\.."

echo %* | findstr \"" >nul && echo Warning: the " character in program arguments is not fully supported.

rem This is the best I could come up with to parse command line arguments.
rem Other, simpler approaches consider '=' a delimiter for splitting arguments.
rem Know issues:
rem 1. --vm.foo=bar works, but "--vm.foo=bar" does not
rem    It considers '=' a delimiter, therefore --vm.foo and bar are considered 2 distinct arguments.
rem    This does not throw an error but arguments are not properly parsed.
rem 2. --vm.foo="bar" works, but --vm.foo="b a r" does not (spaces are delimiters)
rem    This throws a syntax error.
set "next_arg=%*"
:loop
for /f "tokens=1*" %%a in ("%next_arg%") do (
  set "arg=%%a"
  set "_tb="
  set "_h="

  if "!arg!"=="polyglot" (
    set "_tb=!arg!"
  ) else if "!arg!"=="libpolyglot" (
    set "_tb=!arg!"
  ) else if "!arg!"=="js" (
    set "_tb=!arg!"
  ) else if "!arg!"=="llvm" (
    set "_tb=!arg!"
  ) else if "!arg!"=="python" (
    set "_tb=!arg!"
  ) else if "!arg!"=="ruby" (
    set "_tb=!arg!"
  ) else if "!arg!"=="--help" (
    set "_h=true"
  ) else if "!arg!"=="-h" (
    set "_h=true"
  )

  if defined _tb (
    if defined to_build (
      set "to_build=!to_build! !_tb!
    ) else (
      set "to_build=!_tb!
    )
  ) else if defined _h (
    echo Rebuilds native images in place
    call :usage
    exit /b 0
  ) else if "!arg!"=="--verbose" (
    set "verbose=true"
  ) else if "!arg!"=="-v" (
    set "verbose=true"
  ) else if defined custom_args (
    set "custom_args=!custom_args! !arg!"
  ) else (
    set "custom_args=!arg!"
  )
  set "next_arg=%%~b"
)
if defined next_arg goto :loop

if not defined to_build (
  echo Nothing to build
  call :usage
  exit /b 0
)

rem The list of components to be built does not contain special characters
for %%f in (%to_build%) do (
  set "cmd_line="%graalvm_home%\bin\native-image""

  if "%%f"=="polyglot" (
    call :launcher polyglot cmd_line
  ) else if "%%f"=="libpolyglot" (
    call :libpolyglot cmd_line
  ) else if "%%f"=="js" (
    call :launcher js cmd_line
  ) else if "%%f"=="llvm" (
    call :launcher lli cmd_line
  ) else if "%%f"=="python" (
    call :launcher graalpython cmd_line
  ) else if "%%f"=="ruby" (
    call :launcher truffleruby cmd_line
  ) else (
    echo Should not reach here
    exit /b 1
  )
  echo Building %%f...
  if defined verbose echo !cmd_line!
  !cmd_line!
)

goto :eof

:dirname file output
  setlocal
  set "dir=%~dp1"
  set "dir=%dir:~0,-1%"
  endlocal & set "%2=%dir%"
  exit /b 0

:usage
  echo Usage: "%~nx0 [--verbose] polyglot|libpolyglot|js|llvm|python|ruby... [custom native-image args]..."
  exit /b 0

:common cmd_line
  setlocal
  if defined custom_args (
    set "cmd_line=%cmd_line% %custom_args%"
  )
  for /f "tokens=* usebackq" %%l in (`"%graalvm_home%\bin\native-image" --help-extra`) do (
    set "line=%%l"
    if not "!line:--no-server=!"=="!line!" (
      set "cmd_line=%cmd_line% --no-server"
    )
  )
  if exist "%graalvm_home%\jre\lib\svm\builder\svm-enterprise.jar" (
    set "cmd_line=%cmd_line% -g"
  )
  endlocal & set "%1=%cmd_line%"
  exit /b 0

:polyglot_common cmd_line
  set "%1=%cmd_line% --language:all"
  exit /b 0

:libpolyglot cmd_line
  call :common cmd_line
  call :polyglot_common cmd_line
  set "%1=%cmd_line% --macro:polyglot-library"
  exit /b 0

:launcher cmd cmd_line
  call :common cmd_line
  setlocal
  set "cmd_line=%cmd_line% --macro:%1-launcher"
  if "%1"=="polyglot" (
    call :polyglot_common cmd_line
  )
  endlocal & set "%2=%cmd_line%"
  exit /b 0
