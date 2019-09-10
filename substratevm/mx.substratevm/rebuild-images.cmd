@echo off

setlocal enabledelayedexpansion

echo %* | findstr = >nul && (
  echo Warning: the '=' character in program arguments is not fully supported.
  echo Make sure that command line arguments using it are wrapped in double quotes.
  echo Example:
  echo "-Dfoo=bar"
  echo.
)

set "rebuild_images=%~dpnx0"
call :dirname "%rebuild_images%" bin_dir
rem We assume we are in `lib\svm\bin`
set "graalvm_home=%bin_dir%\..\..\.."

set "to_build="
set "custom_args="

for %%a in (%*) do (
  rem Unquote the argument (`u_arg=%%~a`) before checking its prefix.
  rem Pass the argument to the native-image executable as it was quoted by the user (`arg=%%a`)
  set "arg=%%a"
  set "u_arg=%%~a"

  set "_tb="
  set "_h="

  if "!u_arg!"=="polyglot" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="libpolyglot" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="js" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="llvm" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="python" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="ruby" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="--help" (
    set "_h=true"
  ) else if "!u_arg!"=="-h" (
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
  ) else if "!u_arg!"=="--verbose" (
    set "verbose=true"
  ) else if "!u_arg!"=="-v" (
    set "verbose=true"
  ) else if defined custom_args (
    set "custom_args=!custom_args! !arg!"
  ) else (
    set "custom_args=!arg!"
  )
)

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
  echo Usage: "%~nx0 [-v|--verbose] polyglot|libpolyglot|js|llvm|python|ruby... [custom native-image args]..."
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
  if exist "%graalvm_home%\lib\svm\builder\svm-enterprise.jar" (
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
