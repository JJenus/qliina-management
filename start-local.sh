#!/bin/bash
set -e

# ============================================
# USAGE:
#   ./start.sh [profile]
#
# DESCRIPTION:
#   Starts the Qliina API Spring Boot application
#   with the specified Spring profile.
#
# ARGUMENTS:
#   profile - (optional) Spring profile to activate
#             Defaults to 'local' if not specified
#
# EXAMPLES:
#   ./start.sh          # Start with local profile (H2 database)
#   ./start.sh dev      # Start with dev profile
#   ./start.sh postgres # Start with PostgreSQL profile
#   ./start.sh prod     # Start with production profile (DISABLED)
#
# NOTES:
#   - Automatically kills any process running on port 8080
#   - Uses Maven wrapper (./mvnw) for consistent builds
#   - Suppresses verbose Maven output with -q flag
# ============================================

# Set default profile
PROFILE="${1:-local}"

# Optional: List of valid profiles (remove or modify as needed)
VALID_PROFILES=("local" "dev" "postgres" "test")

# Check if profile is valid (optional)
if [[ ! " ${VALID_PROFILES[@]} " =~ " ${PROFILE} " ]]; then
    echo "Warning: Profile '${PROFILE}' might not be valid."
    echo "Valid profiles are: ${VALID_PROFILES[*]}"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "=== Starting Qliina Api with ${PROFILE} profile ==="

pid=$(lsof -t -i:8080 2>/dev/null) && echo "Killing old process $pid" && kill "$pid" && sleep 1

./mvnw spring-boot:run -Dspring-boot.run.profiles=${PROFILE} -q