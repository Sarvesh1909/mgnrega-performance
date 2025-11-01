#!/bin/bash

# MGNREGA Application Deployment Script
# This script automates the deployment process on Ubuntu/Debian servers

set -e

echo "🚀 Starting MGNREGA Application Deployment..."
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
APP_DIR="/opt/mgnrega"
APP_USER="${SUDO_USER:-$USER}"
DB_NAME="mgnrega"
DB_USER="mgnrega_user"
DOMAIN_NAME="${DOMAIN_NAME:-_}"  # Use _ for IP-based access

# Check if running as root or with sudo
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}Please run as root or with sudo${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Running as root/sudo"

# Step 1: Update system
echo -e "\n${YELLOW}[1/9]${NC} Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq

# Step 2: Install required packages
echo -e "\n${YELLOW}[2/9]${NC} Installing required packages..."

# Java 17
if ! command -v java &> /dev/null || ! java -version 2>&1 | grep -q "17"; then
    echo "  Installing Java 17..."
    apt-get install -y openjdk-17-jdk > /dev/null
fi
echo -e "${GREEN}✓${NC} Java installed"

# Maven
if ! command -v mvn &> /dev/null; then
    echo "  Installing Maven..."
    apt-get install -y maven > /dev/null
fi
echo -e "${GREEN}✓${NC} Maven installed"

# PostgreSQL
if ! command -v psql &> /dev/null; then
    echo "  Installing PostgreSQL..."
    apt-get install -y postgresql postgresql-contrib > /dev/null
    systemctl start postgresql
    systemctl enable postgresql
fi
echo -e "${GREEN}✓${NC} PostgreSQL installed"

# Node.js
if ! command -v node &> /dev/null; then
    echo "  Installing Node.js..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - > /dev/null
    apt-get install -y nodejs > /dev/null
fi
echo -e "${GREEN}✓${NC} Node.js installed"

# Nginx
if ! command -v nginx &> /dev/null; then
    echo "  Installing Nginx..."
    apt-get install -y nginx > /dev/null
    systemctl enable nginx
fi
echo -e "${GREEN}✓${NC} Nginx installed"

# Step 3: Setup PostgreSQL
echo -e "\n${YELLOW}[3/9]${NC} Setting up database..."

# Generate random password if not set
if [ -z "$DB_PASSWORD" ]; then
    DB_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-25)
    echo -e "${YELLOW}Generated database password: ${DB_PASSWORD}${NC}"
    echo -e "${YELLOW}Save this password securely!${NC}"
fi

# Create database and user
sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME};" 2>/dev/null || echo "  Database may already exist"
sudo -u postgres psql -c "DROP USER IF EXISTS ${DB_USER};" 2>/dev/null || true
sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"
sudo -u postgres psql -c "ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};"

echo -e "${GREEN}✓${NC} Database configured"

# Step 4: Create application directory
echo -e "\n${YELLOW}[4/9]${NC} Setting up application directory..."
mkdir -p ${APP_DIR}
mkdir -p /var/log/mgnrega
chown -R ${APP_USER}:${APP_USER} ${APP_DIR}
chown -R ${APP_USER}:${APP_USER} /var/log/mgnrega

echo -e "${GREEN}✓${NC} Directories created"

# Step 5: Copy application files
echo -e "\n${YELLOW}[5/9]${NC} Copying application files..."
echo "  Current directory: $(pwd)"
echo "  Target directory: ${APP_DIR}"

# Copy backend
echo "  Copying backend..."
cp -r backend ${APP_DIR}/
# Copy frontend
echo "  Copying frontend..."
cp -r frontend ${APP_DIR}/
# Copy .env.example if exists
if [ -f .env.example ]; then
    cp .env.example ${APP_DIR}/.env.example
fi

chown -R ${APP_USER}:${APP_USER} ${APP_DIR}

echo -e "${GREEN}✓${NC} Files copied"

# Step 6: Build application
echo -e "\n${YELLOW}[6/9]${NC} Building application..."

# Build backend
echo "  Building backend..."
cd ${APP_DIR}/backend
sudo -u ${APP_USER} mvn clean package -DskipTests -q
echo -e "${GREEN}✓${NC} Backend built"

# Build frontend
echo "  Building frontend..."
cd ${APP_DIR}/frontend
sudo -u ${APP_USER} npm install --silent
# Set API base URL to empty (will use relative URLs)
VITE_API_BASE_URL="" sudo -u ${APP_USER} npm run build
echo -e "${GREEN}✓${NC} Frontend built"

cd ${APP_DIR}

# Step 7: Create environment file
echo -e "\n${YELLOW}[7/9]${NC} Creating environment configuration..."

