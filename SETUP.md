# Setup and Testing Guide

## Quick Start - Local Development

### Step 1: Setup PostgreSQL Database

**Windows:**
```bash
# Download and install PostgreSQL from https://www.postgresql.org/download/windows/
# Or use chocolatey: choco install postgresql

# After installation, open pgAdmin or use psql command line:
# Create database
createdb mgnrega

# Or using SQL:
psql -U postgres
CREATE DATABASE mgnrega;
\q
```

**Mac:**
```bash
# Install via Homebrew
brew install postgresql
brew services start postgresql

# Create database
createdb mgnrega
```

**Linux:**
```bash
sudo apt-get install postgresql postgresql-contrib
sudo -u postgres createdb mgnrega
```

### Step 2: Update Backend Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
# Update these lines with your database credentials:
spring.datasource.url=jdbc:postgresql://localhost:5432/mgnrega
spring.datasource.username=postgres  # or your username
spring.datasource.password=your_password  # your PostgreSQL password

# Your Data.gov.in API key (get from https://data.gov.in)
datagov.apiKey=your_api_key_here
```

### Step 3: Build and Run Backend

```bash
cd backend

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Backend should start on http://localhost:9090
# Test health endpoint: http://localhost:9090/api/health
```

**Check if backend is running:**
- Open browser: http://localhost:9090/api/health
- Should see: `{"status":"UP"}`

### Step 4: Setup and Run Frontend

**Open a new terminal:**

```bash
cd frontend

# Install dependencies
npm install

# Create .env file for API URL (optional - defaults to localhost:9090)
echo "VITE_API_BASE_URL=http://localhost:9090" > .env

# Start development server
npm run dev

# Frontend should start on http://localhost:5173
```

### Step 5: Test the Application

1. **Open browser:** http://localhost:5173
2. **Select a district** from dropdown (e.g., "Mumbai")
3. **Click "View Performance"** - Should fetch data
4. **Test Comparatives:**
   - Click "Compare with State Average"
   - Click "Compare with Another District" and select a different district
5. **Test Audio:** Click the 🔊 button
6. **Test Hindi:** Click "हिं" button to switch language

## Troubleshooting

### Backend won't start
**Error: "Connection refused" or database errors**
```bash
# Check if PostgreSQL is running
# Windows: Check Services
# Mac: brew services list
# Linux: sudo systemctl status postgresql

# Verify database exists
psql -U postgres -l | grep mgnrega
```

**Error: "Port 9090 already in use"**
```bash
# Change port in application.properties:
server.port=9091  # or any free port
```

### Frontend can't connect to backend
**Error: "Failed to fetch"**
```bash
# 1. Check backend is running on http://localhost:9090
curl http://localhost:9090/api/health

# 2. Check CORS configuration (should allow *)
# 3. Update .env file with correct API URL
```

### No data showing
**Possible causes:**
1. API key missing or invalid - Check `application.properties`
2. Database not created - Run database setup
3. API rate limited - Wait a few minutes
4. First time fetch takes longer - Be patient

## Testing All Features

### ✅ Feature Checklist

- [ ] District dropdown loads
- [ ] Can select different districts
- [ ] Performance data displays (cards, trends, table)
- [ ] Historical data shows (last 12 months)
- [ ] Compare with State Average works
- [ ] Compare with Another District works
- [ ] Audio playback works (🔊 button)
- [ ] Hindi translation works (हिं button)
- [ ] Glossary expands correctly
- [ ] Geolocation auto-selects district (if location allowed)
- [ ] Error messages display clearly
- [ ] Database stores data (check PostgreSQL)

### Check Database

```bash
# Connect to database
psql -U postgres -d mgnrega

# View stored records
SELECT district_name, fin_year, month, persondays_generated 
FROM performance_records 
ORDER BY created_at DESC 
LIMIT 10;

\q
```

## Prepare for Production Deployment

### 1. Get Production API Key
- Register at https://data.gov.in
- Get your API key
- Update in environment variables

### 2. Choose Deployment Option

**Option A: Use deploy.sh script (Linux/Mac)**
```bash
chmod +x deploy.sh
./deploy.sh
```

**Option B: Manual deployment**
- Follow `DEPLOYMENT.md` guide
- Set up VM/VPS
- Configure nginx
- Set environment variables

### 3. Environment Variables for Production

Create `.env` file or set system environment variables:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/mgnrega
DATABASE_USER=mgnrega_user
DATABASE_PASSWORD=secure_password
DATAGOV_API_KEY=your_api_key
SERVER_PORT=9090
USE_DATABASE=true
CORS_ALLOWED_ORIGINS=https://yourdomain.com
```

### 4. Build Production Builds

**Backend:**
```bash
cd backend
mvn clean package -DskipTests
# JAR file: target/backend-0.0.1-SNAPSHOT.jar
```

**Frontend:**
```bash
cd frontend
npm run build
# Build folder: frontend/dist (upload this to web server)
```

## Next Steps After Setup

1. **Test locally** - Make sure everything works
2. **Get API key** - Register at data.gov.in
3. **Choose hosting** - AWS, DigitalOcean, Azure, etc.
4. **Deploy** - Follow DEPLOYMENT.md
5. **Monitor** - Check logs and health endpoints
6. **Optimize** - Adjust cache TTL, rate limits as needed

## Need Help?

Check logs:
- Backend: Console output or `logs/` folder
- Frontend: Browser console (F12)
- Database: PostgreSQL logs

Test API endpoints directly:
```bash
# Health check
curl http://localhost:9090/api/health

# Districts
curl http://localhost:9090/api/districts

# Performance (after running app)
curl "http://localhost:9090/api/performance?state=Maharashtra&district=Mumbai&limit=12"
```

