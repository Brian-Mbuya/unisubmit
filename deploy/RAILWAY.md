# Deploying on Railway (temporary host)

Bridge host while the Oracle VM is pending (Oracle needs a card). The database
stays on **Supabase** — Railway only runs the app and connects out to it. Moving to
Oracle later changes nothing about the database.

Railway builds the repo's **Dockerfile** on its own servers (no local Docker needed)
and **auto-redeploys on every push to `main`**. HTTPS is provided free on a
`*.up.railway.app` domain.

## One-time setup

1. **Push the repo** (so Railway can build it) — see the git steps in chat / `deploy/README.md`.
2. Go to **railway.app** → **New Project** → **Deploy from GitHub repo** → pick
   `Brian-Mbuya/unisubmit`, branch `main`. Railway detects `railway.toml` and builds the
   Dockerfile.
3. Open the service → **Variables** → add (from Supabase → **Connect** → **Session pooler**,
   port 5432):

   | Variable | Value |
   |---|---|
   | `JDBC_DATABASE_URL` | `jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` |
   | `PGUSER` | `postgres.pzeerzaglvjzefbkadcf` |
   | `PGPASSWORD` | *your Supabase database password* |

   That's all that's required. Do **not** set `PORT` (Railway injects it). The proxy-header
   setting and upload dir already have safe defaults in the image. Optional lean-launch flags
   (`GROBID_ENABLED`, `SPECTER_ENABLED`, `SEARCH_SEMANTIC_ENABLED`, `OPENAI_API_KEY`) stay unset.
4. **(Recommended)** Service → **Volumes** → mount a volume at **`/app/uploads`** so uploaded
   files survive redeploys. *If Volumes aren't on your trial plan, uploads are ephemeral (lost
   on each redeploy) — but all accounts and data still persist, because those live in Supabase.*
5. Service → **Settings → Networking → Generate Domain** → you get
   `https://<name>.up.railway.app`.
6. Watch **Deployments** until the build finishes and the `/health` check goes green, then open
   the URL. First boot runs the migrations + seeds against Supabase (~1–2 min).

## Updates
Edit code → `git commit` → `git push origin main`. Railway rebuilds and redeploys automatically.

## When you move to Oracle (later)
1. Bring up the VM per `deploy/README.md` and put the same Supabase vars in `/etc/unisubmit.env`.
2. Re-enable the GitHub Actions workflow (uncomment the `push` trigger in
   `.github/workflows/deploy.yml`) and add the `OCI_*` secrets.
3. Delete/pause the Railway service. The Supabase database is untouched throughout.
