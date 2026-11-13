# Eexchange — Deployment Guide (single EC2, no Nginx, no load balancer)

Production deployment of the **backend** on one **AWS EC2 t3.micro (1 GB RAM)** instance, running
the Docker image from Docker Hub directly on port `8080`.

External services: **Supabase** (Postgres), **Upstash** (Redis), **S3** (images), **Brevo** (email),
**OpenRouter** (AI moderation). The **frontend** is deployed separately on Vercel.

There is **no Nginx**, **no Application Load Balancer**, **no Docker Postgres/Redis** on EC2.

---

## 0. Instructions for the next AI assistant

When the user asks "give me the commands again", execute this order and give **copy-paste bash
commands**, not theory:

1. AWS Console: create the EC2 instance (Section 3) and security group.
2. SSH into the instance (Section 4).
3. Install Docker + create swap (Section 4).
4. Create `/home/ec2-user/eexchange/.env` (Section 5) — the only application file on the server.
5. `docker pull` the image from Docker Hub and `docker run` it (Section 6).
6. Verify with `docker logs` and `curl /actuator/health` (Section 8).
7. To change environment variables: edit `.env`, then **recreate** the container (Section 7).
   A plain `docker restart` is NOT enough — Docker env vars are fixed at container creation.

Never tell the user to upload the source tree, `target/`, `Frontend/`, tests, or
`docker-compose.yml` to EC2. Only `.env` is placed on the server.

---

## 1. Repository layout

```
Eexchange/                                  ← Git repo root
│
├── backend/                                ← ONLY directory used to build the backend image
│   ├── Dockerfile                          ← 2-stage build: Maven build → JRE runtime
│   ├── .dockerignore                       ← keeps target/, .env, .git, logs out of the build
│   ├── pom.xml                             ← Spring Boot 3.5.6, Java 21, actuator, S3, JWT, Flyway
│   ├── mvnw
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── .env                                ← REAL SECRETS, gitignored, NEVER in image
│   ├── .env.example                        ← committed template/reference
│   ├── docker-compose.yml                  ← LOCAL DEV ONLY (Postgres + Redis), not deployed
│   │
│   └── src/
│       ├── main/
│       │   ├── java/com/eexchange/backend/
│       │   │   ├── BackendApplication.java        ← main(), @EnableScheduling, @EnableAsync
│       │   │   ├── Audit/
│       │   │   ├── Configuration/                 ← SecurityConfig, AdminBootstrap,
│       │   │   │                                    ModerationProperties(+Validator),
│       │   │   │                                    StorageConfig, RedisRateLimiter,
│       │   │   │                                    Filters/ (JWT, auth rate limit, request id)
│       │   │   ├── Controller/                    ← REST endpoints (/api/...)
│       │   │   ├── DTO/Request, DTO/Response
│       │   │   ├── Entity/ + Entity/Enums/
│       │   │   ├── Exception/
│       │   │   ├── Repository/                    ← Spring Data JPA
│       │   │   ├── Service/
│       │   │   │   ├── Cache/                     ← feed version + feed cache
│       │   │   │   ├── Event/                     ← PostCreatedEvent + listener
│       │   │   │   ├── Moderation/                ← AI moderation, retry job, processor
│       │   │   │   └── Storage/                   ← S3 storage
│       │   │   └── Utils/
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-local.properties
│       │       ├── application-prod.properties
│       │       └── db/migration/
│       │           ├── V1__init_schema.sql
│       │           ├── V2__seed_data.sql
│       │           └── V3__add_post_edit_count.sql
│       └── test/                                  ← NOT in the image
│
├── Frontend/Eexchange/                    ← separate app on Vercel; NOT on EC2
│   ├── vite.config.js
│   ├── .env                               ← VITE_API_URL (build-time), gitignored
│   └── src/
│
└── README.md
```

### Keep / do not keep

