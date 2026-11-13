.PHONY: all clean run

# CFLAGS := -Wall -Werror -std=c23 -g -nostdinc -nostdlib -ffreestanding
SOURCES := src/main.kt
LIBS := packages/kotlinizer/kotlinizer.kt packages/aoc/aoc.kt

all: build/app

clean:
	rm -rf build

run: build/app.kexe
	./$<

build/libs.klib: $(LIBS)
	@mkdir -p $(@D)
	kotlinc-native $(LIBS) -p library -o $(basename $@)

build/app.kexe: build/libs.klib $(SOURCES)
	@mkdir -p $(@D)
	kotlinc-native $(SOURCES) -l $< -o $(basename $@)
