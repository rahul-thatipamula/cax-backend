#!/bin/bash

# Load environment variables from .env file
set -a
source .env
set +a

# Run gradle bootRun using the wrapper
./gradlew bootRun
