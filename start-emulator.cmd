@echo off
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd tweetpick -no-boot-anim -memory 4096 -dns-server 8.8.8.8,1.1.1.1
