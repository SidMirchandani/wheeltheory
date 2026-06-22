@echo off
set "GH=C:\Program Files\GitHub CLI\gh.exe"
set "PATH=%PATH%;C:\Program Files\GitHub CLI"

cd /d "%~dp0"

"%GH%" auth status >nul 2>&1
if errorlevel 1 (
  echo Not logged into GitHub. Starting login...
  "%GH%" auth login
)

"%GH%" repo view SidMirchandani/wheeltheory >nul 2>&1
if errorlevel 1 (
  echo Creating repository wheeltheory on GitHub...
  "%GH%" repo create wheeltheory --public --description "Wheel Theory - teacher and student grade management" --source=. --remote=origin --push
) else (
  echo Pushing to GitHub...
  git push -u origin main
)

pause
