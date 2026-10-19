@echo off
cd /d "%~dp0"
if not exist connector\*.jar for /r connector %%f in (mysql-connector-j*.jar) do copy "%%f" connector\ >nul
if not exist connector\*.jar (
  echo Put the mysql-connector-j .jar file inside the connector folder first.
  pause & exit /b
)
set /p ORDER_DB_PASS=Enter MySQL root password: 
set /p LOAD=Load database from warehouse\shop_schema.sql now? (y/n): 
if /i "%LOAD%"=="y" (
  mysql -u root -p%ORDER_DB_PASS% < warehouse\shop_schema.sql
  if errorlevel 1 ( echo Could not load schema. Check password / that mysql is in PATH. & pause & exit /b )
)
javac -cp ".;connector\*" ShopConsole.java ShopServer.java
if errorlevel 1 ( echo Compile failed. & pause & exit /b )
start "" http://localhost:8081
echo Backend starting... keep this window open.
set PORT=8081
java -cp ".;connector\*" ShopServer
pause
