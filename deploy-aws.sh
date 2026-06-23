#!/bin/bash
# Exit immediately if a command exits with a non-zero status
set -e

# Configuration (Read from deploy.config if it exists, otherwise use defaults)
CONFIG_FILE="$(dirname "$0")/deploy.config"
if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
fi

VM_HOST="${DEPLOY_VM_HOST:-ec2-100-31-76-116.compute-1.amazonaws.com}"
VM_USER="${DEPLOY_VM_USER:-ubuntu}"
KEY_PATH="${DEPLOY_KEY_PATH:-$HOME/.ssh/amazon-vm-key-pair.pem}"
REMOTE_DIR="${DEPLOY_REMOTE_DIR:-/opt/myapp}"
JAR_NAME="cax_backend-0.0.1-SNAPSHOT.jar"
HEALTH_CHECK_URL="http://localhost:8080/health"

echo "--------------------------------------------------"
echo "🚀 Starting Secure Deployment to AWS EC2 ($VM_HOST)..."
echo "--------------------------------------------------"

# Fix and validate PEM key path/permissions
if [ ! -f "$KEY_PATH" ]; then
    echo "❌ Error: SSH Private Key not found at $KEY_PATH"
    echo "Please update the KEY_PATH or create a 'deploy.config' file."
    exit 1
fi
chmod 400 "$KEY_PATH"

# Step 1: Build the JAR locally
echo "📦 Step 1: Building Spring Boot JAR locally..."
./gradlew clean bootJar

if [ ! -f "build/libs/$JAR_NAME" ]; then
    echo "❌ Error: Built JAR file not found at build/libs/$JAR_NAME"
    exit 1
fi
echo "✅ Local build successful."

# Helper SSH function to enforce secure flags
ssh_cmd() {
    # Using 'accept-new' permits logging the host key on the first connection, 
    # but prevents Man-in-the-Middle if it changes later.
    ssh -i "$KEY_PATH" -o StrictHostKeyChecking=accept-new "$VM_USER@$VM_HOST" "$@"
}

# Helper SCP function
scp_cmd() {
    scp -i "$KEY_PATH" -o StrictHostKeyChecking=accept-new "$@"
}

# Step 2: Backup previous JAR on the VM
echo "🛑 Step 2: Stopping running application and backing up old JAR..."
ssh_cmd "sudo systemctl stop cax-backend || true"
ssh_cmd "sudo mkdir -p $REMOTE_DIR && sudo chown -R $VM_USER:$VM_USER $REMOTE_DIR"

# Create a backup of the current JAR if it exists
ssh_cmd "[ -f $REMOTE_DIR/$JAR_NAME ] && cp $REMOTE_DIR/$JAR_NAME $REMOTE_DIR/$JAR_NAME.bak || echo 'No previous build found to back up.'"

# Step 3: Copy JAR to VM
echo "📤 Step 3: Uploading JAR..."
scp_cmd build/libs/"$JAR_NAME" "$VM_USER@$VM_HOST:$REMOTE_DIR/"

# Upload .env file securely to VM if it exists locally
if [ -f ".env" ]; then
    echo "🔑 Uploading and securing backend .env file..."
    scp_cmd .env "$VM_USER@$VM_HOST:$REMOTE_DIR/.env"
    ssh_cmd "chmod 600 $REMOTE_DIR/.env"
else
    echo "⚠️ Warning: No local .env file found in cax_backend root. Secrets will not be sync'd."
fi

# Upload Firebase service account JSON if it exists locally
LOCAL_SA_FILE="${FIREBASE_SERVICE_ACCOUNT_LOCAL:-/opt/myapp/service-account.json}"
REMOTE_SA_FILE="$REMOTE_DIR/service-account.json"
if [ -f "$LOCAL_SA_FILE" ]; then
    echo "🔑 Uploading Firebase service account..."
    scp_cmd "$LOCAL_SA_FILE" "$VM_USER@$VM_HOST:$REMOTE_SA_FILE"
    ssh_cmd "chmod 600 $REMOTE_SA_FILE"
    echo "✅ Service account uploaded to $REMOTE_SA_FILE"
else
    echo "⚠️ Warning: Firebase service account not found at $LOCAL_SA_FILE. Push notifications may not work."
fi

# Step 4: Start application on VM using Systemd
echo "🔄 Step 4: Starting application on AWS EC2..."
ssh_cmd "sudo systemctl start cax-backend"

# Step 5: Post-deployment Health Check
echo "📋 Step 5: Checking application health..."
MAX_ATTEMPTS=15
ATTEMPT=1
SUCCESS=false

# Wait for Spring Boot JVM startup and poll health endpoint
echo "Waiting for backend to initialize (polling localhost:8080 on VM)..."
while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    # Execute curl local to VM so we do not expose port 8080 to the public web
    HTTP_STATUS=$(ssh_cmd "curl -s -o /dev/null -w '%{http_code}' $HEALTH_CHECK_URL || echo '000'")
    
    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ Application is healthy! (HTTP 200)"
        SUCCESS=true
        break
    else
        echo "⏳ Attempt $ATTEMPT/$MAX_ATTEMPTS: App is not ready yet (Status: $HTTP_STATUS). Retrying in 4s..."
        sleep 4
        ATTEMPT=$((ATTEMPT + 1))
    fi
done

if [ "$SUCCESS" = false ]; then
    echo "❌ Error: Application failed to start successfully within the time limit."
    echo "📋 Fetching system logs..."
    ssh_cmd "sudo journalctl -u cax-backend -n 50 --no-pager"
    
    echo "🔄 Attempting ROLLBACK to last working version..."
    ssh_cmd "if [ -f $REMOTE_DIR/$JAR_NAME.bak ]; then
                mv $REMOTE_DIR/$JAR_NAME.bak $REMOTE_DIR/$JAR_NAME
                sudo systemctl restart cax-backend
                echo '✅ Rollback completed. Restored last working JAR.'
             else
                echo '❌ Rollback failed: No backup JAR found.'
             fi"
    exit 1
fi

echo "--------------------------------------------------"
echo "🎉 Deployment to AWS EC2 completed successfully!"
echo "--------------------------------------------------"
