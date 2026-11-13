# Kotlin/Native + raylib: a practical C interop guide

> **Goal:** call raylib’s C API directly from Kotlin/Native, using either a static archive or a dynamic/shared library.
>
> **Scope:** desktop command-line builds using `cinterop` and `kotlinc-native`. macOS and Linux have complete build recipes; Windows has separate adaptation notes. This is **not** Kotlin/JVM, JNI, JNA, or a browser/Wasm setup.
>
> Examples pin **raylib 5.5** for reproducibility, not as a claim that it is the newest release. Use `cinterop` and `kotlinc-native` from the **same Kotlin/Native distribution**. Commands below are reference recipes, not locally verified builds.

## Contents

1. [The mental model](#1-the-mental-model)
2. [Prerequisites and targets](#2-prerequisites-and-targets)
3. [Project layout](#3-project-layout)
4. [Build raylib: static and shared](#4-build-raylib-static-and-shared)
5. [Define and generate the bindings](#5-define-and-generate-the-bindings)
6. [Write a working window](#6-write-a-working-window)
7. [Link statically](#7-link-statically)
8. [Link dynamically](#8-link-dynamically)
9. [Understand the C-to-Kotlin mapping](#9-understand-the-c-to-kotlin-mapping)
10. [Resource ownership and lifetimes](#10-resource-ownership-and-lifetimes)
11. [Macros, raymath, callbacks, and C shims](#11-macros-raymath-callbacks-and-c-shims)
12. [Gradle integration](#12-gradle-integration)
13. [Windows notes](#13-windows-notes)
14. [Troubleshooting](#14-troubleshooting)
15. [Shipping checklist](#15-shipping-checklist)
16. [Official references](#16-official-references)

---

## 1. The mental model

```text
raylib.h + raylib.def
         │
         ▼ cinterop
     raylib.klib          ← Kotlin declarations + native interop glue
         │
Main.kt ─┴───────────────┐
                        ▼ kotlinc-native + native linker
libraylib.a ───────────► executable             [static raylib]
             OR
libraylib.so/.dylib ───► executable ── loader ─► shared library
                                                  [dynamic raylib]
```

There are **three distinct jobs**:

| Stage | What it needs | Typical options |
|---|---|---|
| Parse C and generate bindings | Headers, include directories, C preprocessor defines | `headers`, `headerFilter`, `compilerOpts`, `-compiler-option -I…` |
| Link the executable | Native libraries and their dependencies | `-library …klib`, `-linker-option …` |
| Run the executable | Shared libraries discoverable by the OS loader | Linux RUNPATH/RPATH, macOS install names/RPATH, Windows DLL search |

**Headers describe the API; they do not contain raylib’s implementation.** Generating a `.klib` does not by itself supply that implementation unless you explicitly bundle a static archive.

### Static versus dynamic

| | Static raylib | Dynamic raylib |
|---|---|---|
| Native artifact | `libraylib.a` | Linux `.so`, macOS `.dylib`, Windows `.dll` + import library |
| raylib code | Linked into the executable | Loaded from a separate library |
| Executable size | Usually larger | Usually smaller |
| Distribution | No separate raylib shared library | Ship/install compatible raylib shared library |
| Updating raylib | Relink application | Sometimes replace library, **only if ABI-compatible** |
| Dependency handling | Explicitly link raylib’s transitive dependencies | Shared raylib usually records its own dependencies |

**Static raylib ≠ a fully static executable.** System libraries, graphics drivers, frameworks, and other dependencies can still be dynamic. macOS desktop apps normally use dynamic system frameworks.

## 2. Prerequisites and targets

Install:

- A Kotlin/Native distribution with `bin/cinterop` and `bin/kotlinc-native` on `PATH`.
- Git, CMake, a native C toolchain, and optionally `pkg-config`.
- **macOS:** Xcode Command Line Tools (`xcode-select --install`).
- **Linux:** graphics/window-system development packages for your chosen raylib backend.

For a Debian/Ubuntu **X11** development setup, a common starting point is:

```bash
sudo apt install build-essential cmake git pkg-config \
  libasound2-dev libx11-dev libxrandr-dev libxi-dev \
  libgl1-mesa-dev libglu1-mesa-dev libxcursor-dev libxinerama-dev
```

Package names vary by distribution/version. Wayland builds have different requirements; this guide explicitly selects X11 on Linux.

```bash
command -v cinterop kotlinc-native cmake
kotlinc-native -version
kotlinc-native -list-targets
```

Choose a target supported by your installed compiler:

| Machine | CLI target | Gradle target factory |
|---|---|---|
| Apple Silicon Mac | `macos_arm64` | `macosArm64()` |
| Intel Mac | `macos_x64` | `macosX64()` |
| Linux x86-64 | `linux_x64` | `linuxX64()` |
| Linux ARM64 | `linux_arm64` | `linuxArm64()` |
| Windows x86-64 | `mingw_x64` | `mingwX64()` |

All of these must agree: **Kotlin target, C archive/shared-library architecture, OS ABI, and headers/configuration**. A macOS ARM64 `.klib` is not a Linux x86-64 binding.

> Build on the target OS first. Kotlin/Native cross-compilation support does not automatically cross-compile raylib, install target system libraries, or make a foreign windowing backend usable.

## 3. Project layout

Create a directory and run the shell recipes from its root. They assume Bash, paths without spaces, and environment variables retained in the current shell.

```text
raylib-kotlin/
├── native/
│   └── raylib.def
├── src/
│   └── Main.kt
├── vendor/
│   └── raylib/                 # raylib C source
├── deps/
│   ├── raylib-static/          # installed headers + static archive
│   └── raylib-shared/          # installed headers + shared library
└── build/                     # C builds, .klib, executables
```

```bash
mkdir -p raylib-kotlin
cd raylib-kotlin
export ROOT="$PWD"
mkdir -p native src vendor deps build

# Choose ONE for this machine:
export TARGET=macos_arm64
# export TARGET=macos_x64
# export TARGET=linux_x64
# export TARGET=linux_arm64
```

## 4. Build raylib: static and shared

Building raylib yourself makes it clear which library you are linking. Separate prefixes also prevent `-lraylib` from silently choosing a shared library when you intended static linking.

```bash
git clone --branch 5.5 --depth 1 \
  https://github.com/raysan5/raylib.git vendor/raylib

export STATIC_PREFIX="$ROOT/deps/raylib-static"
export SHARED_PREFIX="$ROOT/deps/raylib-shared"

# Match the selected Kotlin target on macOS.
CMAKE_PLATFORM=()
case "$TARGET" in
  macos_arm64) CMAKE_PLATFORM+=(-DCMAKE_OSX_ARCHITECTURES=arm64) ;;
  macos_x64)   CMAKE_PLATFORM+=(-DCMAKE_OSX_ARCHITECTURES=x86_64) ;;
  linux_*)    CMAKE_PLATFORM+=(-DGLFW_BUILD_WAYLAND=OFF -DGLFW_BUILD_X11=ON) ;;
esac

cmake -S vendor/raylib -B build/raylib-static \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$STATIC_PREFIX" \
  -DCMAKE_INSTALL_LIBDIR=lib \
  -DBUILD_SHARED_LIBS=OFF -DBUILD_EXAMPLES=OFF \
  "${CMAKE_PLATFORM[@]}"
cmake --build build/raylib-static --parallel
cmake --install build/raylib-static

cmake -S vendor/raylib -B build/raylib-shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$SHARED_PREFIX" \
  -DCMAKE_INSTALL_LIBDIR=lib \
  -DBUILD_SHARED_LIBS=ON -DBUILD_EXAMPLES=OFF \
  "${CMAKE_PLATFORM[@]}"
cmake --build build/raylib-shared --parallel
cmake --install build/raylib-shared
```

Expected artifacts:

```text
deps/raylib-static/include/raylib.h
deps/raylib-static/lib/libraylib.a

deps/raylib-shared/include/raylib.h
deps/raylib-shared/lib/libraylib.dylib     # macOS; may also have versioned files
# OR libraylib.so / libraylib.so.<version> on Linux
```

**Alternative: package manager.** `brew install raylib` or a distro package can work, but inspect which artifacts it supplies, its architecture, and its backend. On macOS use `brew --prefix raylib`, not a hard-coded `/usr/local` or `/opt/homebrew` path. Header-only installation is insufficient.

## 5. Define and generate the bindings

Create `native/raylib.def`:

```properties
language = C
headers = raylib.h
headerFilter = raylib.h
package = raylib
```

What these mean:

- `headers`: the header(s) to parse.
- `headerFilter`: declarations to expose; prevents importing every system header transitively.
- `package`: Kotlin import namespace, unrelated to the native library filename.
- `compilerOpts`: optional C parser flags, such as include paths or required `-D` definitions.
- `linkerOpts`: optional native linker flags propagated to consumers.

Generate a target-specific binding:

```bash
cinterop \
  -def native/raylib.def \
  -target "$TARGET" \
  -compiler-option "-I$STATIC_PREFIX/include" \
  -o "build/raylib-$TARGET"
```

Output: `build/raylib-$TARGET.klib`.

We keep link flags out of this `.def` so **the same binding can demonstrate either linking mode**, provided the static/shared libraries use matching headers, ABI, and configuration.

> `-I` locates headers. `-L` locates native libraries at **link time**. Neither configures the runtime loader.
>
> A `.def` is a properties file, not a shell script: do not expect `$ROOT`, `$(pwd)`, or `~` to expand inside it. Pass paths through CLI/Gradle or write real paths.

## 6. Write a working window

Create `src/Main.kt`:

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import raylib.*

private fun color(r: Int, g: Int, b: Int, a: Int = 255): CValue<Color> =
    cValue<Color> {
        this.r = r.toUByte()
        this.g = g.toUByte()
        this.b = b.toUByte()
        this.a = a.toUByte()
    }

fun main() {
    val background = color(245, 245, 245)
    val foreground = color(80, 80, 80)
    val accent = color(230, 41, 55)

    InitWindow(800, 450, "Kotlin/Native + raylib")
    try {
        SetTargetFPS(60)
        while (!WindowShouldClose()) {
            BeginDrawing()
            ClearBackground(background)
            DrawText("Hello from Kotlin/Native!", 180, 180, 24, foreground)
            DrawCircle(400, 280, 40.0f, accent)
            EndDrawing()
        }
    } finally {
        CloseWindow()
    }
}
```

Why construct colors? raylib’s named colors are C compound-literal macros. Their import behavior can vary; explicit `CValue<Color>` construction avoids depending on macro translation.

The example uses only raylib functions, avoids C variadic calls, and runs the window/event loop on the main thread. Keep that rule for macOS in particular.

## 7. Link statically

### A. Link the archive explicitly — recommended to learn

Passing the archive’s full path **forces static raylib selection**. A bare `-lraylib` may prefer the shared library if both exist.

**macOS** — after the common setup and binding generation:

```bash
kotlinc-native src/Main.kt \
  -target "$TARGET" \
  -library "build/raylib-$TARGET.klib" \
  -linker-option "$STATIC_PREFIX/lib/libraylib.a" \
  -linker-options "-framework Cocoa -framework IOKit -framework OpenGL -framework CoreVideo" \
  -o build/demo-static

./build/demo-static.kexe
```

**Linux, desktop X11** — a typical dependency set for this configuration. Kotlin/Native uses its own sysroot, so external system-library search directories may need to be explicit. These paths are for Debian/Ubuntu; adjust them for your distribution:

```bash
case "$TARGET" in
  linux_x64)   SYSTEM_LIBDIR=/usr/lib/x86_64-linux-gnu ;;
  linux_arm64) SYSTEM_LIBDIR=/usr/lib/aarch64-linux-gnu ;;
esac

kotlinc-native src/Main.kt \
  -target "$TARGET" \
  -library "build/raylib-$TARGET.klib" \
  -linker-option "$STATIC_PREFIX/lib/libraylib.a" \
  -linker-option "-L$SYSTEM_LIBDIR" \
  -linker-options "-lGL -lm -lpthread -ldl -lrt -lX11" \
  -o build/demo-static

./build/demo-static.kexe
```

**Dependencies are build-dependent.** Backend changes, external versus bundled GLFW, audio configuration, and raylib version can change this list. Inspect the actual CMake link interface and use package metadata as a starting point:

```bash
PKG_CONFIG_PATH="$STATIC_PREFIX/lib/pkgconfig" pkg-config --static --libs raylib
```

**raylib 5.5 caveat:** its CMake-generated `Libs.private` metadata can omit raylib dependencies (such as OpenGL), so do not replace the demonstrated dependency flags solely with this output. Cross-check against the CMake configuration and unresolved symbols.

Keep required `-L`, `-l`, framework, and other dependency options when adapting it. Do not add `-lraylib` again when you already supplied the archive explicitly. `pkg-config` commonly emits compiler-driver flags: a `-Wl,...` option needs conversion to the underlying linker arguments when passed directly via Kotlin/Native `-linker-option`/`-linker-options`.

### B. Bundle the archive inside the `.klib`

Useful when distributing bindings together with raylib’s static implementation. Kotlin documents `staticLibraries` and `libraryPaths` as **experimental**.

Create `native/raylib-static.def`:

```properties
language = C
headers = raylib.h
headerFilter = raylib.h
package = raylib
staticLibraries = libraylib.a
```

Generate it with the archive search directory:

```bash
cinterop \
  -def native/raylib-static.def \
  -target "$TARGET" \
  -compiler-option "-I$STATIC_PREFIX/include" \
  -libraryPath "$STATIC_PREFIX/lib" \
  -o "build/raylib-bundled-$TARGET"
```

Equivalent `.def` property, if you prefer a real path in the file:

```properties
libraryPaths = /absolute/path/to/deps/raylib-static/lib
```

Compile using `-library "build/raylib-bundled-$TARGET.klib"` and the platform dependency flags above, **omitting the explicit `libraylib.a` argument**.

Bundling the archive does **not** bundle its system dependencies. You can record stable platform dependency flags in `linkerOpts.osx` / `linkerOpts.linux`, or leave them to the application build. Do not consume both binding variants in one executable; they expose the same package/declarations.

## 8. Link dynamically

Here the executable uses raylib’s shared library. Static raylib dependency flags generally belong to the shared library’s own build, not your application.

### macOS: development run

```bash
kotlinc-native src/Main.kt \
  -target "$TARGET" \
  -library "build/raylib-$TARGET.klib" \
  -linker-option "-L$SHARED_PREFIX/lib" \
  -linker-option -lraylib \
  -linker-option -rpath \
  -linker-option "$SHARED_PREFIX/lib" \
  -o build/demo-shared

./build/demo-shared.kexe
```

Inspect what was actually recorded:

```bash
otool -L build/demo-shared.kexe
otool -L "$SHARED_PREFIX/lib/libraylib.dylib"
otool -l build/demo-shared.kexe
```

**Important:** macOS records a dylib’s **install name**. An absolute install name remains absolute; adding an RPATH does not rewrite it. If the library uses `@rpath/...`, the executable’s RPATH resolves it. If it uses the installation prefix, it works there but is not yet relocatable.

For redistribution beside the executable:

1. Copy the actual dylib into `build/lib/` and retain required versioned names/symlinks.
2. Use `otool -L` to get the exact old reference and versioned filename.
3. Set the copied dylib’s ID with `install_name_tool -id '@rpath/<actual-name>.dylib' …`.
4. Change the executable’s reference using `install_name_tool -change '<old-reference>' '@rpath/<actual-name>.dylib' …`.
5. Link with `-rpath @executable_path/lib` instead of the absolute development path.
6. Re-sign modified binaries as needed; signing/notarization comes **after** these changes.

For a `.app` bundle, the conventional location is `Contents/Frameworks`, with an appropriate `@executable_path/../Frameworks` RPATH. Inspect transitive non-system dylibs too.

### Linux: development run

```bash
kotlinc-native src/Main.kt \
  -target "$TARGET" \
  -library "build/raylib-$TARGET.klib" \
  -linker-option "-L$SHARED_PREFIX/lib" \
  -linker-option -lraylib \
  -linker-option -rpath \
  -linker-option "$SHARED_PREFIX/lib" \
  -o build/demo-shared

./build/demo-shared.kexe
```

Inspect:

```bash
ldd build/demo-shared.kexe
readelf -d build/demo-shared.kexe
readelf -d "$SHARED_PREFIX/lib/libraylib.so"
```

For a relocatable layout:

```text
build/
├── demo-shared.kexe
└── lib/
    ├── libraylib.so -> <versioned filename>
    └── <actual versioned library and any necessary SONAME symlink>
```

Use these linker options **instead of the absolute RPATH pair**:

```bash
-linker-option -rpath -linker-option '$ORIGIN/lib'
```

Single quotes preserve literal `$ORIGIN`; the Linux loader expands it to the executable’s directory. Copy the actual shared library and the names required by its SONAME, not just a dangling symlink. Dependencies of dependencies may need their own runtime search configuration.

For a temporary diagnostic run:

```bash
LD_LIBRARY_PATH="$SHARED_PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  ./build/demo-shared.kexe
```

Prefer a deliberate runtime-path/package strategy for shipping. macOS has `DYLD_LIBRARY_PATH`, but security restrictions can ignore or strip it; it is not a robust packaging solution.

## 9. Understand the C-to-Kotlin mapping

The generated declarations/IDE navigation are the final authority; typedefs, enums, and compiler versions influence exact signatures.

| C | Typical Kotlin/Native representation |
|---|---|
| `void` | `Unit` |
| `int` | `Int` |
| `float` / `double` | `Float` / `Double` |
| C `bool` | `Boolean` |
| `unsigned char` | `UByte` |
| `const char *` input | Usually `String?` through automatic conversion |
| `char *` return | Usually `CPointer<ByteVar>?`; copy via `?.toKString()` |
| `Color`, `Vector2`, etc. passed/returned by value | `CValue<Color>`, `CValue<Vector2>` |
| `Vector2 *` | `CPointer<Vector2>?` or a `CValuesRef<Vector2>?` parameter |
| `void *` | `COpaquePointer?` |
| enum | Integral value or generated Kotlin enum, depending on interop configuration |

**Use `Float` literals** (`40.0f`), not default `Double` literals, for C `float` parameters. C `long` and `size_t` widths vary by ABI; use appropriate generated types and conversions, not a universal `Int` assumption.

### Struct values versus storage

```kotlin
val position: CValue<Vector2> = cValue<Vector2> {
    x = 100.0f
    y = 200.0f
}

val x: Float = position.useContents { this.x }
```

- `CValue<T>` is a value suitable for C by-value arguments.
- `alloc<T>()` creates native storage for a mutable struct in an allocation scope.
- `.ptr` gets a pointer to that storage.
- `.readValue()` copies native struct storage into a `CValue<T>`.
- `.useContents { … }` gives temporary access to a value’s fields. Do not retain a pointer obtained inside it.

### Pointer/output parameters

raylib’s `GetWindowScaleDPI()` returns a `Vector2` by value:

```kotlin
val scaleX = GetWindowScaleDPI().useContents { x }
```

For a C output-count parameter, allocate storage. In raylib 5.5, `LoadFileData` takes an `int *` byte-count output:

```kotlin
memScoped {
    val byteCount = alloc<IntVar>()
    byteCount.value = 0
    val data = LoadFileData("assets/data.bin", byteCount.ptr)
    if (data != null) {
        try {
            println("Loaded ${byteCount.value} bytes")
            // Read data[i] only for valid indices below byteCount.value.
        } finally {
            UnloadFileData(data)
        }
    }
}
```

Do not return a pointer into `memScoped` storage: it is invalid once the scope exits. A returned struct **value** can escape, but pointer fields inside it still obey their own lifetimes.

### Arrays

C arrays are pointers plus a count; bounds are not checked for you. For a function that immediately reads a `Vector2 *`:

```kotlin
memScoped {
    val points = allocArray<Vector2>(3)
    points[0].x = 20.0f;  points[0].y = 20.0f
    points[1].x = 80.0f;  points[1].y = 20.0f
    points[2].x = 50.0f;  points[2].y = 80.0f
    DrawLineStrip(points, 3, color(0, 0, 0))
}
```

Call drawing functions inside `BeginDrawing()`/`EndDrawing()`. Temporary arrays are only safe if C does not keep the pointer.

For Kotlin primitive arrays, `usePinned { … addressOf(0) … }` can expose storage for a synchronous call. Handle empty arrays separately and never let C retain the pointer beyond the pinning scope.

## 10. Resource ownership and lifetimes

Kotlin’s garbage collector does **not** call raylib’s resource unload functions.

| Acquire | Release |
|---|---|
| `InitWindow` | `CloseWindow` |
| `InitAudioDevice` | `CloseAudioDevice` |
| `LoadImage` | `UnloadImage` |
| `LoadTexture` / `LoadTextureFromImage` | `UnloadTexture` |
| `LoadFont` / `LoadFontEx` | `UnloadFont` |
| `LoadSound` | `UnloadSound` |
| `LoadMusicStream` | `UnloadMusicStream` |
| `LoadModel` | `UnloadModel` |
| `LoadFileData` | `UnloadFileData` |
| `LoadFileText` | `UnloadFileText` |

Example, **inside the lifetime of an initialized window**:

```kotlin
val texture = LoadTexture("assets/player.png")
try {
    // In raylib 5.5, inspect IsTextureValid(texture) before using it.
    // DrawTexture(texture, 100, 100, color(255, 255, 255)) during a frame.
} finally {
    UnloadTexture(texture)
}
```

Key rules:

- Release GPU resources **before** `CloseWindow()`; audio resources before `CloseAudioDevice()`.
- Copying a `Texture`/`Model` struct copies handles/pointers; it does **not** duplicate the resource. Avoid double-unloading aliases.
- `nativeHeap.alloc` allocations require `nativeHeap.free`; `memScoped` allocations are freed automatically at scope exit.
- Memory returned by raylib needs **raylib’s matching release API**, not an arbitrary Kotlin/native allocator’s `free`.
- `toKString()` copies text into Kotlin but does not free the original C allocation.
- Some C strings point to borrowed/static reusable storage. Copy when necessary; do not free borrowed data.
- Default resources such as `GetFontDefault()` are not ordinary owned `LoadFont()` results. Check the API’s ownership rules.
- Paths such as `assets/player.png` are generally relative to the **working directory**, not automatically the executable directory.
- Validate failed loads, null pointers, counts, dimensions, and IDs before use. Kotlin null-safety cannot detect a dangling C pointer.

## 11. Macros, raymath, callbacks, and C shims

### Macros are not always functions

Simple numeric constants usually import cleanly. Function-like macros and compound literals may require wrappers. Add custom C declarations after `---` in a `.def`:

```properties
language = C
headers = raylib.h
headerFilter = raylib.h
package = raylib
---
static inline Color knRayWhite(void) { return RAYWHITE; }
static inline void knTraceInfo(const char *message) {
    TraceLog(LOG_INFO, "%s", message);
}
```

Regenerate the bindings, then call `knRayWhite()` and `knTraceInfo("hello")` from Kotlin. Fixed-signature wrappers are often simpler and safer than exposing variadic APIs. Never use untrusted text as a C printf-style format string.

### raymath and rlgl

`raymath.h` is header-based math functionality. `rlgl.h` exposes lower-level rendering functionality; it is not automatically imported just because you bind `raylib.h`.

```properties
headers = raylib.h raymath.h
headerFilter = raylib.h raymath.h
```

Header implementation rules matter. Do not enable implementation macros indiscriminately: duplicate implementations can produce duplicate symbols. For raymath, one explicit approach is parsing it with `RAYMATH_STATIC_INLINE` enabled, or exposing just the operations you need through `static inline` wrappers. Check the exact raymath version’s configuration.

### Callbacks

Use `staticCFunction(::callback)` for compatible callbacks; the function/lambda must not capture local state. Use the exact generated parameter types and C calling convention.

Important raylib-specific caveats:

- `SetTraceLogCallback` involves a C `va_list`; a C shim that formats the message first is often preferable.
- Audio callbacks may run on a native real-time audio thread. Avoid allocation, blocking, file I/O, and heavy Kotlin work in that callback.
- Catch errors on the Kotlin side; never let Kotlin exceptions unwind through arbitrary C stack frames.
- `StableRef` is useful only when the C API actually offers a user-data pointer. Many raylib callbacks do not. Do not assume a nonexistent context argument.
- Unregister callbacks before destroying their state; dispose any `StableRef` only after C can no longer access it.

### When to write a small `.c` wrapper

Use a C shim when it simplifies macros, varargs, ownership, or callback adaptation. Expose a narrow header, compile the `.c` file for the **same target ABI**, and link its object/archive alongside raylib.

`cinterop` parses declarations and can compile its inline wrapper glue; it does **not** discover and build arbitrary application `.c` files just because their header was imported. C++ libraries need a C ABI wrapper (`extern "C"`); raylib itself already has a C API.

## 12. Gradle integration

Gradle uses the same model: configure a native target, generate a cinterop, then link an executable. Use `src/nativeInterop/cinterop/raylib.def` as the conventional `.def` location and put Kotlin code in the matching native source set.

Example **macOS ARM64, static raylib** fragment inside a project with a compatible Kotlin Multiplatform plugin already configured:

```kotlin
kotlin {
    macosArm64("native") {
        compilations.getByName("main") {
            cinterops.create("raylib") {
                definitionFile.set(
                    project.file("src/nativeInterop/cinterop/raylib.def")
                )
                compilerOpts(
                    "-I${project.file("deps/raylib-static/include").absolutePath}"
                )
            }
        }
        binaries {
            executable {
                entryPoint = "main"
                linkerOpts(
                    project.file("deps/raylib-static/lib/libraylib.a").absolutePath,
                    "-framework", "Cocoa",
                    "-framework", "IOKit",
                    "-framework", "OpenGL",
                    "-framework", "CoreVideo"
                )
            }
        }
    }
}
```

With the target named `native`, use `src/nativeMain/kotlin/Main.kt`. Typical tasks are `cinteropRaylibNative` and `linkDebugExecutableNative`; check `./gradlew tasks --all` for your plugin version.

For Linux, change the target factory and linker dependencies. For dynamic linking, replace the archive with `-L…`, `-lraylib`, and the correct runtime-path options. Each native target needs its own compatible bindings and raylib binary.

Building raylib remains a separate step unless you explicitly wire CMake into Gradle tasks. This fragment is not a complete Gradle project: plugin version, repositories, wrapper, and any desired source-set hierarchy still need configuration.

## 13. Windows notes

For native Windows, use `mingw_x64` and a compatible MinGW-w64 raylib build. Do not assume an MSVC `.lib` is interchangeable with a MinGW archive/import library.

| Mode | Typical files | Application requirement |
|---|---|---|
| Static | `libraylib.a` | Link archive + required Win32 system libraries |
| Shared | `raylib.dll` + `libraylib.dll.a` | Link import library; deploy DLL |

Common desktop system dependencies include `opengl32`, `gdi32`, and `winmm`; use metadata from your particular build for the full list.

- Generate bindings with `cinterop -target mingw_x64` and matching Windows headers.
- For shared raylib, its public header uses `USE_LIBTYPE_SHARED` to select DLL import declarations. Pass the corresponding C parser define (`-compiler-option -DUSE_LIBTYPE_SHARED`) when generating those bindings.
- For static consumption, do not enable the DLL-import define. `BUILD_LIBTYPE_SHARED` is for building raylib itself, not ordinary consumers.
- Put `raylib.dll` beside the generated `.exe` for a simple deployment. `-L` alone does not make Windows find the DLL at runtime.
- The macOS/Linux Bash recipes are not PowerShell commands; adapt quoting, paths, dependency flags, and executable names. WSL builds Linux binaries, not native Windows GUI executables.

## 14. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `raylib.h file not found` | Fix cinterop’s `-I…`; verify the installed header exists. |
| Kotlin `unresolved reference: InitWindow` | Missing `import raylib.*`, wrong package, missing `-library`, or bindings excluded the declaration. |
| `Undefined symbols` / `undefined reference to InitWindow` | Generated declarations exist, but raylib’s implementation was not linked. |
| Undefined GLFW, GL, X11, Cocoa, `dlopen`, or audio symbols | Missing static transitive dependencies or incorrect backend/library configuration. Inspect build metadata. |
| `library not found for -lraylib` / `cannot find -lraylib` | Wrong link directory, missing library, wrong filename, or incompatible target. |
| `wrong architecture` / incompatible object format | C library and Kotlin target do not match. Rebuild both for the same target. |
| `dyld: Library not loaded` / `error while loading shared libraries` | Link succeeded but runtime discovery failed. Inspect install names, SONAME, RPATH, and actual deployment files. |
| Duplicate raylib symbols | Archive linked more than once, bundled and external implementations mixed, or implementation macros enabled in multiple translation units. |
| Named colors missing | Construct `CValue<Color>` or wrap the C macro. |
| C float argument type mismatch | Use `f` literals or `.toFloat()`. |
| Enum constant type mismatch | Inspect generated enum representation; use `.value` only when the generated API actually requires it. |
| Experimental API diagnostic | Add `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` or the appropriate scoped opt-in. |
| Segfault after returning from a scope | C retained memory from `memScoped`, `usePinned`, a temporary string, or an already-freed resource. |
| Blank/missing texture | Wrong asset working directory, load failed, or resource used without a valid graphics context. |
| Works on development computer only | Absolute dylib paths/RPATH, missing shared dependencies/assets, incompatible system version, or wrong architecture. |

Useful inspection tools:

```bash
file deps/raylib-static/lib/libraylib.a
# macOS
lipo -info deps/raylib-static/lib/libraylib.a
otool -L build/demo-shared.kexe
# Linux
readelf -h build/demo-shared.kexe
readelf -d build/demo-shared.kexe
```

Use `nm`/`nm -g` to inspect exported/archive symbols. Avoid running `ldd` on untrusted executables; inspect dynamic metadata with `readelf` instead.

## 15. Shipping checklist

- [ ] Pin Kotlin/Native and raylib versions; use matching cinterop/compiler tools.
- [ ] Build target-specific native libraries and `.klib` bindings.
- [ ] Keep raylib headers and binaries from the same API/ABI/configuration.
- [ ] Static: verify the archive was selected and include transitive link dependencies.
- [ ] Dynamic: deploy real library files, required names/symlinks, and non-system dependencies.
- [ ] Remove development-machine absolute paths from the release layout.
- [ ] Inspect the final executable’s actual dependencies; do not infer linking mode from filenames.
- [ ] Keep rendering on the main thread; release resources before their contexts close.
- [ ] Ship assets and define how the program locates them.
- [ ] Test on a clean machine with the supported OS/architecture and graphics environment.
- [ ] Check minimum OS/glibc compatibility; building on a newer Linux can impose newer glibc requirements.
- [ ] Review raylib’s zlib/libpng license and bundled third-party notices; static linking does not remove license obligations.
- [ ] On macOS, finish install-name changes before signing/notarization.

**Practical recommendation:** start with the explicit static-archive recipe. Once the window works, try the dynamic recipe and inspect what the executable actually loads. This separates C interop problems from linker and runtime-loader problems.

## 16. Official references

Kotlin:

- [Interoperability with C](https://kotlinlang.org/docs/native-c-interop.html) — types, pointers, strings, scopes, macros, callbacks.
- [Definition files](https://kotlinlang.org/docs/native-definition-file.html) — filters, compiler/linker options, static archive bundling, custom declarations.
- [Kotlin/Native libraries](https://kotlinlang.org/docs/native-libraries.html) — `.klib` usage and compiler library paths.
- [Kotlin/Native target support](https://kotlinlang.org/docs/native-target-support.html) — supported hosts/targets and cross-compilation constraints.
- [Kotlin/Native compiler](https://kotlinlang.org/docs/native-get-started.html) — native setup and entry points.

raylib:

- [raylib repository](https://github.com/raysan5/raylib)
- [Pinned raylib 5.5 header](https://github.com/raysan5/raylib/blob/5.5/src/raylib.h) — actual API signatures and DLL macros.
- [raylib cheatsheet](https://www.raylib.com/cheatsheet/cheatsheet.html) — useful overview; use the pinned header when versions differ.
- [Build on GNU/Linux](https://github.com/raysan5/raylib/wiki/Working-on-GNU-Linux)
- [Build on macOS](https://github.com/raysan5/raylib/wiki/Working-on-macOS)
- [Build on Windows](https://github.com/raysan5/raylib/wiki/Working-on-Windows)
- [raylib 5.5 CMake dependency configuration](https://github.com/raysan5/raylib/blob/5.5/cmake/LibraryConfigurations.cmake)
- [raylib license](https://github.com/raysan5/raylib/blob/5.5/LICENSE)
