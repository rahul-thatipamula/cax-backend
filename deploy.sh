#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Configuration
VM_IP="144.24.142.198"
VM_USER="opc"
KEY_PATH="$HOME/Downloads/ssh-key-2026-05-29.key"
REMOTE_DIR="/opt/myapp"
JAR_NAME="cax_backend-0.0.1-SNAPSHOT.jar"

echo "--------------------------------------------------"
echo "🚀 Starting deployment to Oracle VM ($VM_IP)..."
echo "--------------------------------------------------"

# Step 1: Build the JAR locally
echo "📦 Step 1: Building Spring Boot JAR locally..."
./gradlew clean bootJar

if [ ! -f "build/libs/$JAR_NAME" ]; then
    echo "❌ Error: Built JAR file not found at build/libs/$JAR_NAME"
    exit 1
fi
echo "✅ Local build successful."

# Step 2: Stop application on VM to free up RAM
echo "🛑 Step 2: Stopping running application to free up RAM..."
ssh -i "$KEY_PATH" "$VM_USER@$VM_IP" "sudo systemctl stop cax-backend || true"
ssh -i "$KEY_PATH" "$VM_USER@$VM_IP" "mkdir -p $REMOTE_DIR"

# Step 3: Copy JAR and configuration to VM
echo "📤 Step 3: Uploading JAR and configuration..."
scp -i "$KEY_PATH" build/libs/"$JAR_NAME" "$VM_USER@$VM_IP:$REMOTE_DIR/"
scp -i "$KEY_PATH" src/main/resources/application.yml "$VM_USER@$VM_IP:$REMOTE_DIR/"

# Step 4: Start application on VM using Systemd
echo "🔄 Step 4: Starting application on Oracle VM..."
ssh -i "$KEY_PATH" "$VM_USER@$VM_IP" "sudo systemctl start cax-backend"

echo "--------------------------------------------------"
echo "🎉 Deployment completed successfully!"
echo "--------------------------------------------------"
