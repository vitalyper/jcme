@echo off
set ourfile=%0%
for %%F in (%ourfile%) do set dirname=%%~dpF
pushd %dirname%

call :CHECK_FOR_BINARY java || goto :EOF
goto :RUN

:CHECK_FOR_BINARY
%1>nul 2>&1
if ERRORLEVEL 9009 (
	echo %1 is not found on your PATH
)
goto :EOF

:RUN
set sc=jcmegen\src\jcmegen\main.clj 
echo Starting clojure script %sc%
java -cp .\jcmegen\lib\*;.\jcmegen\src clojure.main %sc% --input-file config.clj

:EOF
popd
