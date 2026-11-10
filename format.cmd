@echo off
setlocal
if not defined IDEA_HOME (
    echo Set IDEA_HOME to the IntelliJ IDEA installation directory.
    exit /b 1
)
if not exist "%IDEA_HOME%\bin\format.bat" (
    echo Cannot find IntelliJ IDEA formatter under IDEA_HOME.
    exit /b 1
)
set "FORMAT_HOME=%TEMP%\football-manager-formatter"
if not exist "%FORMAT_HOME%" mkdir "%FORMAT_HOME%"
set "IDEA_PROPERTIES=%FORMAT_HOME%\idea.properties"
> "%IDEA_PROPERTIES%" echo idea.config.path=${java.io.tmpdir}/football-manager-formatter/config
>> "%IDEA_PROPERTIES%" echo idea.system.path=${java.io.tmpdir}/football-manager-formatter/system
>> "%IDEA_PROPERTIES%" echo idea.log.path=${java.io.tmpdir}/football-manager-formatter/log
>> "%IDEA_PROPERTIES%" echo idea.plugins.path=${user.home}/AppData/Roaming/JetBrains/IntelliJIdea2026.2/plugins
pushd "%~dp0"
call "%IDEA_HOME%\bin\format.bat" -s config\code-style.xml -charset UTF-8 -r -m "*.java,*.jsp,*.jspf,*.js,*.css,*.xml,*.yaml" %* src config pom.xml docker-compose.yaml
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
