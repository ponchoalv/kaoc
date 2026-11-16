@echo off
setlocal enabledelayedexpansion

:: Configuration
set "SOURCES=src\main.kt"
set "LIBS=packages\kotlinizer\kotlinizer.kt packages\aoc\aoc.kt packages\greeter\greeter.kt"
set "BUILD_DIR=build"

:: Handle 'clean' argument
if /i "%~1"=="clean" (
    if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
    echo Cleaned %BUILD_DIR% directory.
    exit /b 0
)

:: Ensure build directory exists
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

echo [1/4] Compiling C static library...
clang --target=x86_64-w64-mingw32 -c packages\greeter\greeter.c -o "%BUILD_DIR%\greeter.o"
if errorlevel 1 exit /b %errorlevel%

llvm-ar rcs "%BUILD_DIR%\greeter.lib" "%BUILD_DIR%\greeter.o"
if errorlevel 1 exit /b %errorlevel%

echo [2/4] Running cinterop...
call cinterop.bat -def packages\greeter\greeter.def -o "%BUILD_DIR%\greeter"
if errorlevel 1 exit /b %errorlevel%

echo [3/4] Compiling Kotlin libraries...
call kotlinc-native.bat %LIBS% -p library -l "%BUILD_DIR%\greeter.klib" -o "%BUILD_DIR%\libs"
if errorlevel 1 exit /b %errorlevel%

echo [4/4] Compiling executable...
call kotlinc-native.bat %SOURCES% -l "%BUILD_DIR%\libs.klib" -l "%BUILD_DIR%\greeter.klib" -o "%BUILD_DIR%\app"
if errorlevel 1 exit /b %errorlevel%

:: Run by default (mirrors 'all: run' in your Makefile), skip if 'build' argument was passed
if /i not "%~1"=="build" (
    echo [RUN] Launching app.exe...
    echo ----------------------------------------
    "%BUILD_DIR%\app.exe"
    exit /b %errorlevel%
)

echo Build succeeded: %BUILD_DIR%\app.exe