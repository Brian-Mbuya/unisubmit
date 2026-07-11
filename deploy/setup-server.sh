#!/usr/bin/env bash
# ===========================================================================
# One-time server bootstrap for UniSubmit on Oracle Cloud (Ubuntu 22.04+).
#
#   sudo bash deploy/setup-server.sh <PUBLIC_IP> ["<CI_DEPLOY_PUBLIC_KEY>"]
#
# Installs JRE 17 + Caddy + git, creates the service user, systemd unit, Caddy
# HTTPS reverse proxy on a sslip.io host, opens the firewall, and lets the
# `ubuntu` CI user restart the service without a password. Idempotent.
# ===========================================================================
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Please run with sudo:  sudo bash $0 <PUBLIC_IP> [\"<CI_DEPLOY_PUBLIC_KEY>\"]" >&2
  exit 1
fi

PUBLIC_IP="${1:-}"
DEPLOY_PUBKEY="${2:-}"
if [[ -z "$PUBLIC_IP" ]]; then
  echo "Usage: sudo bash $0 <PUBLIC_IP> [\"<CI_DEPLOY_PUBLIC_KEY>\"]" >&2
  exit 1
fi

IP_DASHES="${PUBLIC_IP//./-}"
SSLIP_HOST="unisubmit-${IP_DASHES}.sslip.io"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export DEBIAN_FRONTEND=noninteractive

echo ">> [1/8] Installing packages (JRE 17, git, iptables-persistent)…"
apt-get update -y
apt-get install -y openjdk-17-jre-headless git curl gnupg debian-keyring debian-archive-keyring apt-transport-https iptables-persistent

echo ">> [2/8] Installing Caddy…"
if ! command -v caddy >/dev/null 2>&1; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
    | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    | tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
  apt-get update -y
  apt-get install -y caddy
fi

echo ">> [3/8] Creating unisubmit user + directories…"
id -u unisubmit >/dev/null 2>&1 || useradd --system --home /opt/unisubmit --shell /usr/sbin/nologin unisubmit
mkdir -p /opt/unisubmit/uploads
chown -R unisubmit:unisubmit /opt/unisubmit

echo ">> [4/8] Installing systemd service + deploy helper…"
install -m 644 "$SCRIPT_DIR/unisubmit.service" /etc/systemd/system/unisubmit.service
install -m 755 "$SCRIPT_DIR/unisubmit-deploy"  /usr/local/bin/unisubmit-deploy
cat > /etc/sudoers.d/unisubmit-deploy <<'EOF'
ubuntu ALL=(root) NOPASSWD: /usr/local/bin/unisubmit-deploy
EOF
chmod 440 /etc/sudoers.d/unisubmit-deploy

echo ">> [5/8] Writing /etc/unisubmit.env (edit it with your Supabase password!)…"
if [[ ! -f /etc/unisubmit.env ]]; then
  install -m 600 "$SCRIPT_DIR/unisubmit.env.example" /etc/unisubmit.env
  echo "   -> created from template — EDIT before first deploy."
else
  echo "   -> already exists; left untouched."
fi

echo ">> [6/8] Configuring Caddy for ${SSLIP_HOST}…"
sed "s/__SSLIP_HOST__/${SSLIP_HOST}/g" "$SCRIPT_DIR/Caddyfile.template" > /etc/caddy/Caddyfile

if [[ -n "$DEPLOY_PUBKEY" ]]; then
  echo ">> [6b] Authorising CI deploy key for user ubuntu…"
  install -d -m 700 -o ubuntu -g ubuntu /home/ubuntu/.ssh
  touch /home/ubuntu/.ssh/authorized_keys
  grep -qxF "$DEPLOY_PUBKEY" /home/ubuntu/.ssh/authorized_keys || echo "$DEPLOY_PUBKEY" >> /home/ubuntu/.ssh/authorized_keys
  chown ubuntu:ubuntu /home/ubuntu/.ssh/authorized_keys
  chmod 600 /home/ubuntu/.ssh/authorized_keys
fi

echo ">> [7/8] Opening firewall ports 80/443 (Oracle host iptables)…"
for p in 80 443; do
  iptables -C INPUT -p tcp --dport "$p" -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport "$p" -j ACCEPT
done
netfilter-persistent save || true

echo ">> [8/8] Enabling services…"
systemctl daemon-reload
systemctl enable caddy    >/dev/null 2>&1 || true
systemctl restart caddy
systemctl enable unisubmit >/dev/null 2>&1 || true

cat <<EOF

======================================================================
 Server bootstrap complete.

 Your site will be:   https://${SSLIP_HOST}

 NEXT STEPS
 1) Edit /etc/unisubmit.env and set PGPASSWORD (and confirm the JDBC
    host/user) from Supabase -> Connect -> Session pooler:
        sudo nano /etc/unisubmit.env
 2) In GitHub -> repo Settings -> Secrets and variables -> Actions, add:
        OCI_HOST    = ${PUBLIC_IP}
        OCI_USER    = ubuntu
        OCI_SSH_KEY = <the deploy PRIVATE key>
 3) Push to main -> GitHub Actions builds + deploys automatically.

 The unisubmit service is enabled but idle until the first deploy delivers
 the JAR. Caddy is already running and will obtain HTTPS on first request.
======================================================================
EOF
