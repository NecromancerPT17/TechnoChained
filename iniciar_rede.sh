#!/usr/bin/env bash
JAR="TechnoChained.jar"
[ -f "dist/TechnoChained.jar" ] && JAR="dist/TechnoChained.jar"
if [ ! -f "$JAR" ]; then
    echo "[ERRO] Ficheiro $JAR não encontrado! Faça primeiro 'Clean and Build' (Shift+F11) no Apache NetBeans."
    exit 1
fi
java -jar "$JAR" localhost 10001 &
java -jar "$JAR" localhost 10002 &
java -jar "$JAR" localhost 10003 &
