# Distributed Compliance & PII Redaction Vault

## Objective

Build a locally deployable, GitOps-managed document processing system that securely ingests documents, redacts personally identifiable information (PII), and generates verifiable audit receipts.

## Services

### Service A — Vault API
- Authenticate document-upload requests.
- Validate file type and size.
- Store original documents in MinIO `raw-zone`.
- Record workflow state and publish document events.
- Return HTTP 202 Accepted for accepted asynchronous work.

### Service B — Redaction Engine
- Consume document events from Kafka.
- Retrieve originals from `raw-zone`.
- Redact supported PII from text documents.
- Store redacted output in `clean-zone`.
- Retry transient failures safely and route exhausted failures to a dead-letter queue.

### Service C — Audit API
- Generate signed audit receipts.
- Record processing metadata and relevant Kubernetes workload information.
- Store receipts in MinIO `audit-zone`.
- Provide an endpoint to retrieve and verify receipts.

## Platform

- Java 21 and Spring Boot
- Docker and Minikube
- Kubernetes and Argo CD
- Kafka in KRaft mode
- MinIO object storage
- PostgreSQL workflow state
- Spring Cloud Gateway and Keycloak
- OpenFeign and Resilience4j
- Istio mTLS and Kubernetes Network Policies
- ELK for centralized logs
- Zipkin for distributed tracing
- JUnit 5, Testcontainers and Trivy

## Security principles

- Never log raw documents, secrets or extracted PII.
- Use least-privilege service accounts and storage credentials.
- Make document processing idempotent.
- Bound retries and provide controlled dead-letter recovery.
- Protect signing keys and verify receipt signatures.
- Validate uploaded files and restrict file size and type.
- Keep secrets out of Git and public online tools.

## Initial scope

The first version supports text files only. PDF and image redaction, production high availability, disaster recovery, and production-scale capacity testing are later extensions.

## Delivery approach

Deploy in stages. Keep configuration in Git and use Argo CD to reconcile workloads. Bootstrap the local Kubernetes cluster and Argo CD only when those tools are needed.