| Item | Git | Docker image | EC2 server |
|---|---|---|---|
| `backend/src/**`, `pom.xml`, `mvnw`, `.mvn` | yes | build stage only | **no** |
| `backend/target/**` | no | no | **no** |
| `backend/.env` | no (gitignored) | no | **yes** (only file you place) |
| `backend/docker-compose.yml` | yes | no | **no** |
| `backend/src/test/**` | yes | no | no |
| `Frontend/**` | yes | no | **no** (Vercel) |
| Docker image | — | yes (Docker Hub) | pulled |

Only **two things** exist on EC2: Docker, and `/home/ec2-user/eexchange/.env`.
The final image contains only `/app/app.jar` (no source, no `.env`, no database).

---

## 2. Public endpoints

| Endpoint | Access |
|---|---|
| `GET /actuator/health` | public (DB + Redis health), returns `200` UP / `503` DOWN |
| `/api/auth/**`, `/api/admin/auth/**` | public |
| `GET /api/colleges`, `GET /api/categories` | public |
| `/api/admin/**` | JWT + `ROLE_SUPER_ADMIN` / `ROLE_MODERATOR` |
| everything else | JWT required |

The container listens on `8080` (`SERVER_PORT`).

---

## 3. Create the EC2 instance (AWS Console)

1. **AMI:** Amazon Linux 2023, x86_64.
2. **Instance type:** `t3.micro` (1 GB RAM). If your account is still in the 12-month
   **Free Tier**, `t3.micro`/`t2.micro` is free for 750 hours/month.
3. **Storage:** 16 GB gp3 (8 GB is tight once the image + swap are added).
4. **Key pair:** create/download the `.pem`.
5. **Network:** default VPC, **public subnet**, **auto-assign public IPv4 = enabled**
   (do NOT add a NAT gateway — it costs ~$32/month).
6. **Security group inbound:**
   - `22` from **your IP only**
   - `8080` from `0.0.0.0/0` (the browser calls the API directly; this is required)
   - do not open 80/443 (no proxy is used)
7. **Outbound:** default allow-all (needed for Supabase, Upstash, S3, OpenRouter, Brevo).

Cost notes:

- Do **not** allocate an Elastic IP if you plan to stop the instance often; a public IPv4 is
  billed the same (~$3.60/month) and an idle Elastic IP is also billed. Use the instance's
  current public IP.
- If you stop and start the instance, the **public IPv4 changes** → update the frontend
  API URL (or Vercel rewrite) accordingly.
- Do not enable detailed monitoring, extra EBS volumes, or a NAT gateway.

---

## 4. First SSH + Docker + swap

```bash
chmod 400 eexchange-key.pem
ssh -i eexchange-key.pem ec2-user@<EC2_PUBLIC_IP>
```

Install Docker:

```bash
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
newgrp docker
docker --version
```

