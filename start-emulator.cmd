@echo off
rem emulator.exe is a console app. A hidden start keeps its console window off screen.
powershell -NoProfile -Command "Start-Process -WindowStyle Hidden -FilePath \"$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe\" -ArgumentList '-avd','tweetpick','-memory','4096','-dns-server','8.8.8.8,1.1.1.1'"
