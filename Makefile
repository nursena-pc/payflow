.PHONY: infra-up infra-down run test verify compose-up

infra-up:
	docker compose up -d postgres redis kafka

infra-down:
	docker compose down

run:
	mvn spring-boot:run

test:
	mvn test

verify:
	mvn verify

compose-up:
	docker compose --profile app up --build