Create a 1 GB swap file (critical on a 1 GB instance):

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
free -h
```

---

## 5. Place the `.env` file

From your Windows machine (the file is gitignored, so it is not in the repo/image):

```powershell
ssh -i E:\path\eexchange-key.pem ec2-user@<EC2_PUBLIC_IP> "mkdir -p /home/ec2-user/eexchange"
scp -i E:\path\eexchange-key.pem .env ec2-user@<EC2_PUBLIC_IP>:/home/ec2-user/eexchange/.env
```

On EC2:

```bash
chmod 600 /home/ec2-user/eexchange/.env
```

### Creating it directly on EC2 instead

```bash
mkdir -p /home/ec2-user/eexchange
nano /home/ec2-user/eexchange/.env
```

Required production values:

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Xss512k

# Supabase — session pooler (IPv4; direct db.<ref>.supabase.co is IPv6-only)
DB_HOST=aws-0-ap-northeast-2.pooler.supabase.com
DB_PORT=5432
POSTGRES_DB=postgres
POSTGRES_USER=postgres.uqyqhubbiqrbatymfdqq
POSTGRES_PASSWORD=<supabase-password>
DB_SSLMODE=require

# Upstash
REDIS_HOST=<endpoint>.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=<upstash-password>
REDIS_SSL=true

# JWT (openssl rand -base64 48)
APP_JWT_SECRET=<random>
APP_ADMIN_JWT_SECRET=<different-random>

# Frontend / CORS (comma-separated list, no trailing slash, no path)
APP_PASSWORD_RESET_BASE_URL=https://frontend-eexchange.vercel.app/reset-password
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://frontend-eexchange.vercel.app

# Brevo
BREVO_API_KEY=<key>
BREVO_SENDER_EMAIL=<verified-sender@example.com>
BREVO_SENDER_NAME=EExchange

# S3
AWS_ACCESS_KEY_ID=<key>
AWS_SECRET_ACCESS_KEY=<secret>
S3_BUCKET=<bucket>
S3_REGION=<region>
STORAGE_PUBLIC_BASE_URL=https://<bucket>.s3.<region>.amazonaws.com

# AI moderation
MODERATION_PROVIDER=openrouter
OPENROUTER_API_KEY=<key>
OPENROUTER_MODEL=nvidia/nemotron-3.5-super-vl

# First-boot admin (only when the admins table is empty)
APP_ADMIN_USERNAME=admin
APP_ADMIN_EMAIL=admin@<domain>
APP_ADMIN_PASSWORD=<at-least-12-chars-not-equal-to-username-or-email>
```

Docker `--env-file` treats values literally: **no quotes**, no variable expansion.

---

## 6. Build (local), push to Docker Hub, run on EC2

### Build and push from your machine

Run in `E:\Projects\Eexchange\backend` (Docker Desktop running):

```powershell
docker login -u <dockerhub-username>

docker build -t <dockerhub-username>/eexchange-backend:1.0.0 .
docker tag <dockerhub-username>/eexchange-backend:1.0.0 <dockerhub-username>/eexchange-backend:latest

docker push <dockerhub-username>/eexchange-backend:1.0.0
docker push <dockerhub-username>/eexchange-backend:latest
```

If building on an ARM Mac, add `--platform linux/amd64`.

### Run on EC2

```bash
docker pull <dockerhub-username>/eexchange-backend:1.0.0

docker run -d \
  --name eexchange-api \
  --restart unless-stopped \
  --env-file /home/ec2-user/eexchange/.env \
  -p 8080:8080 \
  --memory=850m \
  --memory-swap=1850m \
  <dockerhub-username>/eexchange-backend:1.0.0
```

`--memory` keeps the JVM from starving the OS; `--memory-swap` allows the 1 GB swap file to be
used. If the container is OOM-killed, lower `-Xmx` in `JAVA_TOOL_OPTIONS`.

Check:

```bash
docker ps
docker logs -f eexchange-api        # Ctrl+C to stop following
docker stats --no-stream
```

---

## 7. Updating environment variables / redeploying

Docker reads `--env-file` **only when the container is created**. Editing `.env` and running
`docker restart` does not apply changes.

```bash
nano /home/ec2-user/eexchange/.env
```

Then recreate the container with the same image:

```bash
docker stop eexchange-api
docker rm eexchange-api

docker run -d \
  --name eexchange-api \
  --restart unless-stopped \
  --env-file /home/ec2-user/eexchange/.env \
  -p 8080:8080 \
  --memory=850m \
  --memory-swap=1850m \
  <dockerhub-username>/eexchange-backend:1.0.0
```

Deploy a new image version:

```bash
docker pull <dockerhub-username>/eexchange-backend:1.0.1
docker stop eexchange-api
docker rm eexchange-api
docker run -d --name eexchange-api --restart unless-stopped \
  --env-file /home/ec2-user/eexchange/.env \
  -p 8080:8080 --memory=850m --memory-swap=1850m \
  <dockerhub-username>/eexchange-backend:1.0.1
docker image prune -f
```

