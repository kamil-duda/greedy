#!/usr/bin/env bash

docker run -e "SPRING_PROFILES_ACTIVE=container" -p 8080:8080 -t kduda/greedy