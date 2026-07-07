# Self-hosted coturn (TURN) for online sessions

WebRTC connects peers directly, but when both are behind NATs/firewalls the media
must be **relayed through a TURN server**. We self-host **coturn** and the backend
mints short-lived credentials for it. Until this is set up, calls use STUN only and
will work on the same network / permissive NATs but may fail across networks.

## 1. Provision a VPS
Any small Linux VM works (1 vCPU / 1 GB is fine for low volume): Hetzner, DigitalOcean,
Oracle Cloud free tier, etc. Point a DNS A record at it, e.g. `turn.yourdomain.com`.

## 2. Install coturn
```bash
sudo apt update && sudo apt install -y coturn
sudo sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/' /etc/default/coturn
```

## 3. TLS cert (for turns:5349)
```bash
sudo apt install -y certbot
sudo certbot certonly --standalone -d turn.yourdomain.com
```

## 4. Configure `/etc/turnserver.conf`
```
listening-port=3478
tls-listening-port=5349
fingerprint
use-auth-secret
static-auth-secret=REPLACE_WITH_A_LONG_RANDOM_SECRET
realm=turn.yourdomain.com
# TLS
cert=/etc/letsencrypt/live/turn.yourdomain.com/fullchain.pem
pkey=/etc/letsencrypt/live/turn.yourdomain.com/privkey.pem
# Relay port range (open these in the firewall too)
min-port=49152
max-port=65535
no-cli
# Optional hardening
no-multicast-peers
```

## 5. Firewall
Open: `3478/tcp`, `3478/udp`, `5349/tcp`, `5349/udp`, and `49152-65535/udp`.
```bash
sudo ufw allow 3478
sudo ufw allow 5349
sudo ufw allow 49152:65535/udp
```

## 6. Start
```bash
sudo systemctl enable coturn
sudo systemctl restart coturn
```

## 7. Point the backend at it (Render env vars)
Set on the `mindspace-backend` service — **must match** `static-auth-secret`:
```
APP_TURN_HOST   = turn.yourdomain.com
APP_TURN_SECRET = <the same long random secret>
APP_TURN_TTL    = 3600        # optional (seconds)
```
The backend's `GET /api/sessions/{id}/room` then returns TURN entries with a
time-limited `username=<expiry>:mindspace` and `credential=base64(HMAC-SHA1(secret,username))`,
which coturn validates via its REST-auth (`use-auth-secret`) scheme. No static TURN
password is ever shipped to the browser.

## 8. Verify
Use https://icetest.info or Trickle ICE
(https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/) with:
`turn:turn.yourdomain.com:3478`, username/credential from the `/room` response.
You should see a `relay` candidate.
