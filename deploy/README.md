# Deploying & updating UniSubmit

Live setup: **Supabase** (managed Postgres) + **Oracle Cloud** VM (runs the app) +
**Caddy** (automatic HTTPS on a free `sslip.io` host) + **GitHub Actions** (auto-deploy
on push to `main`). No Docker anywhere — the app is a plain Spring Boot fat-jar run by
systemd.

```
Browser ──HTTPS──► Caddy :443 ──► app :8080 (systemd) ──JDBC/SSL──► Supabase Postgres
GitHub push→main ─► Actions (build jar) ─scp─► VM ─► sudo unisubmit-deploy ─► /health
```

## One-time server setup (Oracle Cloud)

1. **Create the VM**: Ubuntu 22.04, shape `VM.Standard.A1.Flex` (ARM, Always-Free),
   1–2 OCPU / 6–12 GB. In the VCN Security List add ingress rules for TCP **22, 80, 443**.
2. **Generate a CI deploy key** on your laptop (separate from your login key):
   ```
   ssh-keygen -t ed25519 -f unisubmit_deploy -C "unisubmit-ci" -N ""
   ```
   `unisubmit_deploy` = private key (GitHub secret). `unisubmit_deploy.pub` = public key.
3. **SSH into the VM** with your login key and run the bootstrap (clone the repo first):
   ```
   git clone https://github.com/Brian-Mbuya/unisubmit.git
   sudo bash unisubmit/deploy/setup-server.sh <PUBLIC_IP> "$(cat unisubmit_deploy.pub)"
   ```
   It installs JRE 17 + Caddy, creates the service, opens the firewall, authorises the CI
   key, and prints your `https://unisubmit-<ip>.sslip.io` URL.
4. **Set the DB password**: `sudo nano /etc/unisubmit.env` → paste `PGPASSWORD` (and confirm
   the JDBC host/user) from Supabase → **Connect** → **Session pooler**.
5. **Add GitHub secrets** (repo → Settings → Secrets and variables → Actions):
   `OCI_HOST` = the IP, `OCI_USER` = `ubuntu`, `OCI_SSH_KEY` = the deploy **private** key.

## Deploying updates

Just push:
```
git add -A && git commit -m "…" && git push origin main
```
GitHub Actions builds the jar and ships it. The DB and the `uploads/` folder are untouched.
Trigger a redeploy without a code change from the Actions tab ("Run workflow").

**Database schema changes**: add a new `src/main/resources/db/migration/V20__*.sql` and push —
Flyway applies it on the next boot. New `@Entity` fields are picked up automatically by
Hibernate `ddl-auto=update`; no migration needed for simple column additions.

## First-login & security

The app auto-creates `admin` / `lecturer` / `student` (all `password123`) on first boot.
**Change the admin password immediately** (login → account settings) and delete or re-password
the demo `lecturer` / `student` accounts. The DB password lives only in `/etc/unisubmit.env`
(chmod 600) — never in git or GitHub.

## Troubleshooting

- Logs: `journalctl -u unisubmit -f` and `journalctl -u caddy -f`.
- App up but no HTTPS: check ports 80/443 are open in **both** the OCI Security List and the
  host firewall (`sudo iptables -L INPUT -n`), and that the sslip host resolves to your IP.
- DB errors on boot: re-check `JDBC_DATABASE_URL` uses the **Session** pooler (5432), not the
  Transaction pooler (6543), and `sslmode=require` is present.
- Restart manually: `sudo systemctl restart unisubmit`.
