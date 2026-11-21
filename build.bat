@echo off
setlocal EnableExtensions DisableDelayedExpansion
pushd "%~dp0" || exit /b 1
call :main "%~1"
set "rc=%errorlevel%"
popd
exit /b %rc%

:main
set "action=%~1"
if not defined action set "action=build"
set "year=2018"
set "day=20"
set "dir=src\%year%\%day%"
set "mainKt=%dir%\main.kt"
set "libKt=packages\aoc\aoc.kt"
set "klib=build\aoc.klib"
set "out=build\aoc_%year%_%day%"
set "exe=%out%.exe"
set "input=%dir%\input.txt"

if /i "%action%"=="build" goto build
if /i "%action%"=="run" goto real_input
if /i "%action%"=="run/example" goto example_input
if /i "%action%"=="clean" goto clean
if /i "%action%"=="new" goto new
if /i "%action%"=="download_input" goto download
echo Uso: build.bat [build ^| run ^| run/example ^| new ^| download_input ^| clean] 1>&2
exit /b 2

:real_input
if exist "%input%" goto build
call :download
if errorlevel 1 exit /b 1
goto build

:example_input
set "input=%dir%\input_example.txt"
if exist "%input%" goto build
echo Falta "%input%". 1>&2
exit /b 1

:build
if not exist "%mainKt%" (
    echo Falta "%mainKt%". 1>&2
    exit /b 1
)
if not exist "%libKt%" (
    echo Falta "%libKt%". 1>&2
    exit /b 1
)
if not exist "build\" mkdir "build"
if not exist "build\" exit /b 1

rem 10 = biblioteca y exe; 11 = solo exe; 0 = todo actualizado.
rem UTC evita comparar fechas como texto o depender del idioma de Windows.
powershell.exe -NoProfile -NonInteractive -Command ^
  "$ErrorActionPreference='Stop';" ^
  "if (-not [IO.File]::Exists($env:klib)) { exit 10 };" ^
  "$libTime=[IO.File]::GetLastWriteTimeUtc($env:klib);" ^
  "if ([IO.File]::GetLastWriteTimeUtc($env:libKt) -gt $libTime) { exit 10 };" ^
  "if (-not [IO.File]::Exists($env:exe)) { exit 11 };" ^
  "$exeTime=[IO.File]::GetLastWriteTimeUtc($env:exe);" ^
  "if ($libTime -gt $exeTime -or [IO.File]::GetLastWriteTimeUtc($env:mainKt) -gt $exeTime) { exit 11 }; exit 0"
set "plan=%errorlevel%"
if "%plan%"=="0" goto ready
if "%plan%"=="10" goto compiler
if "%plan%"=="11" goto compiler
echo No se pudo comprobar el estado del build. 1>&2
exit /b 1

:compiler
where kotlinc-native.bat >nul 2>&1
if errorlevel 1 (
    echo No se encontro kotlinc-native.bat en PATH. 1>&2
    exit /b 1
)
if "%plan%"=="11" goto compile_exe

echo [LIB] %libKt% --^> %klib%
call kotlinc-native.bat "%libKt%" -target mingw_x64 -produce library -o "build\aoc"
if errorlevel 1 goto lib_failed
if not exist "%klib%" goto lib_failed

:compile_exe
echo [EXE] %mainKt% --^> %exe%
call kotlinc-native.bat "%mainKt%" -target mingw_x64 -produce program -library "%klib%" -o "%out%"
if errorlevel 1 goto exe_failed
if not exist "%exe%" goto exe_failed

:ready
if "%plan%"=="0" echo [OK] Build actualizado.
if /i "%action%"=="build" exit /b 0
"%exe%" "%input%"
exit /b %errorlevel%

:lib_failed
if exist "%klib%" del /q "%klib%"
echo Fallo la compilacion de la biblioteca. 1>&2
exit /b 1

:exe_failed
if exist "%exe%" del /q "%exe%"
echo Fallo la compilacion del ejecutable. 1>&2
exit /b 1

:new
if not exist "%dir%\" mkdir "%dir%"
if not exist "%dir%\" exit /b 1
if not exist "%mainKt%" copy /y "template\main.kt" "%mainKt%" >nul
if not exist "%mainKt%" exit /b 1
if not exist "%dir%\input_example.txt" type nul > "%dir%\input_example.txt"
if not exist "%dir%\input_example.txt" exit /b 1
exit /b 0

:download
if exist "%input%" exit /b 0
if not defined AOC_SESSION (
    echo Defini AOC_SESSION para descargar el input. 1>&2
    exit /b 1
)
if not exist "%dir%\" mkdir "%dir%"
if not exist "%dir%\" exit /b 1
curl.exe --fail --silent --show-error --cookie "session=%AOC_SESSION%" --output "%input%.part" "https://adventofcode.com/%year%/day/%day%/input"
if errorlevel 1 (
    if exist "%input%.part" del /q "%input%.part"
    exit /b 1
)
move /y "%input%.part" "%input%" >nul
exit /b %errorlevel%

:clean
if exist "%exe%" del /q "%exe%"
if exist "%klib%" del /q "%klib%"
if exist "%exe%" exit /b 1
if exist "%klib%" exit /b 1
exit /b 0
