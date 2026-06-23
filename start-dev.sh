#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "🚀 Starting Eventora Development Environment..."

# 1. Start Docker Containers
echo "📦 Starting Docker containers..."
docker-compose up -d

echo "⏳ Waiting for databases to be ready (10s)..."
sleep 10

# 2. Start Spring Boot Backend in the background
echo "☕ Starting Spring Boot backend on port 8088..."
./mvnw spring-boot:run > backend.log 2>&1 &
BACKEND_PID=$!
echo "   Backend is starting. Logs are being written to backend.log"

# Wait for backend to become available
echo "⏳ Waiting for Spring Boot to be ready on port 8088..."
while ! curl -s http://localhost:8088/actuator/health > /dev/null; do
    sleep 2
    echo -n "."
done
echo "✅ Backend is up and running!"

# 3. Start Stripe Webhook Listener in the background
echo "💳 Starting Stripe webhook listener..."
stripe listen --forward-to localhost:8088/api/v1/payments/webhook > stripe.log 2>&1 &
STRIPE_PID=$!
echo "   Stripe listener running. Logs in stripe.log"

# 4. Start Next.js Frontend
echo "💻 Starting Next.js frontend..."
cd frontend
npm run dev

# Cleanup function when user presses Ctrl+C
cleanup() {
    echo ""
    echo "🛑 Stopping services..."
    kill $BACKEND_PID
    kill $STRIPE_PID
    echo "✅ Backend and Stripe listener stopped."
    echo "   (Docker containers are still running. Run 'docker-compose down' to stop them)."
    exit 0
}

# Trap SIGINT (Ctrl+C)
trap cleanup SIGINT