# Check if API key is provided
if [ -z "$DATAGOV_API_KEY" ]; then
    echo -e "${YELLOW}Warning: DATAGOV_API_KEY not set. Please update /etc/mgnrega.env after deployment.${NC}"
    DATAGOV_API_KEY="your_api_key_here"
fi

cat > /etc/mgnrega.env <<EOF
DATABASE_URL=jdbc:postgresql://localhost:5432/${DB_NAME}
DATABASE_USER=${DB_USER}
DATABASE_PASSWORD=${DB_PASSWORD}
DATAGOV_API_KEY=${DATAGOV_API_KEY}
SERVER_PORT=9090
USE_DATABASE=true
CACHE_TTL_SECONDS=900
DATAGOV_MAX_RETRIES=3
SPRING_PROFILES_ACTIVE=prod
EOF

chmod 600 /etc/mgnrega.env
echo -e "${GREEN}✓${NC} Environment file created at /etc/mgnrega.env"

# Step 8: Create systemd service
echo -e "\n${YELLOW}[8/9]${NC} Creating systemd service..."

cat > /etc/systemd/system/mgnrega-backend.service <<EOF
[Unit]
Description=MGNREGA Backend Service
After=postgresql.service network.target
Requires=postgresql.service

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${APP_DIR}/backend
EnvironmentFile=/etc/mgnrega.env
ExecStart=/usr/bin/java -jar ${APP_DIR}/backend/target/backend-0.0.1-SNAPSHOT.jar --spring.config.location=classpath:/application.properties,classpath:/application-prod.properties
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mgnrega-backend

# Resource limits
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable mgnrega-backend

echo -e "${GREEN}✓${NC} Systemd service created"

# Step 9: Configure Nginx
echo -e "\n${YELLOW}[9/9]${NC} Configuring Nginx..."

cat > /etc/nginx/sites-available/mgnrega <<EOF
server {
    listen 80;
    server_name ${DOMAIN_NAME};
    
    # Frontend
    root ${APP_DIR}/frontend/dist;
    index index.html;
    
    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript application/xml+rss;
    
    # Frontend routes
    location / {
        try_files \$uri \$uri/ /index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }
    
    # Static assets caching
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    # Backend API
    location /api {
        proxy_pass http://localhost:9090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 300s;
        
        # Buffer settings
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;
        proxy_busy_buffers_size 8k;
    }
    
    # Health check endpoint
    location /health {
        proxy_pass http://localhost:9090/api/health;
        access_log off;
    }
}
EOF

# Enable site
ln -sf /etc/nginx/sites-available/mgnrega /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true

# Test and restart nginx
nginx -t && systemctl restart nginx

echo -e "${GREEN}✓${NC} Nginx configured"

# Step 10: Start services
echo -e "\n${YELLOW}[Starting Services]${NC}"
systemctl start mgnrega-backend
sleep 5

if systemctl is-active --quiet mgnrega-backend; then
    echo -e "${GREEN}✓${NC} Backend service started"
else
    echo -e "${RED}✗${NC} Backend service failed to start. Check logs: journalctl -u mgnrega-backend -n 50"
fi

# Step 11: Configure firewall
echo -e "\n${YELLOW}[Firewall]${NC}"
if command -v ufw &> /dev/null; then
    ufw allow 80/tcp
    ufw allow 443/tcp
    echo -e "${GREEN}✓${NC} Firewall configured"
fi

# Get server IP
SERVER_IP=$(hostname -I | awk '{print $1}')

# Summary
echo -e "\n${GREEN}================================================"
echo "✅ Deployment Complete!"
echo "================================================${NC}"
echo ""
echo "📋 Configuration:"
echo "   Database: ${DB_NAME}"
echo "   DB User: ${DB_USER}"
echo "   DB Password: ${DB_PASSWORD}"
echo "   Environment file: /etc/mgnrega.env"
echo ""
echo "🌐 Access your application:"
echo "   http://${SERVER_IP}"
if [ "$DOMAIN_NAME" != "_" ]; then
    echo "   http://${DOMAIN_NAME}"
fi
echo ""
echo "📝 Useful commands:"
echo "   Check backend status: sudo systemctl status mgnrega-backend"
echo "   View backend logs: sudo journalctl -u mgnrega-backend -f"
echo "   Restart backend: sudo systemctl restart mgnrega-backend"
echo "   Check nginx: sudo systemctl status nginx"
echo "   View nginx logs: sudo tail -f /var/log/nginx/error.log"
echo ""
echo -e "${YELLOW}⚠️  IMPORTANT: Update DATAGOV_API_KEY in /etc/mgnrega.env if not set!${NC}"
echo ""