Rollback = run the previous tag. Flyway migrations are forward-only.

---

## 8. Verify

```bash
docker logs --tail 200 eexchange-api
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/colleges
```

Expected:

- logs show Flyway applying `V1`, `V2`, `V3`, then `Started BackendApplication`
- `/actuator/health` → `{"status":"UP"}`
- `/api/colleges` → `200`

From outside:

```bash
curl -i http://<EC2_PUBLIC_IP>:8080/api/colleges
```

If this fails: check the security group allows inbound `8080` from `0.0.0.0/0`, and that the
container is running (`docker ps`).

---

## 9. Networking / HTTPS without Nginx or an ALB

Current setup: the API is reachable at `http://<EC2_PUBLIC_IP>:8080`.

**Important:** the Vercel frontend is served over **HTTPS**, and browsers block calls from an
HTTPS page to an `http://` API (mixed content). Choose one of these free options:

### Option A — Vercel rewrite (no extra server software)

Point the frontend at a relative path and let Vercel proxy to the EC2 IP. In
`Frontend/Eexchange/vercel.json`:

```json
{
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "http://<EC2_PUBLIC_IP>:8080/api/:path*"
    }
  ]
}
```

Then build the frontend with `VITE_API_URL=` (empty → relative requests). The browser talks
HTTPS to Vercel; Vercel talks HTTP to EC2. Update the rewrite when the EC2 public IP changes.

### Option B — Cloudflare Tunnel (free HTTPS URL, no open ports)

```bash
# install once (x86_64)
curl -L -o cloudflared.rpm https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-x86_64.rpm
sudo rpm -i cloudflared.rpm

# quick tunnel (temporary HTTPS URL, changes on restart)
cloudflared tunnel --url http://localhost:8080
```

For a stable hostname, create a named tunnel in the Cloudflare dashboard and point a domain at
it. With a tunnel you can close port `8080` in the security group.

### Option C — accept HTTP for now

Only works if the frontend is also served over plain HTTP, or during local testing.
Do not ship this to users with the HTTPS Vercel frontend.

Whichever option you choose, keep `APP_CORS_ALLOWED_ORIGINS` set to the real frontend origin if
the browser calls the API directly. With Option A (same-origin rewrite) CORS is not exercised.

---

## 10. Frontend deployment

- Lives in `Frontend/Eexchange`, deployed on Vercel.
- `VITE_API_URL` is baked in at **build time**; set it in Vercel and redeploy.
- If using the Vercel rewrite (Option A), leave `VITE_API_URL` empty.
- `vite.config.js` proxy is for local dev only.

---

## 11. Notable implementation details

- **Feed cache invalidation** happens only when post visibility actually changes
  (`→ APPROVED` or `APPROVED → non-APPROVED`). Create, retry-later, reject, manual-review, and
  deleting a non-approved post do not invalidate the college feed.
- **AI moderation** retries are per revision; a lifetime `edit_count` freezes a post in
  `MANUAL_REVIEW` after 5 edits. Admins can approve/reject `RETRY_LATER` posts from the
  Moderation tab.
- **CORS** comes from `app.cors.allowed-origins` (comma-separated). Credentials are enabled,
  so wildcards are rejected. Trailing slashes are stripped.
- **Client IP** for login lockout and auth rate limiting uses `request.getRemoteAddr()` with
  `server.forward-headers-strategy=native`; behind a proxy/tunnel, forward `X-Forwarded-For`.
- **Scheduled jobs** (`ModerationRetryJob`, `TempBlockExpiryJob`) run in the single app
  instance. Multiple instances would need scheduling disabled on replicas or a distributed lock.
- **Memory:** the JVM is capped at `-Xmx384m` with Serial GC and `-XX:MaxMetaspaceSize=192m`;
  the host has a 1 GB swap file. This is tuned for `t3.micro`.
