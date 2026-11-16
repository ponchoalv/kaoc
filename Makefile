.PHONY: all clean run

# CFLAGS := -Wall -Werror -std=c23 -g -nostdinc -nostdlib -ffreestanding
SOURCES := src/main.kt
LIBS := packages/kotlinizer/kotlinizer.kt packages/aoc/aoc.kt packages/greeter/greeter.kt

all: run

clean:
	rm -rf build

run: build/app.kexe
	./$<

build/libgreeter.a: packages/greeter/greeter.c packages/greeter/greeter.h
	@mkdir -p $(@D)
	clang -c packages/greeter/greeter.c -o build/greeter.o
	ar rcs $@ build/greeter.o

build/greeter.klib: build/libgreeter.a packages/greeter/greeter.def packages/greeter/greeter.h
	@mkdir -p $(@D)
	cinterop -def packages/greeter/greeter.def -o $(basename $@)

build/libs.klib: build/greeter.klib $(LIBS)
	@mkdir -p $(@D)
	kotlinc-native $(LIBS) -p library -l build/greeter.klib -o $(basename $@)

build/app.kexe: build/libs.klib build/greeter.klib $(SOURCES)
	@mkdir -p $(@D)
	kotlinc-native $(SOURCES) -l $< -l build/greeter.klib -o $(basename $@)
