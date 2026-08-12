CC = gcc
CFLAGS = -Wall -Wextra -O3 -g
TARGET = clox
SOURCES = $(filter-out benchmark_table.c, $(wildcard *.c))
OBJECTS = $(SOURCES:.c=.o)

all: $(TARGET)

$(TARGET): $(OBJECTS)
	$(CC) $(CFLAGS) -o $@ $^ -lm

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(TARGET) $(OBJECTS)

run: $(TARGET)
	./$(TARGET)

.PHONY: all clean run
