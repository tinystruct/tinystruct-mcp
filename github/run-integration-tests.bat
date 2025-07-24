@echo off
echo Running GitHub integration tests...
cd %~dp0
mvn test -Dtest=GitHubIntegrationTest
pause
