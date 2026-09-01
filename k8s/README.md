# TapRide — Kubernetes Deployment

Raw Kubernetes manifests (organized via [Kustomize](https://kustomize.io),
base + overlay) for the full TapRide architecture — not a stripped-down
"hello world," the same 3-Postgres / Redis / Kafka / 5-service topology as
`docker-compose.yml`, expressed as real StatefulSets, Deployments, Services,
Secrets, an HPA, and an Ingress.

This was deliberately built as **raw manifests + Kustomize**, not a Helm
chart — it demonstrates the underlying primitives directly rather than
filling in a template, and Kustomize's base/overlay pattern is exactly what
the original project blueprint called for (local vs. prod-style config
without duplicating YAML).

---

## What's here vs. what's deliberately left out

**Included** (the things worth demonstrating):
- StatefulSets with PVCs for all 3 Postgres instances + Kafka (real persistent
  storage, not `emptyDir`)
- Secrets for DB credentials (not plaintext env vars in a Deployment)
- Real `readinessProbe`/`livenessProbe`s wired to each service's actual
  `/actuator/health` endpoint — not placeholders
- An `HorizontalPodAutoscaler` on `matching-service` specifically (the exact
  scaling target called out in this project's original planning — see the
  root README's history)
- An `Ingress` (two, actually — see `base/ingress.yaml`'s comment for why)
  playing the same architectural role as an API Gateway: one entrypoint,
  path-based routing to each service, all without a hand-rolled gateway service

**Left out** (documented trade-offs, same practice as the rest of this project):
- **Prometheus/Grafana/Jaeger** — the full observability stack lives in
  `docker-compose.yml` for local dev; adding it here would roughly double
  this manifest set's size for a demo cluster that isn't running 24/7 anyway
- **TLS/cert-manager** — meaningful for a real public-facing cluster, not
  for a local `kind` demo
- **ArgoCD/GitOps** — `kubectl apply -k` is sufficient to demonstrate the
  manifests work; a full GitOps pipeline is a legitimate "next steps" item,
  not something a local demo needs

---

## Prerequisites

- Docker running locally
- [`kind`](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) installed
- `kubectl` installed

---

## Step 1 — Build the images

From the repo root (each Dockerfile's build context is the repo root — see
each service's own README notes on why):

```bash
docker build -t tapride/order-service:latest -f order-service/Dockerfile .
docker build -t tapride/payment-service:latest -f payment-service/Dockerfile .
docker build -t tapride/matching-service:latest -f matching-service/Dockerfile .
docker build -t tapride/notification-service:latest -f notification-service/Dockerfile .
```

For the frontend specifically, use the k8s-specific config first (see
`k8s/frontend-config.k8s.example.js` for why this differs from the
docker-compose config):

```bash
cp k8s/frontend-config.k8s.example.js frontend/js/config.js
docker build -t tapride/frontend:latest frontend/
```

---

## Step 2 — Create the kind cluster

```bash
kind create cluster --name tapride --config k8s/kind-config.yaml
```

---

## Step 3 — Load the images into the cluster

kind can't pull these from a registry (they're not published anywhere) — they
need to be loaded directly into the cluster's node:

```bash
kind load docker-image tapride/order-service:latest --name tapride
kind load docker-image tapride/payment-service:latest --name tapride
kind load docker-image tapride/matching-service:latest --name tapride
kind load docker-image tapride/notification-service:latest --name tapride
kind load docker-image tapride/frontend:latest --name tapride
```

---

## Step 4 — Install nginx-ingress (kind's own documented setup)

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

---

## Step 5 — Point `tapride.local` at your cluster

Add this line to your hosts file (`/etc/hosts` on Mac/Linux,
`C:\Windows\System32\drivers\etc\hosts` on Windows, needs admin/sudo):

```
127.0.0.1 tapride.local
```

---

## Step 6 — Apply the manifests

```bash
kubectl apply -k k8s/overlays/local
```

Watch everything come up (Postgres/Kafka take the longest — their readiness
probes have real startup delays, matching what you'd see in docker-compose):

```bash
kubectl get pods -n tapride -w
```

---

## Step 7 — Open it

```
http://tapride.local
```

Book a ride exactly as you would via docker-compose — the whole saga, the
live map, chaos controls, all of it, now running on real Kubernetes.

---

## Demoing the HPA

```bash
kubectl get hpa -n tapride -w
```

In another terminal, generate load against matching-service indirectly by
booking many rides in a loop:

```bash
for i in $(seq 1 50); do
  curl -s -X POST http://tapride.local/order-api/api/rides -H "Content-Type: application/json" -d '{"riderId":"11111111-1111-1111-1111-111111111111","pickupLat":22.72,"pickupLng":75.86,"dropoffLat":22.75,"dropoffLng":75.90}' 
    > /dev/null &
done
```

Watch `matching-service`'s replica count climb in the `kubectl get hpa -w`
terminal as CPU utilization crosses 70%.

---

## Tearing down

```bash
kind delete cluster --name tapride
```
