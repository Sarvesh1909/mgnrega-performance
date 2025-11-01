# Quick Vercel Deployment Guide

## Prerequisites

1. **Backend Deployed**: You need to deploy the backend first (Railway/Render/Fly.io)
2. **GitHub Account**: Your code should be on GitHub
3. **Vercel Account**: Sign up at [vercel.com](https://vercel.com)

## Quick Steps

### 1. Deploy Backend (Choose One)

#### Railway (Easiest):
1. Go to [railway.app](https://railway.app) → New Project
2. Deploy from GitHub → Select your repo
3. Add PostgreSQL database
4. Set environment variables:
   ```
   DATABASE_URL=auto (from PostgreSQL service)
   DATAGOV_API_KEY=your_api_key
   SERVER_PORT=8080
   CORS_ALLOWED_ORIGINS=*
   ```
5. Copy your Railway URL: `https://your-app.up.railway.app`

#### Render:
1. Go to [render.com](https://render.com) → New Web Service
2. Connect GitHub repo
3. Root Directory: `backend`
4. Build: `mvn clean package -DskipTests`
5. Start: `java -jar target/backend-0.0.1-SNAPSHOT.jar`
6. Add PostgreSQL database
7. Set environment variables
8. Copy Render URL: `https://your-app.onrender.com`

### 2. Deploy Frontend to Vercel

#### Option A: Via Vercel Dashboard (Recommended)

1. **Go to Vercel**: [vercel.com/dashboard](https://vercel.com/dashboard)

2. **Import Project**:
   - Click "Add New" → "Project"
   - Import from GitHub
   - Select your repository

3. **Configure Project**:
   - **Framework Preset**: Vite
   - **Root Directory**: `frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`

4. **Environment Variables**:
   - Click "Environment Variables"
   - Add: `VITE_API_BASE_URL` = `https://your-backend-url.railway.app`
   - Select: Production, Preview, Development
   - Click "Save"

5. **Update vercel.json**:
   - Edit `frontend/vercel.json`
   - Update the backend URL in the rewrite rule:
   ```json
   {
     "rewrites": [
       {
         "source": "/api/(.*)",
         "destination": "https://your-backend-url.railway.app/api/$1"
       }
     ]
   }
   ```

6. **Deploy**:
   - Click "Deploy"
   - Wait for build to complete
   - Copy your Vercel URL: `https://your-app.vercel.app`

#### Option B: Via Vercel CLI

```bash
# Install Vercel CLI
npm install -g vercel

# Login
vercel login

# Navigate to frontend
cd frontend

# Create .env.production file
echo "VITE_API_BASE_URL=https://your-backend-url.railway.app" > .env.production

# Deploy
vercel --prod
```

### 3. Update Backend CORS (Important!)

Add your Vercel URL to backend CORS settings:

**Railway/Render Environment Variable**:
```
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,https://*.vercel.app
```

Or in `application-prod.properties`:
```properties
cors.allowed.origins=https://your-app.vercel.app
```

### 4. Verify Deployment

1. Visit your Vercel URL
2. Test the app:
   - Select a district
   - Click "View Performance"
   - Check if data loads

## Troubleshooting

### API calls failing
- Check browser console for errors
- Verify `VITE_API_BASE_URL` is set correctly
- Check backend CORS settings
- Test backend URL directly: `https://your-backend-url/api/health`

### CORS errors
- Update `CORS_ALLOWED_ORIGINS` in backend
- Include both production and preview URLs
- Use `*` for development (not recommended for production)

### 404 on API routes
- Check `frontend/vercel.json` rewrite rules
- Verify backend URL is correct
- Ensure backend is running and accessible

## Environment Variables Checklist

### Frontend (Vercel):
- ✅ `VITE_API_BASE_URL` = `https://your-backend-url.railway.app`

### Backend (Railway/Render):
- ✅ `DATABASE_URL` = (auto from PostgreSQL)
- ✅ `DATABASE_USER` = (auto)
- ✅ `DATABASE_PASSWORD` = (auto)
- ✅ `DATAGOV_API_KEY` = `your_api_key`
- ✅ `SERVER_PORT` = `8080` (Railway) or `10000` (Render)
- ✅ `CORS_ALLOWED_ORIGINS` = `https://your-app.vercel.app`

## Your URLs

After deployment, you should have:
- **Frontend**: `https://your-app.vercel.app`
- **Backend**: `https://your-backend.railway.app` (or `.onrender.com`)
- **API Health**: `https://your-backend.railway.app/api/health`

## Next Steps

1. Test all features
2. Set up custom domain (optional)
3. Configure monitoring
4. Set up backups for database

## Need Help?

- Vercel Docs: https://vercel.com/docs
- Railway Docs: https://docs.railway.app
- Render Docs: https://render.com/docs

