# Developer Experience Makefile for notif-test
# Variables (override via: make start PORT=8080 SEND_ASYNC=true)
PROJECT_NAME ?= notif-test
PORT ?= 3000
SEND_ASYNC ?= false
SEND_WORKERS ?= 2
SEND_BUFFER ?= 100
LOGS_BACKEND ?= memory  # memory or postgres

.PHONY: help migrate seed start test db-up db-down stop

help:
	@echo "make migrate        # Run DB migrations (Migratus)"
	@echo "make seed           # Seed the database with sample data"
	@echo "make start          # Start the HTTP server (PORT=$(PORT))"
	@echo "make test           # Run the test suite"
	@echo "make db-up          # Start Postgres with docker-compose"
	@echo "make db-down        # Stop Postgres with docker-compose"

migrate:
	lein migratus migrate

seed:
	lein run -m notif-test.db.seed

start:
	PORT=$(PORT) \
	SEND_ASYNC=$(SEND_ASYNC) \
	SEND_WORKERS=$(SEND_WORKERS) \
	SEND_BUFFER=$(SEND_BUFFER) \
	LOGS_BACKEND=$(LOGS_BACKEND) \
	lein run -m notif-test.web

# Convenience Docker targets (requires Docker and docker-compose)
db-up:
	docker-compose up -d db

db-down:
	docker-compose down

test:
	lein test

