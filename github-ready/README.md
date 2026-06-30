# GitHub Ready Files

This folder collects the main deployment reference files you need before pushing UniSubmit live on Railway.

## Included

- `../README.md`: project and deployment steps
- `../railway.toml`: Railway build and start configuration
- `../.env.railway.example`: environment variable template
- `../railway-collaboration.sql`: optional SQL reference

## What to push

Push the full repository root to GitHub, not just this folder. Railway needs the source code in `src/`, the Maven build in `pom.xml`, and the deployment files above.
