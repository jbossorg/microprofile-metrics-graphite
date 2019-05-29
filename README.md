Microprofile Metrics - Graphite Integration
===========================================

Simple Microprofile Metrics - Graphite integration.
It just reads MP Metrics and send them via borrowed implementation from io.dropwizard.metrics:metrics-graphite (transitive dependency).

Usage
-----

TODO

Development
-----------
# Deploy to repo

mvn deploy -Ddeploy-repo-id=$MVN_REPO_ID -Ddeploy-release-repo-url=$MVN_RELEASE_REPO_URL -Ddeploy-snapshot-repo-url=$MVN_SNAPSHOT_REPO_URL