# CI/CD Pipeline Blueprint for Microservices

## 1. Component-by-Component Design

### 1.1 Repository Structure and Versioning
- **Monorepo Layout:** Each deployable microservice resides under `services/{service-name}` with its REST implementation. For every service there is a sibling `services/{service-name}-client` module that provides the injectable HTTP client used by callers. Client modules expose only the contract and have no runtime dependencies on the concrete service.
- **External-Only Services:** If a service is solely consumed externally (e.g., ingestion pipelines, public-facing APIs without internal callers), a `{service}-client` module is not required. Capture this exception in service documentation to avoid generating unused clients.
- **Semantic Versioning:** All modules (services and clients) follow semantic versioning. Service versions are declared in their own Gradle build files. Client modules bump versions only when their public API surface changes.
- **API Binding:** OpenAPI annotations and controller implementations exist exclusively in the concrete service module. Client modules consume the published API version and must not define endpoints.

### 1.2 Dependency Detection and Mesh Documentation
- **Gradle Target:** A dedicated Gradle task inspects the dependency graph to discover service-to-service relationships based on imports of `{service}-client` modules. The task emits a structured Markdown table and rewrites `services/README.md` so the mesh overview stays current.
- **Service Mesh View:** The generated documentation includes version numbers, REST entry points, and directional edges (caller → callee). It is refreshed in CI whenever the dependency task runs.

### 1.3 GitHub Actions Workflow
- **Workflow Triggering:** Pipelines trigger on pushes to main, release branches, and on PR merges. Workflows inspect module version files to decide the build scope; common library changes alone do not force dependent service deployments.
- **Change Detection:** A preparatory job compares (a) the versions declared in `services/*/build.gradle.kts` against the previous release tag and (b) Git diffs for client modules. Results inform downstream matrix jobs.
- **Matrix Build:** Services with version deltas are added to a build matrix. For each entry the workflow:
  1. Checks out code and restores Gradle cache.
  2. Runs unit/integration tests scoped to the service and its client (e.g., `./gradlew :services:foo:test :services:foo-client:test`).
  3. Builds the Docker image for the service using `./gradlew :services:foo:bootBuildImage` or an equivalent task.
  4. Publishes the service image to the GCP Artifact Registry with tags `${version}` and `latest`.
  5. Publishes the client module to the artifact repository (e.g., Maven/Artifact Registry) if its version changed.

### 1.4 Interdependency Handling
- **Minor/Patch Release:** Only the service whose version changed is built and deployed. Client modules are updated as needed, but consuming services are not automatically rebuilt.
- **Major Release:** Major version bumps represent breaking changes. The workflow queries the dependency graph to determine impacted services (those depending on `{service}-client`). All dependents enter the matrix to rebuild against the new client version and redeploy, ensuring compatibility.
- **Multi-Service PRs:** When a pull request updates versions for multiple services, the matrix includes all affected services. The workflow handles them in parallel to keep PR merges atomic and reproducible.
- **Client Safety:** All clients must expose a thread-safe, injectable class with comprehensive Javadoc that summarizes usage patterns, error handling, and retry semantics. CI enforces this via linting or custom code checks.

### 1.5 Deployment to GCP Kubernetes
- **Configuration:** Deployment manifests (Helm charts or Kustomize overlays) live per service beneath `deploy/{service}`. Image tags are parameterized to accept `${version}`.
- **Release Job:** After successful builds, a gated deploy job runs:
  1. Authenticates with GCP (`gcloud auth`, workload identity).
  2. Applies the Kubernetes manifests with the new image tag (`kubectl apply` / Helm upgrade).
  3. Waits for rollout completion (`kubectl rollout status`) and publishes deployment status back to GitHub.
- **Rollback Strategy:** Each deployment records the previously deployed image tag in an artifact. Rollback commands can be triggered manually through a workflow dispatch to redeploy that tag.

### 1.6 Observability and Quality Gates
- **Static Analysis & Tests:** Build pipelines integrate linting, unit tests, and contract tests. Services exporting REST APIs run OpenAPI validation against client modules.
- **SLO Checks:** Post-deploy, optional smoke tests can call health endpoints through the corresponding `{service}-client`.
- **Audit Trail:** Version changes, build artifacts, and deployment metadata are logged to GitHub releases or GCP Cloud Logging for traceability.

## 2. Implementation Checklist

1. **Repository Preparation**
   - [x] Define semantic versions in each `services/*/build.gradle.kts`.
   - [x] Ensure each service has a matching `{service}-client` module exposing a thread-safe, injectable client with documentation. Only needed if there is actually a service dependency
   - [x] Move all CDM and API field definitions to `design/common_data_model.md` (already complete).

2. **Dependency Reporting**
   - [x] Author a Gradle task (e.g., `generateServiceMesh`) that inspects module dependencies for `{service}-client`.
   - [x] Automate rewrite of `services/README.md` with a generated dependency table.
   - [ ] Add CI job to run the task and fail if `services/README.md` changes without being committed.

3. **GitHub Actions Setup**
   - [ ] Create workflow `ci.yml` to run tests on pull requests.
   - [ ] Create workflow `release.yml` that triggers on main merges, extracts version deltas, and populates a matrix of services/clients to build.
   - [ ] Implement logic to detect major version bumps and gather dependents using the mesh data.

4. **Build and Publish**
   - [ ] Configure Gradle tasks to build Docker images (`bootBuildImage` or Jib) per service.
   - [ ] Configure publishing tasks for `{service}-client` modules to artifact storage.
   - [ ] Store build artifacts (SBOMs, manifests) in GitHub Actions artifacts or GCP storage.

5. **Deployment Automation**
   - [ ] Define Kubernetes deployment manifests or Helm charts under `deploy/{service}`.
   - [ ] Add steps to authenticate with GCP and push images to Artifact Registry.
   - [ ] Add deployment job that upgrades the target namespace and waits for rollouts to complete.

6. **Quality Gates & Observability**
   - [ ] Integrate OpenAPI contract tests to ensure clients remain compatible.
   - [ ] Enforce Javadoc/lint checks verifying client documentation.
   - [ ] Add smoke tests and optional post-deploy verification per service.

7. **Rollbacks and Documentation**
   - [ ] Capture deployed image tags/version metadata in release notes.
   - [ ] Document manual rollback procedures in `design/ci_cd_pipeline.md` (follow-up).
   - [ ] Train developers on versioning rules and dependency documentation workflow.

Completion of the checklist yields a reproducible, selective build-and-deploy pipeline that honors service interdependencies, reinforces contract integrity via client modules, and continuously deploys to GCP Kubernetes through GitHub Actions.
