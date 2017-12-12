CC=cc
CFLAGS=-g -c -Wall -fPIC
LDFLAGS=-Wall -shared
SOURCES=jni-trace-agent.c
OBJECTS=$(SOURCES:.c=.o)
RESULT=jni-trace-agent.so

all: $(SOURCES) $(RESULT)
	
$(RESULT): $(OBJECTS) 
	$(CC) $(LDFLAGS) $(OBJECTS) -o $@

.c.o:
	$(CC) $(CFLAGS) $< -o $@

clean:
	$(RM) *.o *~ $(RESULT)
