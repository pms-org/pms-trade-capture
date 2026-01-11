CI -> GitOps example

This repository contains an example GitHub Actions workflow that builds the Docker image for `trade-capture`, pushes it to a container registry using an immutable tag (the Git commit SHA), and updates `pms-infra` by changing `services/trade-capture/kustomization.yaml`'s `images[0].newTag`.

Workflow file: `.github/workflows/build-and-update-infra.yml`

Required secrets (add to this repo's GitHub Actions secrets):
- REGISTRY_HOST — container registry host (e.g. docker.io)
- REGISTRY_USERNAME — registry username
- REGISTRY_PASSWORD — registry password
- IMAGE_REPO — full image name (e.g. niishantdev/pms-trade-capture or <account>.dkr.ecr.<region>.amazonaws.com/pms-trade-capture)
- INFRA_REPO_TOKEN — personal access token with push access to `pms-org/pms-infra`

Notes
- CI only needs permission to push a single file change to `pms-infra` (no kubectl or cluster access required).
- The workflow creates a branch, pushes the change, and opens a pull request against `pms-infra` (optional). This provides an audit trail and allows human review for staging/prod.

