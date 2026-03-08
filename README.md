# Programming 3 assignment

Name: Van Sy Nguyen
Student Number: 2409320

## Introduction

This repository contains the template Java project for the Programming 3 assignment.
The assignment should be implemented in the [src/main/java/com/o3/server](src/main/java/com/o3/server) subfolder.
Any additional tests can be implemented in the [src/main/java/com/o3/server](src/test/java/com/o3/server) subfolder.
The [pom.xml](pom.xml) file contains the necessary dependencies for the assignment.
New dependencies will be added to the [pom.xml](pom.xml) file during the course.

## Implemented features:
This submission includes the minimum requirements (Features 1-4) as well as the following advanced features:

* **Feature 5:** Add observatory weather to an observation.
* **Feature 6:** Collections endpoint.
* **Feature 7:** Observations stored on the server can be updated.

## Instructions and Comments for the Evaluator

* **Server Configuration:** The application runs on HTTPS port 8001 and implements multi-threading for concurrent requests.
* **Database Management:** The application uses SQLite. The database file location is correctly controlled by the `DATABASE_PATH` environment variable.
* **Feature 5 Execution:** To successfully test Feature 5, the provided `weatherserver.jar` must be running concurrently on `http://localhost:4001`.
* **Grading Expectation:** Grade 5.

Thank you for your review and the informative course. 