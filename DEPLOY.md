# Deploying MindSpace

The env-var names below are the same on any host. Two options:

## Option A — 100% FREE (recommended for launch/testing)
- **Database** → **Neon** (free serverless Postgres, no time limit)
- **Backend** → **Render** free web service (deploys this Dockerfile; sleeps after
  15 min idle, ~30–60s cold start on the next request)
- **3 frontends** → **Vercel** free (one project per repo)

Total cost: **$0**. Tradeoff: the backend cold-starts after inactivity.

## Option B — AWS (not free: App Runner ~$5+/mo, RDS free only 12 months)
- **Backend** → **AWS App Runner** (Docker)
- **Database** → **Amazon RDS for PostgreSQL**
- **3 frontends** → **AWS Amplify Hosting**

Deploy order (both options): **DB → backend → frontends** (frontends need the backend URL).

---

## 1. Database — Amazon RDS (PostgreSQL)
1. RDS → Create database → **PostgreSQL**, template **Free tier** (or Dev/Test).
2. Set a master username/password; DB name `mindspace`.
3. Connectivity: **Public access = Yes** (simplest to start), and in the security
   group add an inbound rule for **PostgreSQL 5432** from the App Runner egress
   (or `0.0.0.0/0` temporarily while testing — tighten later).
4. Note the endpoint, e.g. `mindspace.xxxx.eu-west-1.rds.amazonaws.com`.
   Your JDBC URL:
   `jdbc:postgresql://<endpoint>:5432/mindspace`

## 2. Backend — AWS App Runner (Docker)
This repo has a `Dockerfile`. Two ways to deploy:
- **From GitHub**: App Runner → Create service → Source = GitHub → this repo →
  "Use a configuration file" = No → Build = Dockerfile. OR
- **From ECR**: build & push the image, then point App Runner at it.

Service settings:
- **Port: 8080**
- **Environment variables** (App Runner → Configuration): set ALL of the list below
  (the local `application.properties` is gitignored and NOT in the image).
- After it deploys you get a URL like `https://abc123.eu-west-1.awsapprunner.com` —
  call it **BACKEND_URL**.

### Required environment variables (App Runner)
Spring maps these names automatically (relaxed binding).

```
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/mindspace
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<rds password>

# Auth
APP_JWT_SECRET=<a long random 48+ char string>
APP_JWT_EXPIRATION=86400000
APP_ADMIN_EMAILS=kelvinkiumbe589@gmail.com

# User-app URL (Google login redirects back here)
APP_FRONTEND_URL=<USER_APP_URL>

# CORS — your deployed frontend URLs (comma-separated, no trailing slash)
APP_CORS_ORIGINS=https://user.example.com,https://admin.example.com,https://therapist.example.com

# AI
APP_GEMINI_API_KEY=<gemini key>

# Email (Gmail SMTP)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=kelvinkiumbe589@gmail.com
SPRING_MAIL_PASSWORD=<gmail app password>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
APP_CONTACT_RECIPIENT=kelvinkiumbe589@gmail.com

# Payments — Pesapal (LIVE)
APP_PESAPAL_BASE_URL=https://pay.pesapal.com/v3
APP_PESAPAL_CONSUMER_KEY=<pesapal key>
APP_PESAPAL_CONSUMER_SECRET=<pesapal secret>
APP_PESAPAL_CURRENCY=KES
APP_PESAPAL_IPN_URL=<BACKEND_URL>/api/payments/pesapal/ipn
APP_PESAPAL_CALLBACK_URL=<USER_APP_URL>/find-a-therapist

# Commission on therapist withdrawals
APP_COMMISSION_PERCENT=15

# Google OAuth (optional; only if using Google sign-in)
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<secret>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=email,profile
```

(If you still use the direct M-Pesa Daraja endpoints, also set the `APP_MPESA_*`
vars the same way. Pesapal is the primary path.)

## 3. Frontends — AWS Amplify Hosting (x3)
For each of the three repos (user app, `mindspace-admin`, `mindspace-therapist`):
1. Amplify → New app → Host web app → connect the GitHub repo.
2. Build settings (auto-detected Vite):
   - Build command: `npm run build`
   - Output directory: `dist`
3. Environment variables → add **`VITE_API_BASE = <BACKEND_URL>`**.
4. Deploy → you get a URL like `https://main.d123.amplifyapp.com`.

Then go back and set the backend's **`APP_CORS_ORIGINS`** to these three Amplify
URLs and redeploy the App Runner service.

## 4. Post-deploy wiring
- **Pesapal**: `APP_PESAPAL_IPN_URL` now points at your real BACKEND_URL — no more
  ngrok. (Pesapal re-registers the IPN automatically on the first order.)
- **Google OAuth**: in Google Cloud console add the redirect URI
  `<BACKEND_URL>/login/oauth2/code/google`, and update `OAuth2SuccessHandler`'s
  frontend redirect (currently `http://localhost:5173/dashboard`) to your user-app URL.
- **DB schema**: `spring.jpa.hibernate.ddl-auto=update` creates tables on first boot.
- SPA routing on Amplify: add a rewrite rule `/<*>  →  /index.html  (200)` so deep
  links work.

## Local dev is unchanged
Without `VITE_API_BASE`, the frontends default to `http://localhost:8080`, and the
backend reads `application.properties`. Nothing about local development changes.
