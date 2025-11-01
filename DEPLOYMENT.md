# Production Deployment Guide

## Prerequisites

- Ubuntu 20.04+ or similar Linux distribution
- Root or sudo access
- Domain name (optional) or server IP address
- Data.gov.in API key

## Step-by-Step Deployment

### 1. Server Setup

```bash
# Update system
sudo apt-get update && sudo apt-get upgrade -y

# Install Java 17
sudo apt-get install -y openjdk-17-jdk

# Install Maven
sudo apt-get install -y maven

# Install PostgreSQL
sudo apt-get install -y postgresql postgresql-contrib

# Install Node.js and npm
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install Nginx
sudo apt-get install -y nginx
```

### 2. Database Configuration

```bash
# Switch to postgres user
sudo -u postgres psql

# In PostgreSQL prompt:
CREATE DATABASE mgnrega;
CREATE USER mgnrega_user WITH PASSWORD 'your_secure_password_here';
GRANT ALL PRIVILEGES ON DATABASE mgnrega TO mgnrega_user;
\q
```

### 3. Clone and Build Application

```bash
# Clone repository (or upload files)
cd /opt
sudo mkdir -p mgnrega
sudo chown $USER:$USER mgnrega
cd mgnrega

# Upload or clone your code here

# Build backend
cd backend
mvn clean package -DskipTests
cd ..

# Build frontend
cd frontend
npm install
npm run build
cd ..
```

### 4. Configure Environment Variables

Create `/etc/mgnrega.env`:

```bash
sudo nano /etc/mgnrega.env
```

Add:
```
DATABASE_URL=jdbc:postgresql://localhost:5432/mgnrega
DATABASE_USER=mgnrega_user
DATABASE_PASSWORD=your_secure_password_here
DATAGOV_API_KEY=your_api_key_here
SERVER_PORT=9090
USE_DATABASE=true
CORS_ALLOWED_ORIGINS=*
```

### 5. Create Systemd Service

```bash
sudo nano /etc/systemd/system/mgnrega-backend.service
```

Add:
```ini
[Unit]
Description=MGNREGA Backend Service
After=postgresql.service network.target

[Service]
Type=simple
User=your_username
WorkingDirectory=/opt/mgnrega/backend
EnvironmentFile=/etc/mgnrega.env
ExecStart=/usr/bin/java -jar /opt/mgnrega/backend/target/backend-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable mgnrega-backend
sudo systemctl start mgnrega-backend
sudo systemctl status mgnrega-backend
```

### 6. Configure Nginx

```bash
sudo nano /etc/nginx/sites-available/mgnrega
```

Add:
```nginx
server {
    listen 80;
    server_name your_domain.com;  # or your_server_ip

    # Frontend
    root /opt/mgnrega/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Backend API
    location /api {
        proxy_pass http://localhost:9090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        
        # Increase timeouts for large responses
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
}
```

Enable site:
```bash
sudo ln -s /etc/nginx/sites-available/mgnrega /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 7. SSL Certificate (Optional but Recommended)

```bash
# Install Certbot
sudo apt-get install -y certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d your_domain.com

# Auto-renewal is set up automatically
```

### 8. Firewall Configuration

```bash
# Allow HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# If needed, allow backend directly (not recommended in production)
# sudo ufw allow 9090/tcp

sudo ufw enable
```

## Monitoring and Maintenance

### View Logs

```bash
# Backend logs
sudo journalctl -u mgnrega-backend -f

# Nginx logs
sudo tail -f /var/log/nginx/error.log
sudo tail -f /var/log/nginx/access.log
```

### Restart Services

```bash
# Backend
sudo systemctl restart mgnrega-backend

# Nginx
sudo systemctl restart nginx
```

### Database Backup

```bash
# Create backup
sudo -u postgres pg_dump mgnrega > mgnrega_backup_$(date +%Y%m%d).sql

# Restore
sudo -u postgres psql mgnrega < mgnrega_backup_20240101.sql
```

## Troubleshooting

### Backend won't start
1. Check Java version: `java -version`
2. Check database connection: `sudo -u postgres psql -U mgnrega_user -d mgnrega`
3. Check logs: `sudo journalctl -u mgnrega-backend -n 50`

### Frontend not loading
1. Check nginx: `sudo nginx -t`
2. Check permissions: `sudo chown -R www-data:www-data /opt/mgnrega/frontend/dist`
3. Check nginx error log

### API calls failing
1. Verify environment variables: `sudo cat /etc/mgnrega.env`
2. Test API directly: `curl http://localhost:9090/api/health`
3. Check CORS configuration in application.properties

## Performance Optimization

### Database Indexing
Tables are automatically indexed via Hibernate. Check indexes:
```sql
\di+ performance_records
```

### Cache Configuration
Adjust cache TTL in `/etc/mgnrega.env`:
```
CACHE_TTL_SECONDS=1800  # 30 minutes
```

### Nginx Caching (Optional)
Add to nginx config:
```nginx
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m max_size=1g inactive=60m;

location /api {
    proxy_cache api_cache;
    proxy_cache_valid 200 10m;
    # ... rest of config
}
```

## Security Considerations

1. **Change default passwords** in production
2. **Use environment variables** instead of hardcoded values
3. **Limit CORS** to specific domains in production
4. **Enable SSL/TLS** for HTTPS
5. **Keep system updated**: `sudo apt-get update && sudo apt-get upgrade`
6. **Firewall rules** - only expose necessary ports
7. **Regular backups** of database

## Scaling

For high traffic:
1. Use a load balancer (nginx, HAProxy)
2. Run multiple backend instances
3. Use Redis for distributed caching
4. Consider database read replicas
5. CDN for static frontend assets

