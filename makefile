# compiler version and flags

JC = javac
JFLAGS = -g -source 1.8
JFILES = server.java client.java

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $(JFILES)

CLASSES = server.java client.java

all: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
