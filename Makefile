# Makefile to compile Java programs

# Java compiler
JCC = javac

# Target entry for creating .class files from .java files
default: compile

# Compile the java files
compile:
	$(JCC) AckBufferElement.java TCPTask.java TCPpack.java Communication.java Receiver.java Sender.java TCPend.java

send: 
	java TCPend -p 2359 localhost -a 2360 f test.txt -m 1000 c 20

# Target entry to run the Java program
run:
	java TCPend

# Clean up .class files
clean:
	$(RM) *.class
