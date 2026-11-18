.PHONY: all clean run

# CFLAGS := -Wall -Werror -std=c23 -g -nostdinc -nostdlib -ffreestanding
SOURCES := src/main.kt
#LIBS := packages/kotlinizer/kotlinizer.kt packages/aoc/aoc.kt packages/greeter/greeter.kt
LIBS := packages/aoc/aoc.kt

SESSION?=123
YEAR?=2018
DAY?=19
CURRENT_FILE?=src/${YEAR}/${DAY}/main.kt

all: run

download_input:
	ifeq (,$(wildcard ./src/${YEAR}/${DAY}/input.txt))
		curl -b "session=${SESSION}" "https://adventofcode.com/${YAER}/day/${DAY}/input" > src/${YEAR}/${DAY}/input.txt
	endif

clean:
	rm -rf build

build/aoc.klib: $(LIBS)
	@mkdir -p $(@D)
	kotlinc-native $(LIBS) -p library -o $(basename $@)

build/aoc_${YEAR}_${DAY}.kexe: build/aoc.klib $(CURRENT_FILE)
	@mkdir -p $(@D)
	kotlinc-native $(CURRENT_FILE) -l $< -o $(basename $@)

build: build/aoc_${YEAR}_${DAY}.kexe

run/example: build/aoc_${YEAR}_${DAY}.kexe
	./$< src/${YEAR}/${DAY}/input_example.txt

run: build/aoc_${YEAR}_${DAY}.kexe
	./$< src/${YEAR}/${DAY}/input.txt
