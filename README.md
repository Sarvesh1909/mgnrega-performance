# MGNREGA District Performance Dashboard

A production-ready web application for displaying MGNREGA (Mahatma Gandhi National Rural Employment Guarantee Act) district performance data in an easy-to-understand format for rural citizens.

## Features

✅ **District Selection** - Users can select their district from a dropdown  
✅ **Performance Visualization** - Cards, trends, and tables with icons  
✅ **Historical Data** - Shows last 12 months of performance  
✅ **Comparatives** - Compare district with state average or other districts  
✅ **Low-Literacy Design** - Hindi translation, audio support, icons, glossary  
✅ **Auto-Detect District** - Uses geolocation to preselect user's district  
✅ **Database Persistence** - PostgreSQL for local data storage  
✅ **Rate Limiting** - Protection against API throttling  
✅ **Production Ready** - Error handling, retry logic, caching  

## Tech Stack

### Backend
- Java 17
- Spring Boot 3.2.5
- PostgreSQL
- Spring WebFlux (for API calls)

### Frontend
- React 19
- Vite
- CSS with design tokens

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Node.js 18+ and npm
- PostgreSQL 12+

## Quick Start (Development)

### 1. Setup Database

```bash
# Create database
createdb mgnrega

# Or using PostgreSQL client
psql -U postgres
CREATE DATABASE mgnrega;
```

### 2. Configure Environment

Copy `.env.example` to `.env` and update values:

```bash
cp .env.example .env
# Edit .env with your values
```

### 3. Backend Setup

```bash
cd backend
mvn clean install
# Set environment variables or update application.properties
mvn spring-boot:run
```

Backend runs on `http://localhost:9090`

### 4. Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`

## Production Deployment

### Option 1: Using the Deployment Script

```bash
chmod +x deploy.sh
./deploy.sh
```

### Option 2: Manual Deployment

1. **Build Backend**
   ```bash
   cd backend
   mvn clean package -DskipTests
   ```

2. **Build Frontend**
   ```bash
   cd frontend
   npm install
   npm run build
   ```

3. **Setup PostgreSQL**
   ```bash
   sudo -u postgres psql
   CREATE DATABASE mgnrega;
   CREATE USER mgnrega_user WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE mgnrega TO mgnrega_user;
   ```

4. **Run Backend**
   ```bash
   cd backend
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

5. **Serve Frontend**
   - Copy `frontend/dist` to web server (nginx, Apache)
   - Configure reverse proxy to backend API

### Environment Variables for Production

Set these environment variables on your server:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/mgnrega
export DATABASE_USER=mgnrega_user
export DATABASE_PASSWORD=your_secure_password
export DATAGOV_API_KEY=your_api_key
export SERVER_PORT=9090
export CORS_ALLOWED_ORIGINS=https://yourdomain.com
```

### Nginx Configuration

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    root /path/to/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## API Endpoints

### Districts
- `GET /api/districts` - List all districts

### Performance
- `GET /api/performance?state={state}&district={district}&limit={limit}` - Get performance data

### Comparatives
- `GET /api/comparatives/state-average?state={state}&district={district}` - Compare with state average
- `GET /api/comparatives/district-comparison?state={state}&district1={d1}&district2={d2}` - Compare two districts

### Health
- `GET /api/health` - Health check

## Architecture Decisions

### Database Persistence
- Data fetched from data.gov.in API is stored in PostgreSQL
- Reduces API calls and provides resilience when API is down
- Automatic data synchronization on first fetch

### Rate Limiting
- In-memory rate limiter (10 requests/minute)
- Falls back to database when rate limit exceeded
- Prevents API throttling

### Error Handling
- Retry logic with exponential backoff (3 retries)
- Graceful degradation to cached/database data
- User-friendly error messages

### Caching
- Multi-layer caching:
  1. In-memory cache (15 minutes TTL)
  2. Database (persistent)
  3. API (when needed)

## Design for Low-Literacy Users

- **Visual Icons** - Every metric has an emoji icon
- **Hindi Translation** - Full bilingual support
- **Audio Support** - Text-to-speech for key metrics
- **Glossary** - Expandable explanation of terms
- **Simple Language** - Clear, non-technical labels
- **Color Coding** - Green for good, red for warnings
- **Large Fonts** - Readable on mobile devices

## License

This project is created for educational/demonstration purposes.

## Support

For issues or questions, please check the application logs:
- Backend: `journalctl -u mgnrega-backend -f`
- Nginx: `/var/log/nginx/error.log`

