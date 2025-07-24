#!/bin/bash
echo "Running GitHub integration tests..."
cd "$(dirname "$0")"
mvn test -Dtest=GitHubIntegrationTest
