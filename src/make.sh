#!/bin/bash
echo "cleaning the .class files"
rm -f *.class
rm -f ./*/*.class
echo "compiling source code"
javac Client.java
chmod +x ./bfclient
echo "finish."
