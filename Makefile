.PHONY: all clean run new

# CFLAGS := -Wall -Werror -std=c23 -g -nostdinc -nostdlib -ffreestanding
SOURCES := src/main.kt
#LIBS := packages/kotlinizer/kotlinizer.kt packages/aoc/aoc.kt packages/greeter/greeter.kt
LIBS := packages/aoc/aoc.kt

SESSION?=123
YEAR?=2018
DAY?=20
DIRECTORY?=src/${YEAR}/${DAY}/
CURRENT_FILE?=${DIRECTORY}main.kt

all: run/example

download_input:
	@if [ ! -f "src/${YEAR}/${DAY}/input.txt" ]; then \
		mkdir -p "src/${YEAR}/${DAY}"; \
		curl -s -b "session=${SESSION}" "https://adventofcode.com/${YEAR}/day/${DAY}/input" > "src/${YEAR}/${DAY}/input.txt"; \
	fi

new: download_input
	@mkdir -p ${DIRECTORY}
	@if [ ! -f "${DIRECTORY}main.kt" ]; then \
		cp "template/main.kt" "${DIRECTORY}"; \
		echo "Created ${DIRECTORY}main.kt from template."; \
		touch ${DIRECTORY}input_example.txt; \
	else \
		echo "${DIRECTORY}main.kt already exists, skipping copy."; \
	fi

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
