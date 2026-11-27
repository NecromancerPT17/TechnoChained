@echo off
set JAR=TechnoChained.jar
if exist dist\TechnoChained.jar set JAR=dist\TechnoChained.jar
if not exist "%JAR%" (
    echo [ERRO] Ficheiro %JAR% nao encontrado! Faca primeiro "Clean and Build" (Shift+F11) no Apache NetBeans.
    pause
    exit /b 1
)
start "" javaw -jar "%JAR%" localhost 10001
start "" javaw -jar "%JAR%" localhost 10002
start "" javaw -jar "%JAR%" localhost 10003
